# Console Sniffer v0.3.0 — Release Notes

**Release Date:** 2026-03-07

## What's New

### HTML Inspection Commands

- **`logBody`** — Logs `document.body.innerHTML` to the console, captured by
  console-sniffer so LLMs can inspect the rendered DOM body.
- **`logHead`** — Logs `document.head.innerHTML` to the console, captured by
  console-sniffer so LLMs can inspect the page head element.

### localStorage Scenario Persistence

Scenarios that include `navigate` steps now survive page reloads. Progress is
saved to `localStorage` before each navigation and automatically resumed on the
target page. Saved state expires after 5 minutes.

The total built-in command count is now 14.

## Previous Releases

- [v0.2.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.2.0) — `logPath` and `navigate` commands
- [v0.1.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.1.0) — Initial release
