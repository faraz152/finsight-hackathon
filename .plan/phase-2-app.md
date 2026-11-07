# Phase 2 — App shell + capture (0:45–1:15) · owners B + A

Skill: `finsight-build-run`.

- [x] Theme: InvesTerra-style dark (Indigo Night), green/yellow/red bands
- [x] Bottom nav: Home · Transactions · Coach · Privacy
- [x] Home: month spend / income, Health score card, **data coverage %**, top Pulse card, category bars, "Import demo SMS"
- [x] Transactions: list with source badge (Rule / You), band colour, kind chip (Refund, Transfer, Duplicate×2)
- [x] Review Inbox filter (yellow/red) + one-tap category correction → merchant memory
- [x] "Why?" bottom sheet: checks, rule, confidence, raw text
- [x] Quick add line ("chai 150 cash") through the same pipeline
- [x] `DemoReplay` loads assets JSON → pipeline (shows "N by rules, M review, K dropped")
- [x] `NotificationCaptureService` — SMS app allowlist + JazzCash/Easypaisa + shell (debug) → pipeline
- [x] Onboarding / Privacy: prominent disclosure + button to notification access settings
- [x] Installed and tested on device (Xiaomi redwood): replay + live notification → KFC Gulberg, Food, 95% green

**Exit:** live notification appears categorized in seconds.
