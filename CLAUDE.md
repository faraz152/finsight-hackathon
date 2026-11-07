# FinSight Hackathon (Road to DevFest Karachi 2026)

Privacy-first AI financial companion. Android-first Kotlin Multiplatform app. **Deadline: today, demo-ready build in ~2 hours.** One working story beats many half features.

## Layout
- `finsight-app/` — the app (Gradle root). Single module `composeApp`, Android target only.
  - `commonMain/.../domain` models (Money = Long minor units + currency, never Double)
  - `commonMain/.../pipeline` Gate → RegexExtractor → Integrity → RuleCategorizer → Scorer → Pipeline (+ merchant memory)
  - `commonMain/.../coach` Tools, HealthScore, Pulse, WhatIf, PrivacyRouter/Redactor/SanitizedPrompt, CoachAgent
  - `commonMain/.../data` in-memory repository (StateFlow); `ui/` Compose screens
  - `androidMain/.../android` MainActivity, NotificationCaptureService, DemoReplay, GeminiCloudLlm (only network code)
- `HACKATHON_KMP_PROTOTYPE.md` — the full plan (product, architecture, scope). Source of truth for *what*.
- `.plan/` — phases, tasks, checklists. Source of truth for *what's next*. Tick boxes as you go.
- `ref/finsight`, `ref/investerra` — old projects, **read-only reference**. Never edit, never build.

## Commands
```bash
cd finsight-app
./gradlew :composeApp:assembleDebug            # build APK
./gradlew :composeApp:installDebug             # install on connected phone/emulator
./gradlew :composeApp:testDebugUnitTest        # JVM tests (commonTest)
~/Library/Android/sdk/platform-tools/adb devices
```
Use the `finsight-build-run` skill for install/run/notification-test details.

## Rules (non-negotiable)
- **Privacy boundary:** only `androidMain/.../GeminiCloudLlm.kt` may touch the network. `CloudLlm` accepts only `SanitizedPrompt`; never pass raw SMS, merchant names, person names, account digits or exact balances to it.
- **Numbers come from the device:** Coach answers use tool output (Kotlin), never model-invented figures.
- **Rules first, LLM second.** The pipeline must work rules-only (no model on phone yet). `LocalLlm` is optional (`NoLocalLlm`).
- **Money:** `Long` paisa + ISO code. Format with `formatPkr`.
- **Keep it minimal:** no new libraries unless a task needs them (no SQLDelight/Koin/Ktor today). Persistence is in-memory + demo replay.
- Gemini key lives in `finsight-app/local.properties` (`GEMINI_API_KEY=`); never commit it. Missing key → Coach falls back to on-device answers.

## Versions (pinned, known to build on this machine)
Kotlin 2.0.21 · Compose Multiplatform 1.7.1 · AGP 8.7.3 · Gradle 8.14 · compileSdk 34 · minSdk 26 · JDK 21.

## Skills
- `finsight-build-run` — build, install, run, post test notifications, troubleshoot Gradle.
- `finsight-pipeline` — add/fix SMS parsing rules, categories, integrity checks; test against `sms_test_templates.json`.
- `finsight-coach` — add Coach tools/intents, Health/Pulse/What-If, privacy router rules.
