package com.github.dannyns.consolesniffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogService {

    private final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();
    // ObjectMapper is thread-safe after construction — one instance is sufficient.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serialises one LogRequest to a compact JSONL line and appends it to targetPath.
     * Null/blank fields are omitted from the output. targetPath is never written.
     * Jackson escapes embedded newlines in stack traces to \n, preserving JSONL validity.
     */
    public void appendLog(LogRequest request) throws IOException {
        String targetPath = request.targetPath();
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath must not be blank");
        }

        String effectiveType = (request.type() != null && !request.type().isBlank())
                ? request.type() : "LOG";

        String effectiveTs = (request.ts() != null && !request.ts().isBlank())
                ? request.ts() : java.time.Instant.now().toString();

        // Build ordered map — insertion order becomes field order in the JSON line
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type",    effectiveType);
        putIfPresent(map, "session", request.session());
        putIfPresent(map, "seq",     request.seq());
        map.put("ts",      effectiveTs);
        putIfPresent(map, "url",     request.url());
        putIfPresent(map, "ua",      request.ua());
        putIfPresent(map, "message", request.message());
        putIfPresent(map, "source",  request.source());
        putIfPresent(map, "line",    request.line());
        putIfPresent(map, "col",     request.col());
        putIfPresent(map, "stack",   request.stack());

        String jsonLine = objectMapper.writeValueAsString(map);

        Object lock = fileLocks.computeIfAbsent(targetPath, k -> new Object());
        synchronized (lock) {
            Path path = Path.of(targetPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
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
            Path path = Path.of(targetPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) return;
        if (value instanceof String s && s.isBlank()) return;
        map.put(key, value);
    }


}
