# FinSight

FinSight is a privacy-first AI financial companion. It turns everyday transaction notifications and financial records into a verified picture of your money. Built around on-device processing and a hybrid AI coach, it helps you understand spending, check your financial health and plan smarter decisions, without handing your transaction history to the cloud.

Android-first Kotlin Multiplatform prototype, built for Road to DevFest Karachi 2026 (Buildathon 01).

**Journey:** Capture → Understand → Diagnose → Plan → Improve.

---

## What works today

Verified against the code on `main`. Items marked *roadmap* are not in this build.

### Capture
| Feature | Status | Where |
|---|---|---|
| Live capture from notifications (SMS apps from major OEMs + an allowlist of PK bank and wallet apps). Non-allowlisted notifications are ignored in memory. | Working, device-tested | `android/NotificationCaptureService.kt` |
| Demo replay: 59 labelled bank/wallet SMS templates plus 18 extra PK records (salary, K-Electric, Nayatel, Foodpanda, Raast committee, refund, fee, OTP, phishing) and one duplicate notification | Working | `android/FinSightApp.kt`, `assets/sms_test_templates.json` |
| Quick add: type "chai 150 cash", same pipeline | Working | `ui/screens/QuickAddSheet.kt`, `pipeline/Pipeline.kt` |
| Share-in: share any text to FinSight, runs through the pipeline | Working | `android/MainActivity.kt` |
| Statement / receipt upload, scan receipt | *Roadmap* (shown as a locked quick action) | |

### Understand (Transaction Intelligence Pipeline, rules only)
| Stage | What it does | File |
|---|---|---|
| Gate | Drops OTPs, promos, messages with no amount or no completed-payment verb; flags phishing ("account blocked", short links) as a scam warning | `pipeline/Gate.kt` |
| Extract | Amount, direction, merchant, account last 4, balance, reference, parse confidence | `pipeline/RegexExtractor.kt` |
| Integrity | Fingerprint (reference ID, or merchant + amount + account within 10 minutes); duplicate notifications merged into one record; refund / reversal / fee / transfer kinds | `pipeline/Integrity.kt`, `data/Repository.kt` |
| Categorize | Merchant memory (your corrections) → known PK merchants → keywords | `pipeline/RuleCategorizer.kt` |
| Score | Confidence bands (green / yellow / red); below 90% goes to the Review inbox; every record carries "Why?" explanation lines | `pipeline/Pipeline.kt` |
| Learn | One-tap category correction writes merchant memory and re-applies to every record from that merchant | `data/Repository.kt` |

### Diagnose, plan, improve (FinSight Coach)
| Feature | Status |
|---|---|
| Chat with intents in English and Roman Urdu ("Meri spending kahan gayi?"): spend by category, top merchants, month comparison, recurring bills, Pulse, Health, report, "Can I afford it?" | Working |
| Personal numbers always come from Kotlin tools on the device; local answers are templated, not model-written | Working |
| Financial Health 2.0: profile-aware (Salaried, Freelancer, Student, Self-employed, Household), "N of 6 checks", unknown is not zero | Working |
| FinSight Pulse: category up vs previous 30 days, recurring bill day, fees | Working |
| What If? affordability: surplus, latest balance, 1-month emergency reserve, months to afford | Working |
| Privacy Router: On-device / Hybrid / Cloud badge per answer, automatic fallback to on-device when offline, timed out, no key, cloud switch off, or the question names a merchant or account number | Working |
| Gemini hybrid answers (redacted facts only) | Working **when a Gemini API key is configured**; without a key every answer is on-device |
| On-device Gemma via LiteRT-LM (pipeline long tail + local chat) | *In progress / roadmap*. `LocalLlm` interface exists; the build ships `NoLocalLlm` |

### Screens
Home · Transactions (Review filter, "Why?" sheet) · centre hexagon quick actions (Add transaction, Ask Coach, Can I afford it?, Import messages, Scan receipt *locked*) · Coach ("What was shared?" sheet) · Privacy Center (notification access, cloud switch, cloud log, delete all).

