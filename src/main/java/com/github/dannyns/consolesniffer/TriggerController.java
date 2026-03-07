package com.github.dannyns.consolesniffer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;

@RestController
public class TriggerController {

    private final TriggerService triggerService;

    public TriggerController(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    /**
     * Returns the full command catalog in an LLM-friendly JSON format.
     */
    @GetMapping(value = "/api/trigger/commands", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCommands() {
        return ResponseEntity.ok(triggerService.getCommandsCatalog());
    }

    /**
     * Accepts a scenario (sequence of commands) and queues it for execution.
     */
    @PostMapping(value = "/api/trigger/scenarios", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TriggerScenario> submitScenario(@RequestBody TriggerScenario scenario) {
        if (scenario.getSteps() == null || scenario.getSteps().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        TriggerScenario saved = triggerService.submitScenario(scenario);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Long-polling endpoint for the JS client. Returns 200 with a scenario
     * when one is available, or 204 No Content after timeout (~30s).
     */
    @GetMapping("/api/trigger/scenarios/poll")
    public DeferredResult<ResponseEntity<TriggerScenario>> pollScenario(
            @RequestParam(required = false) String target) {
        return triggerService.pollForScenario(target);
    }
}
