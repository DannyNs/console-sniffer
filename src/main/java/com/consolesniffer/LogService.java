package com.consolesniffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogService {

    private final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();
    // ObjectMapper is thread-safe after construction — one instance is sufficient.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogService() {
    }

    /**
     * Serialises one LogRequest to a compact JSONL line and appends it to targetPath.
     * Null/blank fields are omitted from the output. targetPath is never written.
     * Jackson escapes embedded newlines in stack traces to \n, preserving JSONL validity.
     */
    public void appendLog(LogRequest request) throws IOException {
        String targetPath = request.getTargetPath();
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath must not be blank");
        }

        String effectiveType = (request.getType() != null && !request.getType().isBlank())
                ? request.getType() : "LOG";

        String effectiveTs = (request.getTs() != null && !request.getTs().isBlank())
                ? request.getTs() : java.time.Instant.now().toString();

        // Build ordered map — insertion order becomes field order in the JSON line
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type",    effectiveType);
        putIfPresent(map, "session", request.getSession());
        putIfPresent(map, "seq",     request.getSeq());
        map.put("ts",      effectiveTs);
        putIfPresent(map, "url",     request.getUrl());
        putIfPresent(map, "ua",      request.getUa());
        putIfPresent(map, "message", request.getMessage());
        putIfPresent(map, "source",  request.getSource());
        putIfPresent(map, "line",    request.getLine());
        putIfPresent(map, "col",     request.getCol());
        putIfPresent(map, "stack",   request.getStack());

        String jsonLine = objectMapper.writeValueAsString(map);

        Object lock = fileLocks.computeIfAbsent(targetPath, k -> new Object());
        synchronized (lock) {
            Path path = Paths.get(targetPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile(), true))) {
                writer.write(jsonLine);
                writer.newLine();
            }
        }
    }

    /**
     * Truncates (clears) the log file at targetPath.
     */
    public void clearLog(String targetPath) throws IOException {
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath must not be blank");
        }
        Object lock = fileLocks.computeIfAbsent(targetPath, k -> new Object());
        synchronized (lock) {
            Path path = Paths.get(targetPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            new FileWriter(path.toFile(), false).close();
        }
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) return;
        if (value instanceof String s && s.isBlank()) return;
        map.put(key, value);
    }


}