### Known limits of this build
- Storage: app-private files (`filesDir/finsight_store`), atomic writes, survives restarts. SQLDelight + SQLCipher encryption is roadmap.
- The confidence score is a heuristic, not a calibrated probability.
- Data coverage is computed from balance chains (or active days), not from all your accounts.
- No onboarding flow yet; the Privacy tab carries the disclosure and the notification-access button.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  ANDROID APP (Compose Multiplatform UI, InvesTerra design tokens)    │
│   Home   Transactions (+Review, Why?)   [hexagon]   Coach   Privacy  │
│                 AppState (manual DI, StateFlow)                      │
└──────────────────────────┬───────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────────┐
│  SHARED CORE (commonMain, pure Kotlin, JVM-tested)                   │
│                                                                      │
│  ┌──────── Transaction Intelligence Pipeline (no network) ────────┐  │
│  │ RawInput ─► Gate ─► RegexExtractor ─► Integrity ─►             │  │
│  │             RuleCategorizer ─► Score/bands ─► Repository       │  │
│  │                         ▲                                      │  │
│  │                         └── merchant memory ◄── user correction│  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────── FinSight Coach (hybrid assistant) ─────────────────┐  │
│  │ question ─► intent ─► PrivacyRouter ─┬─► LOCAL:  Tools ─► text │  │
│  │                                      ├─► HYBRID: Tools ─►      │  │
│  │                                      │   Redactor ─►           │  │
│  │                                      │   SanitizedPrompt ─►    │  │
│  │                                      │   Gemini ─►             │  │
│  │                                      │   NumberVerifier        │  │
│  │                                      └─► CLOUD: scrubbed       │  │
│  │                                          question only         │  │
│  │ Tools · HealthScore · Pulse · WhatIf · CoachLog (cloud log)    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  Repository (StateFlow + file store)                                 │
│  interface LocalLlm (NoLocalLlm today)                               │
│  interface CloudLlm { complete(SanitizedPrompt) }                    │
└──────────────┬────────────────────────┬──────────────────────────────┘
               │                        │
┌──────────────▼──────────┐  ┌──────────▼───────────────────────────────┐
│ androidMain             │  │ INPUT CHANNELS (on-device)               │
│ GeminiCloudLlm          │  │ NotificationCaptureService (allowlist)   │
│  (REST, the ONLY        │  │ Share-in (ACTION_SEND text)              │
│   network code)         │  │ Quick add                                │
│ LiteRtLlm: roadmap      │  │ Demo replay (assets JSON)                │
└─────────────────────────┘  └──────────────────────────────────────────┘

  No READ_SMS. INTERNET is used only by GeminiCloudLlm, which accepts
  only a SanitizedPrompt and every call is written to the Cloud log.
