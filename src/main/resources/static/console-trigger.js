(function () {
  'use strict';

  // ---- 1. Locate this script tag to extract serverOrigin ----

  var scriptEl = null;
  var scripts = document.getElementsByTagName('script');
  for (var i = 0; i < scripts.length; i++) {
    var src = scripts[i].getAttribute('src') || '';
    if (src.indexOf('console-trigger.js') !== -1) {
      scriptEl = scripts[i];
      break;
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

  // Extract target from query string (e.g. console-trigger.js?target=my-crm)
  var triggerTarget = null;
  var qIdx = fullSrc.indexOf('?');
  if (qIdx !== -1) {
    var queryString = fullSrc.substring(qIdx + 1);
    var pairs = queryString.split('&');
    for (var p = 0; p < pairs.length; p++) {
      var kv = pairs[p].split('=');
      if (decodeURIComponent(kv[0]) === 'target' && kv.length > 1) {
        triggerTarget = decodeURIComponent(kv[1]);
        break;
      }
    }
  }

  var pollUrl = serverOrigin + '/api/trigger/scenarios/poll';
  if (triggerTarget) {
    pollUrl += '?target=' + encodeURIComponent(triggerTarget);
  }

  // ---- 2. Command executors ----

  var DEFAULT_TIMEOUT = 5000;

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
        // Use native setter to trigger React/Vue change detection
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
      // Set the text
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
    }
  };

  // ---- 3. Scenario runner ----

  function runScenario(scenario) {
    var steps = scenario.steps || [];
    var i = 0;

    function nextStep() {
      if (i >= steps.length) {
        return Promise.resolve();
      }
      var step = steps[i];
      i++;
      var executor = executors[step.command];
      if (!executor) {
        return Promise.reject(new Error('Unknown command: ' + step.command));
      }
      try {
        return executor(step).then(nextStep);
      } catch (e) {
        return Promise.reject(e);
      }
    }

    return nextStep();
  }

  // ---- 4. Long polling loop ----

  var retryDelay = 2000;
  var maxRetryDelay = 30000;

  function pollLoop() {
    fetch(pollUrl).then(function (response) {
      if (response.status === 204) {
        // Timeout, no scenario available — re-poll immediately
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

  // ---- 5. Start polling ----

  pollLoop();

})();
