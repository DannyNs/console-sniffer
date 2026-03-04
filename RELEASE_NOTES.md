# Console Sniffer v0.1.0 — Release Notes

**Release Date:** 2026-03-04

## Overview

Console Sniffer is a lightweight Spring Boot server that captures browser console
output to JSONL log files on disk and enables LLM-friendly remote UI automation
via a REST API. Drop a `<script>` tag into any web app — no framework dependencies
required.

## Features

### Console Log Capture

- Intercepts `console.log`, `.warn`, `.error`, `.info`, and `.debug` calls
- Captures uncaught exceptions (`window.onerror`) and unhandled Promise rejections
- Writes structured JSONL to any file path on the server
- Per-session IDs and monotonic sequence numbers for easy correlation
- Configurable log levels via the `levels` query parameter
- Persistent mode to preserve logs across page reloads
- Thread-safe concurrent writes with per-file locking

### Remote UI Automation (Console Trigger)

- Long-polling architecture for real-time scenario delivery to the browser
- 10 built-in commands: `click`, `dblclick`, `type`, `select`, `wait`,
  `waitFor`, `waitForHidden`, `find`, `assertExists`, `assertText`
- Multi-app routing via the `target` parameter
- LLM-friendly command catalog endpoint (`GET /api/trigger/commands`)
- Exponential backoff on connection errors

### REST API

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/console-sniffer.js` | Serve log-capture snippet |
| POST | `/api/log` | Receive a log entry |
| DELETE | `/api/log?targetPath=...` | Clear a log file |
| GET | `/console-trigger.js` | Serve automation snippet |
| GET | `/api/trigger/commands` | Command catalog (JSON) |
| POST | `/api/trigger/scenarios` | Submit a test scenario |
| GET | `/api/trigger/scenarios/poll` | Long-poll for next scenario |

### Infrastructure

- Spring Boot 3.2.3, Java 17+
- Single executable JAR (`console-sniffer.jar`)
- Default port: **7979**
- CORS enabled for all origins
- GitHub Actions CI: build verification and version checks
- GitHub Packages CD: automatic Maven publish on release

## Getting Started

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/console-sniffer.jar

# Add to your HTML
<script src="http://localhost:7979/console-sniffer.js?targetPath=/tmp/app.log"></script>
<script src="http://localhost:7979/console-trigger.js"></script>
```

## Known Limitations

- Stack trace line numbers reflect bundled source positions, not original source
  (source maps are not resolved server-side).
