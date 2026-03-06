package com.github.dannyns.consolesniffer;

public record LogRequest(
        String  type,        // canonical event type
        String  session,     // 8-char hex, unique per page load
        Integer seq,         // 0-based monotonic sequence number within session
        String  ts,          // ISO 8601 timestamp
        String  url,         // window.location.href — SESSION_START only
        String  ua,          // navigator.userAgent  — SESSION_START only
        String  message,
        String  source,      // script URL           — WINDOW_ERROR only
        Integer line,        // error line number    — WINDOW_ERROR only
        Integer col,         // error column number  — WINDOW_ERROR only
        String  stack,       // stack trace          — WINDOW_ERROR, UNHANDLED_REJECTION
        String  targetPath
) {}
