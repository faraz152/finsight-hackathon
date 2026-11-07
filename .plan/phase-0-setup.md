# Phase 0 — Setup (0:00–0:15)

- [x] Clone reference repos into `ref/` (finsight, investerra) — read-only
- [x] Project skills: `finsight-build-run`, `finsight-pipeline`, `finsight-coach` in `.claude/skills/`
- [x] Root `CLAUDE.md`
- [x] `.plan/` phases + checklists
- [x] Gradle scaffold `finsight-app/` (Kotlin 2.0.21, CMP 1.7.1, AGP 8.7.3, Android target only)
- [x] Copy `sms_test_templates.json` into `androidMain/assets`
- [x] `./gradlew :composeApp:assembleDebug` green
- [ ] Phone or emulator visible in `adb devices`
- [ ] (C) Gemini API key in `finsight-app/local.properties` (optional; app works without)

**Exit:** empty app builds.
