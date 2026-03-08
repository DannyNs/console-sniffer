# Console Sniffer v0.5.0 — Release Notes

**Release Date:** 2026-03-08

Code quality and cleanup release — seven fixes addressing best-practice
violations found during a comprehensive code review.

## Changes

### Server-side

- **Remove redundant `serveJs()` endpoint** — Spring Boot already serves static
  files from `src/main/resources/static/` with HTTP caching and 304 support.
  The explicit `@GetMapping("/console-sniffer.js")` in `LogController` bypassed
  all of this; it has been removed. (#18)

- **Remove extra blank line in `LogService.java`** — Formatting cleanup. (#13)

### Client-side (`console-sniffer.js`)

- **Rename `[console-trigger]` log prefixes to `[console-sniffer]`** — After
  the v0.4.0 merge, all JS log messages still used the old `[console-trigger]`
  prefix. Now consistently `[console-sniffer]`. (#16)

- **Extract duplicated native setter lookup in `type` executor** — The
  `Object.getOwnPropertyDescriptor(...)` call for the value setter was
  duplicated in the clear and set phases. Extracted into a single lookup with
  a shared `setValue` helper. (#15)

- **Rename `waitForHidden` helper to avoid shadowing executor key** — The free
  function `waitForHidden` and `executors.waitForHidden` shared the same name.
  The helper is now `waitForElementHidden`. (#19)

- **Document `find` command as alias for `waitFor`** — Both call the same
  underlying `waitForElement` function. Added a clarifying comment and updated
  the command catalog description. (#14)

- **Remove unnecessary per-step `saveProgress` calls** — `saveProgress` was
  called after every step but is only needed before `navigate`. Removes
  unnecessary `localStorage` writes during scenario execution. (#17)

## Previous Releases

- [v0.4.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.4.0) — Unified script (`console-trigger.js` merged into `console-sniffer.js`)
- [v0.3.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.3.0) — `logBody`/`logHead` commands and localStorage persistence
- [v0.2.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.2.0) — `logPath` and `navigate` commands
- [v0.1.0](https://github.com/DannyNs/console-sniffer/releases/tag/v0.1.0) — Initial release
