# Console Sniffer v0.2.0 — Release Notes

**Release Date:** 2026-03-07

## What's New

### Browser Navigation Commands

Two new commands have been added to the console trigger automation API:

- **`logPath`** — Logs the current page URL to the console, which is then
  captured by console-sniffer for backend inspection. Useful for verifying
  navigation state during automated scenarios.
- **`navigate`** — Forces the browser to navigate to a given path via
  `window.location.href`. Enables multi-page test scenarios without manual
  intervention.

The total built-in command count is now 12.

## Previous Release

See [v0.1.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.1.0)
for the full initial feature set including console log capture, remote UI
automation, and REST API documentation.
