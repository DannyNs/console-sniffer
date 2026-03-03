package com.consolesniffer;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class TriggerController {

    private final TriggerService triggerService;

    public TriggerController(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    /**
     * Serves the JavaScript client so any web app can load it via a script tag.
     */
    @GetMapping(value = "/console-trigger.js", produces = "application/javascript")
    public ResponseEntity<String> serveTriggerJs() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/console-trigger.js");
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript; charset=UTF-8"))
                .body(content);
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
