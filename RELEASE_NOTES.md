# Console Sniffer v0.1.1 — Release Notes

## Changes

- **CI: automatic version from release tag** — the Maven publish workflow now sets the artifact version from the Git release tag, removing the need to manually update `pom.xml` before each release

---

# Console Sniffer v0.1.0 — Release Notes

**Initial release** of Console Sniffer, a lightweight Spring Boot server for browser console capture and LLM-driven remote UI automation.

## Highlights

- **Zero-dependency browser integration** — add a single `<script>` tag to any web app
- **Two complementary modules** in one server: log capture and UI automation
- **LLM-friendly design** — command catalog endpoint, fire-and-forget scenarios, long-polling architecture

---

## Console Sniffer — Log Capture

Captures browser console output and errors to a JSONL file on disk.

- Intercepts `console.log`, `.info`, `.warn`, `.error`, `.debug`
- Captures uncaught exceptions (`window.onerror`) and unhandled promise rejections
- Session tracking with 8-char hex IDs and monotonic sequence numbers
- Configurable event-level filtering (`levels=ERROR,WARN`)
- Persistent mode to preserve logs across page reloads
- Stack trace capture for errors
- CORS-enabled for cross-origin use

## Console Trigger — Remote UI Automation

Enables LLMs and automated clients to drive browser interactions via REST.

- **10 commands**: `click`, `dblclick`, `type`, `select`, `wait`, `waitFor`, `waitForHidden`, `find`, `assertExists`, `assertText`
- Long-polling architecture (30s timeout) for low-latency command delivery
- Sequential step execution with fail-on-first-error behavior
- **Multi-app routing** via `target` parameter — one server drives multiple browser sessions
- LLM-friendly command catalog at `GET /api/trigger/commands`
- Automatic cleanup of stale scenarios (5-minute retention)
- Compatible with framework-managed inputs (React, Vue) via native setter dispatch

---

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/console-sniffer.js` | Browser log-capture snippet |
| `POST` | `/api/log` | Append a log entry |
| `DELETE` | `/api/log?targetPath=...` | Clear/truncate log file |
| `GET` | `/console-trigger.js` | Browser UI automation snippet |
| `GET` | `/api/trigger/commands` | Command catalog (JSON) |
| `POST` | `/api/trigger/scenarios` | Submit an automation scenario |
| `GET` | `/api/trigger/scenarios/poll` | Long-polling endpoint |

## Tech Stack

- Java 17+, Spring Boot 3.2.3, Maven
- Single dependency: `spring-boot-starter-web`
- Published to GitHub Packages (`com.github.dannyns:console-sniffer`)

## Quick Start

```html
<!-- Log capture -->
<script src="http://host:7979/console-sniffer.js?targetPath=/tmp/app.log"></script>

<!-- UI automation -->
<script src="http://host:7979/console-trigger.js?target=my-app"></script>
```

```bash
java -jar console-sniffer.jar              # default port 7979
java -jar console-sniffer.jar --server.port=8080
```

## Known Limitations

- Stack trace line numbers may differ from DevTools when source maps are in use (bundlers like webpack/Vite return raw transformed positions in `new Error().stack`)
