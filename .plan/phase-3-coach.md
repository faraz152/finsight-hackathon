# Phase 3 — FinSight Coach (1:15–1:40) · owner C (+B for UI)

Skill: `finsight-coach`.

- [x] `coach/Tools.kt` — spend by category, top merchants, compare months, recurring bills
- [x] `coach/HealthScore.kt` — Health 2.0: profile, unknown ≠ zero, N of 6 checks, coverage
- [x] `coach/Pulse.kt` — 2 detectors: category up vs last month; recurring bill day
- [x] `coach/WhatIf.kt` — affordability: price vs surplus, balance, emergency reserve → months + verdict
- [x] `coach/Privacy.kt` — SafeFact, SanitizedPrompt (private ctor), Redactor, NumberVerifier, CoachLog
- [x] `coach/PrivacyRouter.kt` — LOCAL / HYBRID / CLOUD + fallback, cloud switch
- [x] `coach/CoachAgent.kt` — intents (English + Roman Urdu), local templated answers, hybrid call
- [x] `androidMain/GeminiCloudLlm.kt` — REST call, 8 s timeout, the only network code
- [x] Coach screen: chat bubbles + route badge (On-device / Hybrid / Cloud) + "What was shared?"
- [x] Suggested question chips (incl. "Meri spending kahan gayi?", "Can I afford PKR 150,000?")
- [x] Privacy tab: sources, cloud switch, Cloud log, delete all data

**Exit:** airplane mode → on-device answer; online → hybrid answer + payload shown.
