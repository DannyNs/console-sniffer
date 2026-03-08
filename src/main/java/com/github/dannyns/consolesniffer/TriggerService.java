package com.github.dannyns.consolesniffer;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TriggerService {

    private static final long POLL_TIMEOUT_MS = 30_000;
    private static final long STALE_THRESHOLD_MS = Duration.ofMinutes(5).toMillis();

    private static final String NULL_KEY = "";

    private final Map<String, ArrayDeque<TriggerScenario>> scenariosByTarget = new HashMap<>();
    private final Map<String, ArrayDeque<DeferredResult<ResponseEntity<TriggerScenario>>>> pollersByTarget = new HashMap<>();

    private final Object handshakeLock = new Object();

    /**
     * Submits a new scenario. If a poller is waiting, resolves it immediately.
     * Otherwise queues the scenario for the next poll.
     */
    public TriggerScenario submitScenario(TriggerScenario scenario) {
        scenario.setId(UUID.randomUUID().toString());
        scenario.setCreatedAt(Instant.now().toString());
        String key = targetKey(scenario.getTarget());

        synchronized (handshakeLock) {
            // Try to hand off directly to a waiting poller for this target
            ArrayDeque<DeferredResult<ResponseEntity<TriggerScenario>>> pollers = pollersByTarget.get(key);
            if (pollers != null) {
                DeferredResult<ResponseEntity<TriggerScenario>> waiter;
                while ((waiter = pollers.poll()) != null) {
                    if (!waiter.isSetOrExpired()) {
                        if (pollers.isEmpty()) pollersByTarget.remove(key);
                        waiter.setResult(ResponseEntity.ok(scenario));
                        return scenario;
                    }
                }
                pollersByTarget.remove(key);
            }
            // No matching active poller — queue it
            scenariosByTarget.computeIfAbsent(key, k -> new ArrayDeque<>()).offer(scenario);
        }
        return scenario;
    }

    /**
     * Long-polling endpoint. Returns immediately if a scenario is queued,
     * otherwise parks the request until a scenario arrives or timeout.
     */
    public DeferredResult<ResponseEntity<TriggerScenario>> pollForScenario(String target) {
        String key = targetKey(target);
        DeferredResult<ResponseEntity<TriggerScenario>> result =
                new DeferredResult<>(POLL_TIMEOUT_MS, ResponseEntity.noContent().build());

        synchronized (handshakeLock) {
            // Check for a queued scenario matching this target
            ArrayDeque<TriggerScenario> scenarios = scenariosByTarget.get(key);
            if (scenarios != null) {
                TriggerScenario queued = scenarios.poll();
                if (queued != null) {
                    if (scenarios.isEmpty()) scenariosByTarget.remove(key);
                    result.setResult(ResponseEntity.ok(queued));
                    return result;
                }
                scenariosByTarget.remove(key);
            }
            // No scenario available — park the poller
            pollersByTarget.computeIfAbsent(key, k -> new ArrayDeque<>()).offer(result);
        }

        result.onTimeout(() -> removePoller(key, result));
        result.onCompletion(() -> removePoller(key, result));

        return result;
    }

    private void removePoller(String key, DeferredResult<ResponseEntity<TriggerScenario>> result) {
        synchronized (handshakeLock) {
            ArrayDeque<DeferredResult<ResponseEntity<TriggerScenario>>> pollers = pollersByTarget.get(key);
            if (pollers != null) {
                pollers.remove(result);
                if (pollers.isEmpty()) pollersByTarget.remove(key);
            }
        }
    }

    /**
     * Removes stale scenarios that have not been polled within the threshold.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupStaleScenarios() {
        Instant cutoff = Instant.now().minusMillis(STALE_THRESHOLD_MS);
        synchronized (handshakeLock) {
            Iterator<Map.Entry<String, ArrayDeque<TriggerScenario>>> mapIt = scenariosByTarget.entrySet().iterator();
            while (mapIt.hasNext()) {
                ArrayDeque<TriggerScenario> queue = mapIt.next().getValue();
                queue.removeIf(s -> Instant.parse(s.getCreatedAt()).isBefore(cutoff));
                if (queue.isEmpty()) mapIt.remove();
            }
        }
    }

    /**
     * Returns the command catalog in an LLM-friendly JSON structure.
     */
    public Map<String, Object> getCommandsCatalog() {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("description", "Console Trigger DSL — commands for browser UI automation via POST /api/trigger/scenarios");
        catalog.put("usage", """
                POST a JSON object with 'name' (string), optional 'description' (string), \
                'target' (string), and 'steps' (array of command objects). Each step \
                must have a 'command' field plus the required parameters listed below. Steps \
                execute sequentially; execution stops on first failure. The 'target' field \
                is the 'targetPath' value from the console-sniffer.js script tag's src attribute \
                (e.g. src=".../console-sniffer.js?targetPath=/tmp/app.log" means use \
                "target": "/tmp/app.log"). \
                Use 'logPath' to check the current URL, 'logBody' and 'logHead' to inspect \
                the rendered HTML (all captured by console-sniffer), and 'navigate' to \
                redirect the browser to a different path. When using 'navigate', scenario \
                state is automatically persisted to localStorage and resumed after page reload. \
                It is recommended to use persistent=true on the script tag when using 'navigate'.""");

        List<Map<String, Object>> commands = new ArrayList<>();
        commands.add(cmd("click", "Click a DOM element",
                params(param("selector", "string", true, "CSS selector for the target element")),
                List.of(example("click", Map.of("selector", "#submit-btn")),
                        example("click", Map.of("selector", ".menu-item:first-child")))));

        commands.add(cmd("dblclick", "Double-click a DOM element",
                params(param("selector", "string", true, "CSS selector for the target element")),
                List.of(example("dblclick", Map.of("selector", ".editable-cell")))));

        commands.add(cmd("type", "Type text into an input or textarea. Clears the field first by default.",
                params(param("selector", "string", true, "CSS selector for the input element"),
                       param("text", "string", true, "Text to type"),
                       param("clear", "boolean", false, "Clear the field before typing (default: true)")),
                List.of(example("type", Map.of("selector", "#username", "text", "admin")),
                        example("type", Map.of("selector", "#search", "text", "appended text", "clear", false)))));

        commands.add(cmd("select", "Select an option in a <select> dropdown by its value attribute",
                params(param("selector", "string", true, "CSS selector for the <select> element"),
                       param("value", "string", true, "The option value to select")),
                List.of(example("select", Map.of("selector", "#country", "value", "us")))));

        commands.add(cmd("wait", "Pause execution for a fixed number of milliseconds",
                params(param("ms", "integer", true, "Number of milliseconds to wait")),
                List.of(example("wait", Map.of("ms", 1000)))));

        commands.add(cmd("waitFor", "Wait until an element appears in the DOM (polls until found or timeout)",
                params(param("selector", "string", true, "CSS selector to wait for"),
                       param("timeout", "integer", false, "Max wait time in ms (default: 5000)")),
                List.of(example("waitFor", Map.of("selector", ".modal-dialog")),
                        example("waitFor", Map.of("selector", "#results-table", "timeout", 10000)))));

        commands.add(cmd("waitForHidden", "Wait until an element disappears from the DOM or becomes hidden",
                params(param("selector", "string", true, "CSS selector to wait for disappearance"),
                       param("timeout", "integer", false, "Max wait time in ms (default: 5000)")),
                List.of(example("waitForHidden", Map.of("selector", ".loading-spinner")))));

        commands.add(cmd("find", "Alias for waitFor — locate a DOM element, retrying until found or timeout.",
                params(param("selector", "string", true, "CSS selector for the element"),
                       param("timeout", "integer", false, "Max wait time in ms (default: 5000)")),
                List.of(example("find", Map.of("selector", "#main-content")))));

        commands.add(cmd("assertExists", "Assert that an element currently exists in the DOM. Fails immediately if not found.",
                params(param("selector", "string", true, "CSS selector to check")),
                List.of(example("assertExists", Map.of("selector", ".success-message")))));

        commands.add(cmd("assertText", "Assert that an element's text content matches or contains the expected text",
                params(param("selector", "string", true, "CSS selector for the element"),
                       param("text", "string", true, "Expected text"),
                       param("contains", "boolean", false, "If true (default), checks substring match. If false, checks exact match.")),
                List.of(example("assertText", Map.of("selector", ".status", "text", "Success")),
                        example("assertText", Map.of("selector", "h1", "text", "Dashboard", "contains", false)))));

        commands.add(cmd("logPath", "Log the current page URL to the console. The console-sniffer captures it so the backend can check the URL from the log file.",
                params(),
                List.of(example("logPath", Map.of()))));

        commands.add(cmd("logBody", "Log the current page body HTML to the console. The console-sniffer captures it so the backend can inspect the rendered DOM body.",
                params(),
                List.of(example("logBody", Map.of()))));

        commands.add(cmd("logHead", "Log the current page head HTML to the console. The console-sniffer captures it so the backend can inspect the page head element.",
                params(),
                List.of(example("logHead", Map.of()))));

        commands.add(cmd("navigate", "Navigate the browser to a given path",
                params(param("path", "string", true, "The URL path to navigate to (e.g. /dashboard, /users/123)")),
                List.of(example("navigate", Map.of("path", "/dashboard")),
                        example("navigate", Map.of("path", "/settings/profile")))));

        catalog.put("commands", commands);
        return catalog;
    }

    // --- helpers to build the catalog ---

    private static String targetKey(String target) {
        return (target == null || target.isEmpty()) ? NULL_KEY : target;
    }

    private static Map<String, Object> cmd(String name, String description,
                                           Map<String, Object> parameters,
                                           List<Map<String, Object>> examples) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("command", name);
        c.put("description", description);
        c.put("parameters", parameters);
        c.put("examples", examples);
        return c;
    }

    @SafeVarargs
    private static Map<String, Object> params(Map<String, Object>... defs) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (Map<String, Object> d : defs) {
            String name = (String) d.remove("_name");
            p.put(name, d);
        }
        return p;
    }

    private static Map<String, Object> param(String name, String type, boolean required, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("_name", name);
        p.put("type", type);
        p.put("required", required);
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> example(String command, Map<String, Object> fields) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("command", command);
        e.putAll(fields);
        return e;
    }
}
