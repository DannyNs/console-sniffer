package com.github.dannyns.consolesniffer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * Receives a console log entry from the browser and appends it to the target file.
     */
    @PostMapping(value = "/api/log", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receiveLog(@RequestBody LogRequest request) {
        try {
            logService.appendLog(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            System.err.println("[console-sniffer] Failed to write log: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Clears (truncates) the log file. Called by the JS snippet on page load
     * unless the 'persistent' query param is set to 'true'.
     */
    @DeleteMapping("/api/log")
    public ResponseEntity<Void> clearLog(@RequestParam String targetPath) {
        try {
            logService.clearLog(targetPath);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            System.err.println("[console-sniffer] Failed to clear log: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
