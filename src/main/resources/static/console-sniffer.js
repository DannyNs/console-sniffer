(function () {
  'use strict';

  // ---- 1. Shared: Locate script tag, extract origin & params ----

  var scriptEl = document.currentScript;
  if (!scriptEl) {
    var scripts = document.getElementsByTagName('script');
    for (var i = 0; i < scripts.length; i++) {
      var src = scripts[i].getAttribute('src') || '';
      if (src.indexOf('console-sniffer.js') !== -1) {
        scriptEl = scripts[i];
        break;
      }
    }
  }

  if (!scriptEl) {
    return;
  }

  var fullSrc = scriptEl.getAttribute('src');

  var originMatch = fullSrc.match(/^(https?:\/\/[^\/]+)/);
  if (!originMatch) {
    return;
  }
  var serverOrigin = originMatch[1];

  var targetPath = '';
  var persistent = false;
  var allowedTypes = {};  // empty = no filter (all types allowed)
  var qIndex = fullSrc.indexOf('?');
  if (qIndex !== -1) {
    var query = fullSrc.substring(qIndex + 1);
    var params = query.split('&');
    for (var p = 0; p < params.length; p++) {
      var kv = params[p].split('=');
      var key = kv[0];
      var val = decodeURIComponent(kv.slice(1).join('='));
      if (key === 'targetPath') {
        targetPath = val;
      } else if (key === 'persistent') {
        persistent = (val === 'true');
      } else if (key === 'levels') {
        var lvlParts = val.split(',');
        for (var l = 0; l < lvlParts.length; l++) {
          var t = lvlParts[l].trim().toUpperCase();
          if (t) { allowedTypes[t] = true; }
        }
      }
    }
  }

  if (!targetPath) {
    return;
  }

  // ---- 2. Sniffer module ----

  var apiUrl = serverOrigin + '/api/log';
  var clearUrl = serverOrigin + '/api/log?targetPath=' + encodeURIComponent(targetPath);

  function generateSessionId() {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      var arr = new Uint32Array(1);
      crypto.getRandomValues(arr);
      return ('00000000' + arr[0].toString(16)).slice(-8);
    }
    return ('00000000' + Math.floor(Math.random() * 0xFFFFFFFF).toString(16)).slice(-8);
  }

  var sessionId = generateSessionId();
  var seqCounter = 0;

  if (!persistent) {
    try {
      fetch(clearUrl, { method: 'DELETE' }).catch(function () {});
    } catch (e) {}
  }

  try {
    fetch(apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      keepalive: true,
      body: JSON.stringify({
        type:       'SESSION_START',
        session:    sessionId,
        seq:        seqCounter++,
        ts:         new Date().toISOString(),
        url:        window.location.href,
        ua:         navigator.userAgent,
        targetPath: targetPath
      })
    }).catch(function () {});
  } catch (e) {}

  function formatArgs(args) {
    var parts = [];
    for (var i = 0; i < args.length; i++) {
      var arg = args[i];
      if (arg === null) {
        parts.push('null');
      } else if (arg === undefined) {
        parts.push('undefined');
      } else if (typeof arg === 'object') {
        try {
          parts.push(JSON.stringify(arg));
        } catch (e) {
          parts.push('[object Object]');
        }
      } else {
        parts.push(String(arg));
      }
    }
    return parts.join(' ');
  }

  function sendLog(type, message, extra) {
    if (Object.keys(allowedTypes).length > 0 && !allowedTypes[type]) {
      seqCounter++;   // keep seq monotonic; gaps in file indicate filtered events
      return;
    }
    try {
      var payload = {
        type:       type,
        session:    sessionId,
        seq:        seqCounter++,
        ts:         new Date().toISOString(),
        targetPath: targetPath
      };
      if (message !== undefined && message !== null) {
        payload.message = message;
      }
      if (extra) {
        if (extra.source !== undefined && extra.source !== null && extra.source !== '') payload.source = extra.source;
        if (extra.line   !== undefined && extra.line   !== null)                        payload.line   = extra.line;
        if (extra.col    !== undefined && extra.col    !== null)                        payload.col    = extra.col;
        if (extra.stack  !== undefined && extra.stack  !== null && extra.stack !== '')  payload.stack  = extra.stack;
      }
      fetch(apiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        keepalive: true,
        body: JSON.stringify(payload)
      }).catch(function () {});
    } catch (e) {}
  }

  function captureStack() {
    try {
      var raw = new Error().stack || '';
      var lines = raw.split('\n').filter(function (l) {
        return l.trim() !== '' && l.trim() !== 'Error'
            && l.indexOf('console-sniffer.js') === -1;
      });
      return lines.length > 0 ? lines.join('\n') : undefined;
    } catch (e) {
      return undefined;
    }
  }

  var levelMap = {
    log:   'LOG',
    warn:  'WARN',
    error: 'ERROR',
    info:  'INFO',
    debug: 'DEBUG'
  };

  Object.keys(levelMap).forEach(function (method) {
    var original = console[method].bind(console);
    console[method] = function () {
      original.apply(console, arguments);
      var stack = captureStack();
      sendLog(levelMap[method], formatArgs(arguments), stack ? { stack: stack } : undefined);
    };
  });

  var previousOnError = window.onerror;
  window.onerror = function (message, source, lineno, colno, error) {
    sendLog('WINDOW_ERROR', String(message), {
      source: source  || undefined,
      line:   lineno  || undefined,
      col:    colno   || undefined,
      stack:  (error && error.stack) ? error.stack : undefined
    });
    if (typeof previousOnError === 'function') {
      return previousOnError.apply(this, arguments);
    }
    return false;
  };

  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    var msg;
    var stack;
    if (reason instanceof Error) {
      msg   = reason.message;
      stack = reason.stack || undefined;
    } else {
      try { msg = JSON.stringify(reason); } catch (e) { msg = String(reason); }
      stack = undefined;
    }
    sendLog('UNHANDLED_REJECTION', msg, { stack: stack });
  });

  // ---- 3. Trigger module ----

  var pollUrl = serverOrigin + '/api/trigger/scenarios/poll?target=' + encodeURIComponent(targetPath);

  var DEFAULT_TIMEOUT = 5000;

  var LS_KEY = 'cs-trigger-scenario';

  function saveProgress(scenario, nextStepIndex) {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify({
        scenarioId: scenario.id,
        scenarioName: scenario.name,
        steps: scenario.steps,
        nextStepIndex: nextStepIndex,
        target: scenario.target || null,
        savedAt: new Date().toISOString()
      }));
    } catch (e) {
      console.warn('[console-trigger] Cannot save progress to localStorage: ' + e.message);
    }
  }

  function clearProgress() {
    try {
      localStorage.removeItem(LS_KEY);
    } catch (e) {
      // ignore
    }
  }

  function loadProgress() {
    try {
      var raw = localStorage.getItem(LS_KEY);
      if (!raw) return null;
      var data = JSON.parse(raw);
      if (data.savedAt) {
        var elapsed = Date.now() - new Date(data.savedAt).getTime();
        if (elapsed > 5 * 60 * 1000) {
          localStorage.removeItem(LS_KEY);
          return null;
        }
      }
      return data;
    } catch (e) {
      return null;
    }
  }

  function resolveElement(selector) {
    var el = document.querySelector(selector);
    if (!el) {
      throw new Error('Element not found: ' + selector);
    }
    return el;
  }

  function waitForElement(selector, timeout) {
    var deadline = Date.now() + (timeout || DEFAULT_TIMEOUT);
    return new Promise(function (resolve, reject) {
      var check = function () {
        var el = document.querySelector(selector);
        if (el) {
          resolve(el);
        } else if (Date.now() >= deadline) {
          reject(new Error('Timed out waiting for element: ' + selector));
        } else {
          setTimeout(check, 50);
        }
      };
      check();
    });
  }

  function waitForHidden(selector, timeout) {
    var deadline = Date.now() + (timeout || DEFAULT_TIMEOUT);
    return new Promise(function (resolve, reject) {
      var check = function () {
        var el = document.querySelector(selector);
        if (!el || el.offsetParent === null) {
          resolve();
        } else if (Date.now() >= deadline) {
          reject(new Error('Timed out waiting for element to hide: ' + selector));
        } else {
          setTimeout(check, 50);
        }
      };
      check();
    });
  }

  function dispatchInputEvents(el) {
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  var executors = {
    click: function (step) {
      var el = resolveElement(step.selector);
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      return Promise.resolve();
    },

    dblclick: function (step) {
      var el = resolveElement(step.selector);
      el.dispatchEvent(new MouseEvent('dblclick', { bubbles: true, cancelable: true }));
      return Promise.resolve();
    },

    type: function (step) {
      var el = resolveElement(step.selector);
      var shouldClear = step.clear !== false; // default true
      if (shouldClear) {
        var nativeSetter = Object.getOwnPropertyDescriptor(
          Object.getPrototypeOf(el).constructor.prototype || HTMLInputElement.prototype, 'value'
        );
        if (nativeSetter && nativeSetter.set) {
          nativeSetter.set.call(el, '');
        } else {
          el.value = '';
        }
        dispatchInputEvents(el);
      }
      var setter = Object.getOwnPropertyDescriptor(
        Object.getPrototypeOf(el).constructor.prototype || HTMLInputElement.prototype, 'value'
      );
      if (setter && setter.set) {
        setter.set.call(el, step.text || '');
      } else {
        el.value = step.text || '';
      }
      dispatchInputEvents(el);
      return Promise.resolve();
    },

    select: function (step) {
      var el = resolveElement(step.selector);
      el.value = step.value;
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return Promise.resolve();
    },

    wait: function (step) {
      return new Promise(function (resolve) {
        setTimeout(resolve, step.ms || 0);
      });
    },

    waitFor: function (step) {
      return waitForElement(step.selector, step.timeout);
    },

    waitForHidden: function (step) {
      return waitForHidden(step.selector, step.timeout);
    },

    find: function (step) {
      return waitForElement(step.selector, step.timeout);
    },

    assertExists: function (step) {
      resolveElement(step.selector);
      return Promise.resolve();
    },

    assertText: function (step) {
      var el = resolveElement(step.selector);
      var actual = (el.textContent || '').trim();
      var expected = step.text || '';
      var isContains = step.contains !== false; // default true
      if (isContains) {
        if (actual.indexOf(expected) === -1) {
          throw new Error('assertText failed: "' + actual + '" does not contain "' + expected + '"');
        }
      } else {
        if (actual !== expected) {
          throw new Error('assertText failed: expected "' + expected + '" but got "' + actual + '"');
        }
      }
      return Promise.resolve();
    },

    logPath: function () {
      console.log('[console-trigger] Current path: ' + window.location.href);
      return Promise.resolve();
    },

    logBody: function () {
      console.log('[console-trigger] Current body HTML: ' + document.body.innerHTML);
      return Promise.resolve();
    },

    logHead: function () {
      console.log('[console-trigger] Current head HTML: ' + document.head.innerHTML);
      return Promise.resolve();
    },

    navigate: function (step, scenario, stepIndex) {
      saveProgress(scenario, stepIndex + 1);
      window.location.href = step.path;
      return new Promise(function () {});
    }
  };

  function runScenario(scenario, startIndex) {
    var steps = scenario.steps || [];
    var i = startIndex || 0;

    function nextStep() {
      if (i >= steps.length) {
        return Promise.resolve();
      }
      var step = steps[i];
      var currentIndex = i;
      i++;
      var executor = executors[step.command];
      if (!executor) {
        return Promise.reject(new Error('Unknown command: ' + step.command));
      }
      try {
        return executor(step, scenario, currentIndex).then(function () {
          saveProgress(scenario, i);
          return nextStep();
        });
      } catch (e) {
        return Promise.reject(e);
      }
    }

    saveProgress(scenario, i);
    return nextStep().then(function () {
      clearProgress();
    }, function (err) {
      clearProgress();
      throw err;
    });
  }

  var retryDelay = 2000;
  var maxRetryDelay = 30000;

  function pollLoop() {
    fetch(pollUrl).then(function (response) {
      if (response.status === 204) {
        retryDelay = 2000;
        pollLoop();
        return;
      }
      if (!response.ok) {
        throw new Error('Poll returned status ' + response.status);
      }
      return response.json();
    }).then(function (scenario) {
      if (!scenario) {
        return;
      }
      retryDelay = 2000;
      return runScenario(scenario).then(function () {
        console.log('[console-trigger] Scenario completed: ' + (scenario.name || scenario.id));
      }).catch(function (err) {
        console.error('[console-trigger] Scenario failed: ' + err.message);
      }).then(function () {
        pollLoop();
      });
    }).catch(function (err) {
      console.warn('[console-trigger] Poll error, retrying in ' + retryDelay + 'ms: ' + err.message);
      setTimeout(function () {
        retryDelay = Math.min(retryDelay * 2, maxRetryDelay);
        pollLoop();
      }, retryDelay);
    });
  }

  function tryResumeScenario() {
    var saved = loadProgress();
    if (!saved) return false;
    var savedTarget = saved.target || null;
    if (savedTarget !== targetPath) {
      clearProgress();
      return false;
    }
    if (!saved.steps || saved.nextStepIndex >= saved.steps.length) {
      clearProgress();
      return false;
    }
    console.log('[console-trigger] Resuming scenario "' + (saved.scenarioName || saved.scenarioId) + '" from step ' + saved.nextStepIndex);
    var scenario = {
      id: saved.scenarioId,
      name: saved.scenarioName,
      steps: saved.steps,
      target: saved.target
    };
    runScenario(scenario, saved.nextStepIndex).then(function () {
      console.log('[console-trigger] Scenario completed: ' + (scenario.name || scenario.id));
    }).catch(function (err) {
      console.error('[console-trigger] Scenario failed: ' + err.message);
    }).then(function () {
      pollLoop();
    });
    return true;
  }

  if (!tryResumeScenario()) {
    pollLoop();
  }

})();
