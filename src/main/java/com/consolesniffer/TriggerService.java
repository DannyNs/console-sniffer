package com.consolesniffer;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class TriggerService {

    private static final long POLL_TIMEOUT_MS = 30_000;
    private static final long STALE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    private final ConcurrentHashMap<String, TriggerScenario> pendingScenarios = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> scenarioOrder = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DeferredResult<ResponseEntity<TriggerScenario>>> waitingPollers = new ConcurrentLinkedQueue<>();

    private final Object handshakeLock = new Object();

    /**
     * Submits a new scenario. If a poller is waiting, resolves it immediately.
     * Otherwise queues the scenario for the next poll.
     */
    public TriggerScenario submitScenario(TriggerScenario scenario) {
        scenario.setId(UUID.randomUUID().toString());
        scenario.setCreatedAt(Instant.now().toString());

        synchronized (handshakeLock) {
            // Try to hand off directly to a waiting poller
            DeferredResult<ResponseEntity<TriggerScenario>> waiter;
            while ((waiter = waitingPollers.poll()) != null) {
                if (!waiter.isSetOrExpired()) {
                    waiter.setResult(ResponseEntity.ok(scenario));
                    return scenario;
                }
            }
            // No active poller — queue it
            pendingScenarios.put(scenario.getId(), scenario);
            scenarioOrder.offer(scenario.getId());
        }
        return scenario;
    }

    /**
     * Long-polling endpoint. Returns immediately if a scenario is queued,
     * otherwise parks the request until a scenario arrives or timeout.
     */
    public DeferredResult<ResponseEntity<TriggerScenario>> pollForScenario() {
        DeferredResult<ResponseEntity<TriggerScenario>> result =
                new DeferredResult<>(POLL_TIMEOUT_MS, ResponseEntity.noContent().build());

        synchronized (handshakeLock) {
            // Drain any stale IDs and find a valid queued scenario
            TriggerScenario queued = drainNextScenario();
            if (queued != null) {
                result.setResult(ResponseEntity.ok(queued));
                return result;
            }
            // No scenario available — park the poller
            waitingPollers.offer(result);
        }

        result.onTimeout(() -> waitingPollers.remove(result));
        result.onCompletion(() -> waitingPollers.remove(result));

        return result;
    }

    /**
     * Removes stale scenarios that have not been polled within the threshold.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupStaleScenarios() {
        Instant cutoff = Instant.now().minusMillis(STALE_THRESHOLD_MS);
        Iterator<Map.Entry<String, TriggerScenario>> it = pendingScenarios.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TriggerScenario> entry = it.next();
            Instant created = Instant.parse(entry.getValue().getCreatedAt());
            if (created.isBefore(cutoff)) {
                it.remove();
                scenarioOrder.remove(entry.getKey());
            }
        }
    }

    /**
     * Returns the command catalog in an LLM-friendly JSON structure.
     */
    public Map<String, Object> getCommandsCatalog() {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("description", "Console Trigger DSL — commands for browser UI automation via POST /api/trigger/scenarios");
        catalog.put("usage", "POST a JSON object with 'name' (string), optional 'description' (string), and 'steps' (array of command objects). Each step must have a 'command' field plus the required parameters listed below. Steps execute sequentially; execution stops on first failure.");

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

        commands.add(cmd("find", "Locate a DOM element, retrying until found or timeout. Use to verify an element exists before acting on it.",
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

        catalog.put("commands", commands);
        return catalog;
    }

    // --- helpers to build the catalog ---

    private TriggerScenario drainNextScenario() {
        String id;
        while ((id = scenarioOrder.poll()) != null) {
            TriggerScenario scenario = pendingScenarios.remove(id);
            if (scenario != null) {
                return scenario;
            }
        }
        return null;
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
