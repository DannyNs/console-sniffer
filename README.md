# console-sniffer

A lightweight Spring Boot server that captures browser console output to a JSONL log file on disk.

Drop a single `<script>` tag into any web page and every `console.log`, `console.warn`, `console.error`, uncaught exception, and unhandled promise rejection is forwarded to the server and appended to a file you specify.

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

## Known limitations / TODO

- **Stack trace line numbers**: Line numbers in captured stack traces may differ from what Chrome DevTools displays. DevTools applies source maps when rendering stacks visually, but `new Error().stack` returns the raw transformed/bundled positions. Expect differences when using bundlers such as webpack or Vite.

## API endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/console-sniffer.js` | Serves the browser snippet |
| `POST` | `/api/log` | Appends a log entry (JSON body) |
| `DELETE` | `/api/log?targetPath=...` | Truncates (clears) the log file |
