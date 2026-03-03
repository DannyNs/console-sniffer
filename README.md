# console-sniffer

A lightweight Spring Boot server that captures browser console output to a JSONL log file on disk **and** enables remote UI automation via an LLM-friendly command API.

Drop a single `<script>` tag into any web page and every `console.log`, `console.warn`, `console.error`, uncaught exception, and unhandled promise rejection is forwarded to the server and appended to a file you specify. A second script tag enables an LLM (or any HTTP client) to remotely drive browser interactions — clicking buttons, filling forms, waiting for elements — by posting scenario scripts to a REST API.

## Requirements

- Java 17+
- Maven 3.6+ (build only)

## Build

```bash
mvn clean package -DskipTests
```

Produces `target/console-sniffer.jar`.

## Run

```bash
java -jar target/console-sniffer.jar
```

The server starts on port **7979** by default. Override with:

```bash
java -jar target/console-sniffer.jar --server.port=8080
```

## Usage

### Console Sniffer (log capture)

Add the script tag to the page you want to monitor:

```html
<script src="http://<host>:7979/console-sniffer.js?targetPath=/path/to/app.log"></script>
```

### Query parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `targetPath` | yes | — | Absolute path of the log file to write on the server |
| `persistent` | no | `false` | Set to `true` to keep previous log entries across page reloads; omit to clear the file on each load |
| `levels` | no | all | Comma-separated list of event types to capture, e.g. `ERROR,WARN`. Other types are silently dropped (sequence numbers still advance so gaps indicate filtered events) |

### Example

```html
<!-- Capture only errors and warnings, keep entries across reloads -->
<script src="http://192.168.1.10:7979/console-sniffer.js?targetPath=/tmp/app.log&persistent=true&levels=ERROR,WARN"></script>
```

## Log format

Each event is written as a single JSON line (JSONL). Fields that are not applicable to a given event type are omitted.

```jsonc
{"type":"SESSION_START","session":"a1b2c3d4","seq":0,"ts":"2024-01-15T10:23:45.123Z","url":"http://localhost:3000/","ua":"Mozilla/5.0 ..."}
{"type":"LOG",          "session":"a1b2c3d4","seq":1,"ts":"2024-01-15T10:23:45.200Z","message":"Hello world"}
{"type":"WARN",         "session":"a1b2c3d4","seq":2,"ts":"2024-01-15T10:23:45.210Z","message":"Something looks off","stack":"    at App (http://localhost:3000/src/App.tsx:12:10)"}
{"type":"WINDOW_ERROR", "session":"a1b2c3d4","seq":3,"ts":"2024-01-15T10:23:45.220Z","message":"Cannot read properties of undefined","source":"http://localhost:3000/src/App.tsx","line":42,"col":7,"stack":"TypeError: Cannot read properties of undefined\n    at App ..."}
{"type":"UNHANDLED_REJECTION","session":"a1b2c3d4","seq":4,"ts":"2024-01-15T10:23:45.230Z","message":"fetch failed","stack":"Error: fetch failed\n    at ..."}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `type` | string | Event type (see below) |
| `session` | string | 8-char hex ID, unique per page load |
| `seq` | number | Monotonic sequence number within the session; gaps indicate filtered events |
| `ts` | string | ISO 8601 timestamp from the browser |
| `message` | string | Formatted log message |
| `url` | string | `window.location.href` — `SESSION_START` only |
| `ua` | string | `navigator.userAgent` — `SESSION_START` only |
| `source` | string | Script URL where the error occurred — `WINDOW_ERROR` only |
| `line` | number | Error line number — `WINDOW_ERROR` only |
| `col` | number | Error column number — `WINDOW_ERROR` only |
| `stack` | string | Stack trace — `WINDOW_ERROR` and `UNHANDLED_REJECTION`; also included for console methods |

### Event types

| Type | Trigger |
|---|---|
| `SESSION_START` | Fired once per page load, before any console interception |
| `LOG` | `console.log()` |
| `INFO` | `console.info()` |
| `WARN` | `console.warn()` |
| `ERROR` | `console.error()` |
| `DEBUG` | `console.debug()` |
| `WINDOW_ERROR` | Uncaught exception (`window.onerror`) |
| `UNHANDLED_REJECTION` | Unhandled promise rejection (`window.addEventListener('unhandledrejection', ...)`) |

### Console Trigger (remote UI automation)

Add the trigger script to the page you want to control:

```html
<script src="http://<host>:7979/console-trigger.js"></script>
```

The script starts long-polling the server for scenarios. Both scripts can be loaded together or independently.

#### LLM workflow

1. **Discover commands** — `GET /api/trigger/commands` returns the full command catalog in a JSON format designed for LLM consumption.

2. **Post a scenario** — `POST /api/trigger/scenarios` with a JSON body:

```json
{
  "name": "Join room",
  "steps": [
    { "command": "click", "selector": "#menu-btn" },
    { "command": "waitFor", "selector": ".menu-dropdown" },
    { "command": "click", "selector": ".join-room-btn" },
    { "command": "waitFor", "selector": "#room-name-input" },
    { "command": "type", "selector": "#room-name-input", "text": "Test Room" },
    { "command": "click", "selector": "#confirm-join" },
    { "command": "assertExists", "selector": ".room-view" }
  ]
}
```

3. **Automatic execution** — The browser picks up the scenario via long polling and executes the steps sequentially.

#### Available commands

| Command | Parameters | Description |
|---|---|---|
| `click` | `selector` | Click a DOM element |
| `dblclick` | `selector` | Double-click a DOM element |
| `type` | `selector`, `text`, `clear?` | Type text into an input (clears first by default) |
| `select` | `selector`, `value` | Select a dropdown option by value |
| `wait` | `ms` | Pause for a fixed delay |
| `waitFor` | `selector`, `timeout?` | Wait for an element to appear (default 5s) |
| `waitForHidden` | `selector`, `timeout?` | Wait for an element to disappear |
| `find` | `selector`, `timeout?` | Locate an element with retry |
| `assertExists` | `selector` | Assert an element exists in the DOM |
| `assertText` | `selector`, `text`, `contains?` | Assert element text content |

Scenarios are fire-and-forget: once polled by the browser they are removed from the server. Unpicked scenarios are automatically cleaned up after 5 minutes.

## Known limitations / TODO

- **Stack trace line numbers**: Line numbers in captured stack traces may differ from what Chrome DevTools displays. DevTools applies source maps when rendering stacks visually, but `new Error().stack` returns the raw transformed/bundled positions. Expect differences when using bundlers such as webpack or Vite.

## API endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/console-sniffer.js` | Serves the log-capture browser snippet |
| `POST` | `/api/log` | Appends a log entry (JSON body) |
| `DELETE` | `/api/log?targetPath=...` | Truncates (clears) the log file |
| `GET` | `/console-trigger.js` | Serves the UI-automation browser snippet |
| `GET` | `/api/trigger/commands` | Returns the command catalog (LLM-friendly JSON) |
| `POST` | `/api/trigger/scenarios` | Submits a scenario for browser execution |
| `GET` | `/api/trigger/scenarios/poll` | Long-polling endpoint used by `console-trigger.js` |
