# Console Sniffer v0.4.0 — Release Notes

**Release Date:** 2026-03-07

## What's New

### Unified Script

`console-trigger.js` has been merged into `console-sniffer.js`. A single
`<script>` tag now provides both log capture and trigger-based UI automation:

```html
<script src="http://<host>:7979/console-sniffer.js?targetPath=/tmp/app.log"></script>
```

### `targetPath` as Trigger Routing Key

The `targetPath` query parameter now serves double duty — it specifies the log
file path on the server **and** acts as the routing key for trigger scenarios.
The separate `target` query parameter on `console-trigger.js` is no longer
needed.

When posting a scenario, set the `target` field to the `targetPath` value from
the script tag:

```json
{ "target": "/tmp/app.log", "steps": [...] }
```

### Breaking Changes

- **`/console-trigger.js` endpoint removed** — The separate JS file and its
  serving endpoint no longer exist. Update any script tags that reference
  `console-trigger.js` to use `console-sniffer.js` with a `targetPath` parameter
  instead.
- **`target` query parameter removed** — Trigger routing is now driven by
  `targetPath`. Scenarios must use the `targetPath` value as their `target`
  field.

## Previous Releases

- [v0.3.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.3.0) — `logBody`/`logHead` commands and localStorage persistence
- [v0.2.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.2.0) — `logPath` and `navigate` commands
- [v0.1.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.1.0) — Initial release
