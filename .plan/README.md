# FinSight build plan (2 hours, 7 Nov 2026)

Tick `[x]` as tasks land. Order matters: each phase ends with a **working, demoable APK**. If time runs out, stop at the last green phase and demo that.

| Phase | Time | Goal | Exit check |
|---|---|---|---|
| [P0 Setup](phase-0-setup.md) | 0:00–0:15 | Skills, CLAUDE.md, plan, Gradle scaffold builds | `assembleDebug` green |
| [P1 Core pipeline](phase-1-pipeline.md) | 0:15–0:45 | Rules-only pipeline + integrity + tests | ≥ 90% amount accuracy on templates |
| [P2 App shell + capture](phase-2-app.md) | 0:45–1:15 | Home / Transactions / Review / Why? + demo replay + live notification | 58 SMS import on device |
| [P3 Coach](phase-3-coach.md) | 1:15–1:40 | Chat (local + Gemini hybrid), Health 2.0, Pulse, What If, Privacy Center | Health + "what was shared" demo works |
| [P4 Demo ready](phase-4-demo.md) | 1:40–2:00 | Polish, release APK, rehearse, backup video | Demo script runs twice |

Owners (adjust on site): A = KMP/data, B = UI, C = Coach/AI, D = Pipeline/tests.

Out of scope today (say "roadmap"): LiteRT-LM Gemma on device, SQLDelight persistence, SQLCipher, PDF export, committee/Zakat modules, iOS.
