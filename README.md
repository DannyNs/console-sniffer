# console-sniffer

A lightweight Spring Boot server that captures browser console output to a JSONL log file on disk **and** enables remote UI automation via an LLM-friendly command API.

Drop a single `<script>` tag into any web page and every `console.log`, `console.warn`, `console.error`, uncaught exception, and unhandled promise rejection is forwarded to the server and appended to a file you specify. The same script also starts long-polling for trigger scenarios, enabling an LLM (or any HTTP client) to remotely drive browser interactions — clicking buttons, filling forms, waiting for elements — by posting scenario scripts to a REST API.

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

Add the script tag to the page you want to monitor and control:

```html
<script src="http://<host>:7979/console-sniffer.js?targetPath=/path/to/app.log"></script>
```

This single script tag enables **both** log capture and trigger-based UI automation. The `targetPath` value serves double duty: it specifies the log file path on the server and acts as the routing key for trigger scenarios.

### Query parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `targetPath` | yes | — | Absolute path of the log file to write on the server. Also used as the routing key for trigger scenarios. |
| `persistent` | no | `false` | Set to `true` to keep previous log entries across page reloads; omit to clear the file on each load. Recommended when using `navigate` commands. |
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

## Remote UI automation (trigger)

The script automatically starts long-polling the server for trigger scenarios using the `targetPath` as the routing key.

### Multi-app routing

When multiple apps use the same console-sniffer server, each app's `targetPath` acts as its unique routing key. A scenario with `"target": "/tmp/app.log"` will only be picked up by the browser whose script tag has `?targetPath=/tmp/app.log`.

| Scenario target | Browser targetPath | Match? |
|---|---|---|
| `"/tmp/app.log"` | `"/tmp/app.log"` | Yes |
| `"/tmp/app.log"` | `"/tmp/other.log"` | No |

### LLM workflow

1. **Discover commands** — `GET /api/trigger/commands` returns the full command catalog in a JSON format designed for LLM consumption.

2. **Post a scenario** — `POST /api/trigger/scenarios` with a JSON body:

```json
{
  "name": "Join room",
  "target": "/tmp/app.log",
  "steps": [
    { "command": "click", "selector": "#menu-btn" },
    { "command": "waitFor", "selector": ".menu-dropdown" },
    { "command": "click", "selector": ".join-room-btn" },
    { "command": "waitFor", "selector": "#room-name-input" },
    { "command": "type", "selector": "#room-name-input", "text": "Test Room" },
    { "command": "click", "selector": "#confirm-join" },
    { "command": "assertExists", "selector": ".room-view" },
    { "command": "logPath" }
  ]
}
```

The `target` value must match the `targetPath` from the browser's script tag.

3. **Automatic execution** — The browser picks up the scenario via long polling and executes the steps sequentially.

### Available commands

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
| `logPath` | *(none)* | Log the current page URL to the console (captured by console-sniffer) |
| `logBody` | *(none)* | Log the current page body HTML to the console (captured by console-sniffer) |
| `logHead` | *(none)* | Log the current page head HTML to the console (captured by console-sniffer) |
| `navigate` | `path` | Navigate the browser to a given URL path |

Scenarios are fire-and-forget: once polled by the browser they are removed from the server. Unpicked scenarios are automatically cleaned up after 5 minutes.

### Navigate resilience (localStorage persistence)

When a scenario contains a `navigate` step, the page reloads and the in-memory scenario state would normally be lost. To handle this, the script automatically saves scenario progress to `localStorage` before each navigation and resumes execution after the new page loads.

**How it works:**
- Before each step (and specifically before `navigate`), progress is saved to `localStorage` with the index of the next step to run.
- On page load, the script checks for saved state and resumes the scenario from where it left off.
- On scenario completion or failure, the saved state is cleared.
- Saved state older than **5 minutes** is automatically discarded (e.g. if a tab is closed mid-scenario).

**Limitations:**
- **Same-origin only** — `localStorage` is scoped to the origin. If `navigate` redirects to a different origin, the saved state is inaccessible and the scenario cannot resume.
- **Requires `console-sniffer.js` on the target page** — The destination page must also include the `console-sniffer.js` script tag for resumption to work.

**Recommendation:** When using `navigate` in scenarios, add `persistent=true` to the script tag to prevent the log file from being cleared on page reload:

```html
<script src="http://<host>:7979/console-sniffer.js?targetPath=/tmp/app.log&persistent=true"></script>
```

## Known limitations / TODO

- **Stack trace line numbers**: Line numbers in captured stack traces may differ from what Chrome DevTools displays. DevTools applies source maps when rendering stacks visually, but `new Error().stack` returns the raw transformed/bundled positions. Expect differences when using bundlers such as webpack or Vite.

## API endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/console-sniffer.js` | Serves the unified browser snippet (log capture + trigger) |
| `POST` | `/api/log` | Appends a log entry (JSON body) |
| `DELETE` | `/api/log?targetPath=...` | Truncates (clears) the log file |
| `GET` | `/api/trigger/commands` | Returns the command catalog (LLM-friendly JSON) |
| `POST` | `/api/trigger/scenarios` | Submits a scenario for browser execution |
| `GET` | `/api/trigger/scenarios/poll?target=...` | Long-polling endpoint used by `console-sniffer.js` (`target` = the `targetPath` value) |
