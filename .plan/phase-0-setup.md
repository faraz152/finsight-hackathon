# Phase 0 — Setup (0:00–0:15)

- [x] Clone reference material into `ref/` (local only, gitignored) — read-only
- [x] Project skills: `finsight-build-run`, `finsight-pipeline`, `finsight-coach` in `.claude/skills/`
- [x] Root `CLAUDE.md`
- [x] `.plan/` phases + checklists
- [x] Gradle scaffold `finsight-app/` (Kotlin 2.0.21, CMP 1.7.1, AGP 8.7.3, Android target only)
- [x] Copy `sms_test_templates.json` into `androidMain/assets`
- [x] `./gradlew :composeApp:assembleDebug` green
- [x] Phone or emulator visible in `adb devices`
- [x] Cloud AI key: Google key blocked (project cannot generate); Gemma 4 via OpenRouter works (`OPENROUTER_API_KEY`)

**Exit:** empty app builds.