```

Money is `Long` minor units (paisa) plus an ISO currency code, never `Double`.

---

## Privacy guarantees and how the code enforces them

| Guarantee | Enforcement in code |
|---|---|
| Only redacted facts can reach the cloud | `CloudLlm.complete()` takes only a `SanitizedPrompt`. Its constructor is `private`; the only factory is `internal` and called from `Redactor.build()` (`coach/Privacy.kt`). Facts are a sealed `SafeFact` allowlist: percentage, rounded amount (nearest PKR 1,000), category share, count, months ratio, label. |
| Questions naming a merchant, phone number or long digit string stay local | `Redactor.scrub()` returns null; the router forces LOCAL and the answer says why. |
| Cloud cannot invent figures about you | `NumberVerifier.verify()` drops any personal sentence whose numbers are not in the facts sent; the answer says how many were removed. |
| Every cloud call is inspectable | `CoachLog` stores route, model, exact payload, latency and status; shown in Privacy → Cloud log, and per answer in "What was shared?". |
| Exactly one file touches the network | `androidMain/.../GeminiCloudLlm.kt`. Check: `grep -rlE 'java\.net\|HttpURLConnection\|okhttp\|ktor' finsight-app/composeApp/src` returns only that file; `PrivacyBoundaryTest` enforces it in CI. |
| No SMS inbox access | Manifest has no `READ_SMS` / `RECEIVE_SMS`. Capture is a `NotificationListenerService` with a package allowlist. |
| No cloud backup of your data | `android:allowBackup="false"`. |
| User control | Cloud AI switch (Privacy), delete one record and its raw text ("Why?" sheet), delete all local data. |
| Works offline | Pipeline, Health, Pulse, What If and local Coach answers need no network. |

The only permission requested is `INTERNET` (used by Coach only) plus the user-granted notification-listener access.

---

## Tech stack

| Area | Choice |
|---|---|
| Language / platform | Kotlin 2.0.21, Kotlin Multiplatform (Android target; shared code in `commonMain`) |
| UI | Compose Multiplatform 1.7.1, Material 3, InvesTerra "Indigo Night" design tokens, Manrope font |
| Build | AGP 8.7.3, Gradle 8.14, compileSdk 34, minSdk 26, JDK 21 (JVM target 11) |
| Concurrency | kotlinx.coroutines 1.9.0, StateFlow |
| Cloud AI | Gemini via the REST `generateContent` endpoint (`HttpURLConnection`, 6 s connect / 9 s timeout), default model `gemini-flash-latest` |
| On-device AI | Gemma via LiteRT-LM: planned, behind the `LocalLlm` interface |
| Libraries deliberately not used today | SQLDelight, Koin, Ktor (roadmap) |

---

## Build and run

```bash
cd finsight-app
cp local.properties.example local.properties   # set sdk.dir; optional GEMINI_API_KEY=
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
adb shell am start -n com.finsight.app/com.finsight.android.MainActivity
./gradlew :composeApp:testDebugUnitTest        # pipeline accuracy, coach flow, design-token guard
```

- APK: `finsight-app/composeApp/build/outputs/apk/debug/composeApp-debug.apk`
- Grant notification access: Privacy tab → Enable, or
  `adb shell cmd notification allow_listener com.finsight.app/com.finsight.android.NotificationCaptureService`
- Post a test bank notification (quote the whole remote command):
  ```bash
  adb shell "cmd notification post -S bigtext -t 8558 fs1 'Rs 1,250.00 sent to KFC Gulberg via JazzCash. Fee: Rs 0.00. Balance: Rs 8,750.00. TID: 033050058799.'"
  ```
- Gemini (optional): `GEMINI_API_KEY=...` and optional `GEMINI_MODEL=` in `finsight-app/local.properties`, then rebuild. Never commit the key. No key means on-device answers only.
- Xiaomi/MIUI `INSTALL_FAILED_USER_RESTRICTED`: enable "Install via USB" in Developer options.

More detail: `.claude/skills/finsight-build-run/SKILL.md`. Demo run-sheet: [docs/DEMO.md](docs/DEMO.md). Judge Q&A: [docs/QA.md](docs/QA.md).

---

## Accuracy

Rules-only pipeline on the 59 labelled templates in `sms_test_templates.json`: **59/59 amount correct, 59/59 direction (debit/credit) correct** (14/14 Pakistani templates). Measured by `PipelineAccuracyTest` (`./gradlew :composeApp:testDebugUnitTest`, prints `TEMPLATES= ACCEPTED= AMOUNT_OK= TYPE_OK=`).

Caveat: these are the formats the rules were written against, so this is a regression score, not an unseen-format score. Methodology and category numbers: [docs/ACCURACY.md](docs/ACCURACY.md).

---

## Repo layout

```
README.md                      this file
HACKATHON_KMP_PROTOTYPE.md     full product + architecture plan (source of truth for "what")
CLAUDE.md                      rules for AI-assisted work in this repo
.plan/                         phases and checklists (source of truth for "what's next")
.claude/skills/                project skills: build-run, pipeline, coach, design-system
docs/                          DEMO.md, QA.md, ACCURACY.md
finsight-app/                  Gradle root
  composeApp/
    MANROPE-OFL.txt
    src/commonMain/kotlin/com/finsight/
      domain/      Money, RawInput, Transaction, Category
      pipeline/    Gate, RegexExtractor, Integrity, RuleCategorizer, Pipeline
      data/        Repository, Persistence (file store), coverage
      coach/       Tools, Insights (HealthScore, Pulse, WhatIf), Privacy, CoachAgent
      ai/          LocalLlm, CloudLlm interfaces
      ui/          App, AppState, designsystem/, screens/
    src/androidMain/kotlin/com/finsight/android/
      FinSightApp, MainActivity, NotificationCaptureService, GeminiCloudLlm
    src/androidMain/assets/sms_test_templates.json
    src/androidUnitTest/   PipelineAccuracyTest, CoachFlowTest, DesignTokenGuardTest
```

## Branches

| Branch | Purpose |
|---|---|
| `main` | Everything merged; demo builds come from here |
| `phase-3-ui` | UI collaborator's branch (screens built with AI help), merged into `main` at checkpoints |
| `phase-1-pipeline`, `phase-2-coach`, `phase-3-app` | Historical stacked branches, reference only |

## Team

| Name | Role |
|---|---|
| _TBD_ | KMP / data |
| _TBD_ | UI |
| _TBD_ | Coach / AI |
| _TBD_ | Pipeline / tests |

Prior work: SMS templates, category taxonomy and design tokens come from the team's earlier FinSight (Flutter) and InvesTerra projects. All code in this repo was written fresh for the hackathon.

## Licence notes

- Manrope font: SIL Open Font License 1.1, see `finsight-app/composeApp/MANROPE-OFL.txt`.
- Project licence: _TBD_.
