# FinSight KMP: Hackathon Prototype Plan

**Event:** Road to DevFest Karachi 2026, Buildathon 01. Saturday 7 Nov 2026, 9:00 AM PKT, Disrupt Karachi.
**Team:** formed on site (2 to 4 people). This doc is written so a new teammate can read it in 10 minutes and start building.

**One line:** FinSight is a privacy-first AI financial companion. It captures transactions automatically from the sources you enable (SMS and app notifications already on your phone), verifies them, using on-device rules and Gemma. Then **FinSight Coach**, a hybrid AI finance assistant, tells you how financially healthy you are, warns you early (FinSight Pulse) and helps you plan (What If? simulator). The journey: **Capture → Understand → Diagnose → Plan → Improve** (section 6). Private and secure by default: your transaction data never leaves the phone. Only redacted facts and general questions go to the cloud, only when that gives a better answer, and the user can switch cloud AI off in the Privacy Center.

**The three goals, in order:**
1. **Privacy and security by default.** The data source is already on the device, and all transaction processing (rules and AI) happens on the device. There is no account and no server holding your transactions. The cloud is used only by FinSight Coach, only for knowledge, and it never receives raw transactions, merchants, names or account numbers.
2. **Hybrid input, automatic first.** Transactions come in every way the user has them:
   - **automatically**, from SMS and from bank, wallet and email app notifications;
   - **uploaded files**, such as bank statements, receipts and payment screenshots;
   - **shared in** from another app;
   - **manual entry**, when the user wants it.

   Every input, manual included, goes through the same pipeline. So even a quick typed line like "lunch 850 card" becomes a complete, categorized record. Automatic capture does most of the work; the user types only when they choose to, and confirms the few entries the app is unsure about.
3. **A hybrid AI finance assistant, not just a tracker.** FinSight Coach covers what FinSight lacked: a **Financial Health Score**, **financial education**, and planning (goals, cash-flow forecast, recurring bills, "can I afford it?"). It is where users **chat**, **generate reports** and **explore analytics**. It runs **hybrid automatically**: the user never picks a mode per question (a single cloud off switch lives in the Privacy Center). For each question, a Privacy Router chooses on-device Gemma, the cloud (Gemini), or both. Personal numbers are always computed on the phone. If the phone is offline, Coach still answers on the device.

---

## 1. The idea in 60 seconds

Everyone with a bank account, card or mobile wallet is notified about each payment, by SMS or by a push notification from the bank or wallet app. Today, tracking that money means typing each transaction into an app by hand, which people give up on, or handing financial data to a cloud service.

FinSight captures those notifications on the phone, **only from the sources the user allows**. It also accepts uploaded statements and receipts, content shared from other apps, and manual entries. Every input runs through the same **Transaction Intelligence Pipeline** and is saved as a record. Because the data is already on the device and never leaves it, privacy is not a feature we added; it is how the app is built.

The pipeline has five stages:

1. **Gate:** is this a real transaction, or an OTP or promo?
2. **Extract:** amount, debit/credit, merchant, account, balance.
3. **Categorize:** Food, Transport, Bills, Transfer and so on.
4. **Score:** how sure are we? Auto-accept, ask the user to review, or ask the user to decide.
5. **Learn:** when the user corrects something, remember it for next time.

Then the user can **ask questions** ("Where did my money go this week?") and get answers computed from their own data, on the phone.

On top of the records sits **FinSight Coach**, the app's AI assistant for chat, reports and analytics. It turns tracking into guidance: a Financial Health Score computed on the device, lessons tied to the user's own habits, and planning tools. When a question needs world knowledge ("How do I build an emergency fund?"), Coach sends the cloud model only redacted facts such as "savings rate 12%, runway 1.4 months". It never sends the transactions themselves. The user never chooses a mode; the router decides for each question and falls back to the device when there is no network.

The AI is the engine of the app, not a chatbot bolted on. Rules and Gemma work together on every transaction, and the app shows *why* it decided what it did.

---

## 2. Pitch: why this, why now, why this way

### Problem
- **Privacy:** financial data is the most sensitive data people have. Cloud finance apps want it on their servers, or want your bank login. Many users will not accept that, and should not have to.
- **Manual effort:** without automatic capture, expense tracking means typing every transaction by hand. People stop within weeks (the PRD cites 78% abandonment).
- **Bank connections are fragile or missing:** "connect your bank" integrations do not exist for most banks and wallets, and break where they do. But every bank and wallet already notifies the user on the phone.
- **Every bank writes differently:** rule-based trackers need a hand-written parser per bank, and break when a bank changes its message format.
- **Tracking is not enough:** knowing where the money went does not tell you whether you are financially healthy, or what to do next. Most trackers stop at charts. People who most need guidance get none, and the AI assistants that could help want all their data in the cloud.

Persona from the PRD: *a 28-year-old developer with two wallets and a debit card*: "I get 20+ transaction SMS daily. I want an app that reads them automatically without sending my data anywhere."

### Solution
Use what the phone already receives (bank SMS, and bank, wallet and email app notifications, from sources the user allows) as the main data source. Add file upload, sharing from other apps and manual entry for everything else. Do all the transaction intelligence on the device, with a deterministic rule engine plus a small Gemma model. Most transactions are captured automatically; the user confirms only the uncertain ones. On top of that, FinSight Coach gives financial health checks, education and planning. It uses on-device Gemma for anything personal and cloud Gemini for knowledge, routed automatically, and the cloud only ever sees redacted facts.

### Any bank, any format, any language
The on-device model is what removes the per-bank limit. Older trackers needed a hand-written parser for every bank, which is why they only ever supported a few. Gemma reads a message format it has never seen, in any currency or language, and the verifier checks its answer against the original text. Our rule profiles are only a **fast path** for formats we have already seen; they are not a list of supported banks. With a multimodal Gemma, the same model can also read payment screenshots and receipts, so no separate OCR engine is needed (roadmap).

### Secure by default: what we can show, not just claim

| Design choice | Why it matters | How to show judges |
|---|---|---|
| **Transaction data cannot reach the network** | The app holds `INTERNET` only for Coach. The only class that talks to the network, `CloudLlm`, accepts only a `SanitizedPrompt`, which only the on-device Redactor can build. An architecture test fails the build if pipeline or data code touches the network. | Show the "What was shared?" sheet and the exact payload sent |
| **Capture works fully offline** | Capture, the pipeline, the Health Score and local Coach answers never need the network | Run capture and the first Coach question in airplane mode |
| **No `READ_SMS` permission** | We never read the SMS inbox; we only see notifications from allowed sources | Show the permission list in Android settings |
| **Notification Listener with an allowlist** | Only messaging apps plus the finance and email apps the user picks are read; everything else is ignored in memory and never stored | Show the Sources screen with toggles |
| **System file picker for uploads** | No storage permission; the app sees only the file the user picks | Upload a statement in the demo |
| **Email without a mailbox login** | Email arrives via the email app's notifications or by sharing an email in. Connecting to a mailbox API would need the user's account, which breaks the promise. | Mention it if asked |
| **On-device Gemma via LiteRT-LM** | Pipeline prompts and transaction text never leave the phone | Run the capture part of the demo in airplane mode |
| **Cloud log** | Every cloud request is stored locally (route, exact payload, model, time). The user can read and clear it. | Open Privacy → Cloud log |
| **App-private storage, `allowBackup="false"`** | No copy goes to cloud backup | Mention it; roadmap: SQLCipher + Keystore and a biometric lock |
| **User control** | Turn any source off, delete any record and its raw text | Show the toggle and delete in the demo |

Two cautions for the build:
- With `INTERNET` in the manifest, the boundary must be enforced in code, not just claimed. Only the `cloud/` package may depend on the network client (Firebase AI Logic or Ktor). Add an architecture test (for example with Konsist) that fails if `pipeline/`, `data/` or `sms/` imports it.
- In the hackathon build the model is copied onto the phone with `adb`. In a store release it must still get onto the phone once: Play Asset Delivery is the likely route, or a one-time download (now possible, since the app holds `INTERNET` for Coach). Say "roadmap" if asked.

### Why now
Until recently, running an LLM on a mid-range Android phone meant hand-building llama.cpp native libraries. Our earlier Flutter version did exactly that, and the plumbing cost days. Google's **LiteRT-LM** (part of Google AI Edge) now gives a production-grade, Kotlin-first runtime for Gemma on Android, with GPU acceleration. On-device AI is finally a normal app dependency.

### Why hybrid (rules + Gemma) in the pipeline, not "just ask the LLM"
This is the core technical argument. Say it clearly in the pitch.

| Rules alone | LLM alone | Hybrid (what we built) |
|---|---|---|
| Fast, exact, explainable | Understands new formats and unknown merchants | Rules handle the known 80% instantly; Gemma handles the long tail |
| Breaks on any new SMS format or merchant | Slow per message, uses battery | Gemma runs only when rules are unsure |
| Cannot generalize | Can hallucinate amounts (unacceptable for money) | **Rules verify Gemma:** an amount is accepted only if it literally appears in the SMS |
| No confidence nuance | Self-reported confidence is unreliable | Confidence comes from agreement between independent sources |

Short version for the stage: **"Gemma proposes, rules verify, the user has the final say, and the app learns from it."**

### Why hybrid (device + cloud) for the assistant
The second meaning of "hybrid" in FinSight. The pipeline stays 100% on the device; the assistant mixes device and cloud.

| On-device only | Cloud only | Hybrid (FinSight Coach) |
|---|---|---|
| Private, offline, free | Broad knowledge, better explanations, many languages | Personal numbers on the device, knowledge from the cloud |
| A 1B-class model knows little about personal finance and explains poorly | Needs all your transactions on someone's server | The cloud sees only redacted facts ("savings rate 12%"), never transactions |
| Answers stay shallow | Breaks with no network | Router picks the path automatically and falls back to the device when offline |
| Cannot check itself either | Can invent figures about you | **Device verifies the cloud:** user figures in the answer must match the facts computed on the phone |

Short version: **"Your data stays on the phone; only knowledge comes from the cloud."**

### Why on-device first (this is the product, not a detail)
- **Privacy and security:** transaction data never leaves the phone. No account, no server holding your transactions, no breach surface.
- **Works offline:** capture, the pipeline, the Health Score and local Coach answers all work with no network, which suits patchy mobile data.
- **Low inference cost:** every transaction is processed for free on the device. Cloud calls happen only for Coach knowledge questions, and they are small, because the prompts are a few redacted facts.
- **Demo proof:** run capture and the first Coach answer in airplane mode, then turn the network on and show exactly what was shared.

### Why KMP, Android first
- Android first because reading notifications from other apps is an Android capability (iOS does not allow it), and Android is the large majority of phones.
- KMP because the valuable part (parser, scoring engine, pipeline, database, view models) is pure Kotlin in `commonMain`. iOS later reuses all of it, with share-sheet or receipt input instead of SMS. Only the AI runtime and SMS capture are platform-specific.

### Why our team can build it in a day
We are not starting from zero. A previous Flutter version gives us:
- 58 real bank and wallet SMS samples with expected outputs;
- bank and wallet profiles (sender IDs and formats) to seed the rule fast path;
- a working Kotlin Notification Listener with boot restart, a watchdog, and OEM battery-killer handling (including Infinix/Transsion);
- a proven category taxonomy and confidence thresholds;
- 27 screen designs;
- labelled categorization data.

The code is written fresh in Kotlin today, so we only reuse data, design and lessons learned. **Check the hackathon rules on prior work in the opening session** and be upfront about this in the pitch.

### Suggested judging angles
- **Use of Google tech:** Gemma on the device, Gemini in the cloud (via Firebase AI Logic), LiteRT-LM, Kotlin Multiplatform, Jetpack Compose. Optionally EmbeddingGemma.
- **Technical depth:** hybrid scoring, LLM output verification, learning from corrections, an agent with tools, a Privacy Router that splits work between device and cloud with a compiler-enforced redaction boundary.
- **Impact:** automatic, private money tracking for anyone with a bank account, card or wallet, plus financial health checks and financial education for people who have never had a financial advisor.
- **Privacy you can verify:** no `READ_SMS`, airplane-mode capture, the "What was shared?" sheet and the Cloud log.
- **Demo:** a live notification becomes a categorized transaction with no typing, with explainable green, yellow and red results. Then Coach gives a health check and shows exactly what it shared.

---

## 3. High-level prototype architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  ANDROID APP (Compose Multiplatform UI, InvesTerra design tokens)    │
│                                                                      │
│  Dashboard   Transactions   Review Inbox   FinSight Coach   Sources  │
│      │            │              │          (Chat, Reports,          │
│      │            │              │           Analytics, Health,      │
│      │            │              │           Learn)                  │
│      │            │              │               │                   │
│      └────────────┴──────┬───────┴───────────────┘                   │
│                    ViewModels (StateFlow)                            │
└──────────────────────────┼───────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────────┐
│  SHARED CORE (commonMain, pure Kotlin, unit-tested)                  │
│                                                                      │
│  ┌────────────── Transaction Intelligence Pipeline ───────────────┐  │
│  │                (100% on-device, never touches the network)     │  │
│  │                                                                │  │
│  │  text ─────► 1.Gate ─► 2.Extract ─► 3.Categorize ─► 4.Score ─► │  │
│  │                 │          │              │             │      │  │
│  │              rules     regex parser   memory + rules  agreement│  │
│  │              + Gemma   + Gemma        + Gemma         + bands  │  │
│  │                        + verifier                              │  │
│  │                                                     5.Learn ◄──│  │
│  └─────────────────────────────────────────── user correction ────┘  │
│                                                                      │
│  ┌──────────── FinSight Coach (hybrid AI finance assistant) ──────┐  │
│  │                                                                │  │
│  │  question ─► Privacy Router ─┬─► LOCAL:  Gemma + SQL tools     │  │
│  │                              ├─► HYBRID: SQL tools ─► Redactor │  │
│  │                              │     ─► Gemini ─► number verifier│  │
│  │                              └─► CLOUD:  general knowledge only│  │
│  │                                  (offline: lesson pack + Gemma)│  │
│  │                                                                │  │
│  │  Analytics engine (SQL)   Report builder   Health Score engine │  │
│  │  Lesson pack (bundled)    Cloud log                            │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  Repositories ─► SQLDelight DB (transactions, accounts, categories,  │
│                  merchant_memory, budgets, goals, reports, coach_log)│
│                                                                      │
│  interface LocalLlm    interface CloudLlm(SanitizedPrompt)           │
│  interface TransactionSource                                         │
└──────────────┬────────────────────────┬──────────────────────────────┘
               │                        │
┌──────────────▼──────────┐  ┌──────────▼───────────────────────────────┐
│ androidMain             │  │ INPUT CHANNELS (on-device, user-allowed) │
│ LiteRtLlm (LiteRT-LM +  │  │ Automatic: NotificationListenerService   │
│ Gemma, GPU, one queue)  │  │   SMS apps + allowed bank/wallet/email   │
│ FakeLlm (for tests)     │  │ Upload: statement CSV, receipt, image    │
│                         │  │   (system file picker)                   │
│ cloud/GeminiCloudLlm    │  │ Share in: text, email, screenshot        │
│ (Firebase AI Logic;     │  │ Manual: quick-add line or full form      │
│  the only network code) │  │ Demo: replay of 58 sample SMS            │
└─────────────────────────┘  └──────────────────────────────────────────┘

   No READ_SMS permission. Transaction data never leaves the phone.
   INTERNET is used only by cloud/GeminiCloudLlm, which accepts only
   redacted SanitizedPrompts and logs every request in the Cloud log.
```

Every channel produces the same thing: `RawInput(channel, sourceApp, sender, text or file, receivedAt)`, where `channel` is `NOTIFICATION`, `FILE`, `SHARE` or `MANUAL`. The pipeline handles them all the same way, with three differences:
- **Manual form** with every field filled in: skip extraction (confidence 1.0, source `You`), but still suggest a category if the user left it empty.
- **Manual quick-add line** ("lunch 850 card"): goes through extraction and categorization like an SMS. The verifier checks Gemma's fields against the typed text.
- **Statement CSV:** Gemma maps the header row to fields once (date, description, amount, debit/credit). Then each row runs through categorization, and duplicates of transactions already captured from notifications are flagged. PDF statements and images need a multimodal model (roadmap).

Key design decisions:
- **The pipeline and scoring live in `commonMain`.** They are testable on the JVM with `FakeLlm`, with no phone needed, so the AI person and the logic person can work in parallel.
- **One `LocalLlm` interface:** `suspend fun complete(prompt: String, maxTokens: Int): String` plus a streaming variant for chat. LiteRT-LM is one implementation.
- **One LLM worker.** The model is loaded once and requests go through a single queue (a `Channel`, or `limitedParallelism(1)`). Never run two inferences at once.
- **Two model interfaces, one router.** `LocalLlm` (Gemma) serves the pipeline and local Coach answers. `CloudLlm` (Gemini) has one method, `suspend fun complete(prompt: SanitizedPrompt): String`. `SanitizedPrompt` has a private constructor, and only the Redactor can build one, so the compiler enforces the privacy boundary. The pipeline never calls `CloudLlm`.
- **Money is `Long` in the currency's smallest unit, plus an ISO currency code.** Never `Double`. This follows InvesTerra ADR-0004.
- **Every transaction stores its provenance:** `parse_source`, `category_source`, `confidence`, `needs_review`, `raw_sms`. That is what makes the UI explainable.

### Module layout

```
composeApp/
  commonMain/
    domain/        Transaction, Account, Category, Money, Confidence, Source enums
    pipeline/      Gate, RegexExtractor, LlmExtractor, ExtractionVerifier,
                   RuleCategorizer, LlmCategorizer, MerchantMemory, Scorer, Pipeline
    coach/         CoachAgent (chat), PrivacyRouter, Redactor, SanitizedPrompt,
                   NumberVerifier, Tools (SQL queries), AnalyticsEngine,
                   ReportBuilder, HealthScore, Lessons, prompts
    data/          SQLDelight .sq files, repositories, BankProfiles loader
    ui/            theme (ported from InvesTerra), screens, view models
    ai/            LocalLlm and CloudLlm interfaces, FakeLlm, FakeCloudLlm
  androidMain/
    ai/LiteRtLlm.kt
    cloud/GeminiCloudLlm.kt   (the only code allowed to use the network)
    report/PdfExporter.kt     (android.graphics.pdf.PdfDocument + share sheet)
    sms/NotificationCapture.kt, DemoReplaySource.kt
    di/ (Koin Android module), SQLite driver, ConnectivityMonitor
  composeResources/ bank_profiles/*.json, sms_test_templates.json,
                    lessons/*.json, fonts
```

Stack: Compose Multiplatform + Material3, Navigation Compose, SQLDelight, Koin, kotlinx.serialization (for LLM JSON output), kotlinx.coroutines. For Coach: Firebase AI Logic (Gemini) in `androidMain/cloud` only, and a chart library such as Vico, or simple Compose Canvas charts.

---

## 4. The Transaction Intelligence Pipeline in detail

This is the heart of the product. It is based on the old Flutter engine (`finsight/lib/ml/categorization/categorization_orchestrator.dart`, `rule_based_categorizer.dart`, `llm_categorizer.dart`, `sms_parser.dart`), simplified for one day.

### Stage 1: Gate (is it a transaction?)

First, the **source check**, done before anything is stored: the notification must come from an SMS app or from a finance app the user enabled on the Sources screen. Anything else is dropped immediately and never written anywhere. The old `BankNotificationService.kt` already has the list of SMS app packages for each phone brand. For wallet and bank apps, find the real package names on the phone with `adb shell pm list packages | grep -i <app name>`; do not guess them. In the product, the Sources screen lists installed apps and the user picks.

Then the content checks:

| Check | Result |
|---|---|
| Contains OTP, "code", "password", promo words ("offer", "% off", "win") and no completed-payment verb | Drop, no LLM call |
| Sender matches a bank profile and the text has an amount pattern | Transaction, go to Extract |
| Has an amount, but the sender or format is unknown | Ask Gemma (the `is_transaction` field in the extraction JSON) |
| No amount at all | Drop |

### Stage 2: Extract (fields)

1. **Regex extractor:** ported from `sms_parser.dart` and driven by `bank_profiles/*.json`. It computes a parse confidence using the old weights: amount 0.40, merchant 0.20, date 0.15, type 0.15, balance 0.10.
2. If parse confidence is **≥ 0.75**, accept it with no LLM call. This is the fast path for formats we have seen before. Any other format goes to Gemma, which is why the app is not limited to the banks in the profiles.
3. Otherwise **Gemma extraction**: return JSON only (prompt below).
4. **Verifier:** this is the anti-hallucination step, and it is a strong pitch point.
   - The amount must appear in the SMS text after removing thousands separators and currency symbols or codes.
   - The type must agree with keyword evidence ("sent", "paid", "debited" mean debit; "received", "credited" mean credit).
   - The merchant must be a (fuzzy) substring of the SMS.
   - The account's last 4 digits must appear in the SMS.
   - A field that fails verification is dropped. A dropped amount means the transaction goes to Review.
5. **Field merge:** take the regex field when it exists; fill the gaps from verified Gemma fields. Record `parse_source` as `REGEX`, `LLM` or `MERGED`.

### Stage 3: Categorize (candidate sources)

| Source | When | Confidence it contributes |
|---|---|---|
| **Merchant memory** (user corrected this merchant before) | Normalized merchant key matches | 0.98, and it short-circuits everything else |
| **Rules:** exact merchant from the merchant list | Match | 0.95 |
| **Rules:** keyword map (`category_mapping.json`) | Match | 0.75 to 0.85 |
| **Rules:** transfer heuristics ("sent to <person name>", wallet to wallet) | Match | 0.80 |
| **Gemma** (constrained to the category list, with few-shot examples) | Rules < 0.90, or no rule hit | 0.80 if the output is a valid category, 0.60 if it needed repair |
| *Stretch:* EmbeddingGemma nearest neighbours over labelled examples | If time | similarity score |

**Fast path:** if memory hits, or rules give ≥ 0.90, skip Gemma. That saves battery and makes most transactions instant.

### Stage 4: Score (combine and band)

Adapted from the old `_ensembleCombine`, which used rule weight 0.4, KNN weight 0.6 and an agreement bonus of 0.15:

```
if memory hit:               final = memory (0.98), source = USER_MEMORY
else if rule >= 0.90:        final = rule, source = RULE
else run Gemma:
   both agree:               final = min(1.0, max(rule, gemma) + 0.15), source = HYBRID
   disagree:                 final = higher one, capped at 0.85, needs_review = true,
                             keep the other as a "suggestion" chip
   only one has an answer:   final = that one, source = RULE or LLM
   none:                     category = Uncategorized, confidence = 0
```

Bands, from the PRD decision (section 8.2):

| Confidence | Action | UI |
|---|---|---|
| ≥ 0.90 | Auto-accept | Green check |
| 0.75 to 0.89 | Accepted, flagged for review | Yellow |
| < 0.75 | Goes to the Review Inbox, user decides | Red question mark |

Every transaction row shows a small **source badge**: `Rule`, `Gemma`, `Rule+Gemma`, or `You`. Tapping it opens a "Why?" sheet listing the matched rule, Gemma's answer, the confidence and the verifier checks. Explainability is a real differentiator in finance; make it visible.

### Stage 5: Learn (no retraining)

- When the user corrects a category, write `merchant_memory(merchant_key → category)`. The next transaction from that merchant is categorized instantly with confidence 0.98.
- Recent corrections are added as **dynamic few-shot examples** in Gemma's prompt, so the model adapts to this user with no training. Pitch line: *"It learns your habits without your data ever leaving the phone."*

### Batch mode (the "process my inbox" moment)

On first launch, "Import" runs the backlog through the same pipeline with a progress bar: the 58 demo SMS, or real notifications. Rules clear most of them instantly; Gemma handles the rest one by one through the single queue. Show the live count: *"52 by rules, 6 by Gemma, 3 need your review."*

### Prompts (starting point, tune on device)

LiteRT-LM's conversation API applies Gemma's chat template, so send plain text. Keep prompts short: prefill time dominates on phones.

**Extraction:**
```
Extract the transaction from this bank SMS. Reply with JSON only, no prose.
Schema: {"is_transaction": bool, "amount": number|null, "currency": ISO 4217 code|null, 
"type": "debit"|"credit"|null, "merchant": string|null, "account_last4": string|null,
"balance": number|null}
Use null when a field is not in the SMS. Do not guess.
SMS: <text>
```

**Categorization:**
```
Pick exactly one category id for this transaction. Reply with the id only.
Categories: food, transport, shopping, bills, health, entertainment, education,
housing, transfer, income, financial, other
Examples:
<3-8 lines "merchant | type | amount -> category", recent user corrections first,
 then examples from unified_transactions.csv>
Transaction: <merchant> | <type> | <amount>
```

Parse defensively: trim the output, take the first token or JSON object, and validate it against the allowed list. If it is invalid, retry once with "Reply with one id from the list", then fall back to Uncategorized.

### Insights Agent ("Ask FinSight")

> This agent is now the on-device core of **FinSight Coach** (section 5). Coach adds the chat, reports, analytics, Health Score, Learn and the hybrid device + cloud routing on top of it. The tools below remain the single source of truth for every number.

This is not free-form chat over raw data. It is an agent with a few tools, which matches the workshop's "AI agent" theme and keeps the numbers correct:

1. **Router:** Gemma maps the question to one tool and its arguments, as JSON. For example, `{"tool":"spend_by_category","period":"this_week"}`.
2. **Tools:** fixed SQL queries.
   - `spend_by_category(period)`
   - `top_merchants(period, n)`
   - `compare_periods(a, b)`
   - `find_transactions(merchant|category, period)`
   - `budget_status()`
3. **Answer:** Gemma writes 2 or 3 sentences using only the numbers the tool returned, and the UI shows the numbers as a mini chart next to the text.
4. **Guardrail:** if the router output is not a valid tool, reply with suggested questions instead of guessing.

*Stretch:* semantic search ("that coffee place near work") with EmbeddingGemma over merchant names. This is the RAG pattern from the workshop.

---

## 5. FinSight Coach: the hybrid AI finance assistant

FinSight Coach is the AI assistant inside the app. It is one place where the user can **chat**, **generate reports** and **explore analytics** about their money. On top of that it adds what FinSight lacked: **financial health**, **financial education** and **planning**. It is built on the Insights Agent from section 4, and runs **hybrid by design**: on-device Gemma and cloud Gemini work together, and a router picks the path for each request.

### 5.1 What Coach does

| Capability | What the user gets | Where it runs |
|---|---|---|
| **Chat** | Ask anything in plain language (English, Urdu or Roman Urdu): "Where did my money go this week?", "Am I saving enough?", "What is an emergency fund?" Answers come with charts and source badges. | Router decides: device, cloud or both |
| **Reports** | A weekly or monthly financial report on demand ("make my October report") or on schedule. It covers income, spend, savings rate, top categories, biggest changes against the last period, recurring bills, Health Score trend, a short written summary and 3 next steps. Saved in the app; exported as PDF through the share sheet only when the user chooses. | Numbers and charts on the device; summary written by Gemma (device) or Gemini (redacted facts) |
| **Analytics** | Spending trends over time, category breakdown, month against month, top merchants, income against spend, day-of-week patterns, unusual spend against your own baseline, cash-flow forecast to month end. Available as an Analytics screen and as answers in chat. | Always on the device (SQL) |
| **Financial health** | A 0 to 100 Financial Health Score with sub-scores and what to fix first (5.5) | Score on the device; deeper advice hybrid |
| **Financial education** | Short lessons, nudges based on your own data, quizzes, and answers to any general money question (5.6) | Bundled lessons offline; cloud for open questions |
| **Planning** | Savings goals with what-if ("save 5,000 a month: when do I reach 100,000?"), recurring bill and subscription detection, "Can I afford it?" | Math on the device; coaching hybrid |

### 5.2 What FinSight lacked, and what Coach adds

| Gap in FinSight | Coach capability | Runs | Priority |
|---|---|---|---|
| Records money but never says whether you are OK | Financial Health Score and a monthly check-up | Device score, hybrid explanation | P1 |
| Q&A only; no reports or analytics | Report generator and Analytics screen, both reachable from chat | Device | P1 (reports), P2 (full Analytics screen) |
| Cannot teach finance concepts | Learn: lessons, personalized nudges, open questions | Bundled offline + cloud | P1 (open questions via router), P2 (lesson pack) |
| Looks backward only | Cash-flow forecast: projected month-end balance | Device | P2 |
| No planning | Savings goals with what-if | Device math, hybrid coaching | P2 |
| Recurring costs stay hidden | Subscription and recurring bill detection, upcoming bill reminders | Device | P2 |
| Surprises found too late | Unusual-spend alerts against the user's own baseline | Device | P2 |
| No decision support | "Can I afford it?" check against the forecast and goals | Hybrid | P2 |
| Debt is invisible | Debt payoff planner (avalanche and snowball compared) | Device math, hybrid explanation | P3 |
| No local context | Zakat calculator (the user enters the current nisab from an official source), Urdu and Roman Urdu answers | Device calc; cloud language | P3 |
| Phishing SMS look like bank SMS | Scam warning: the Gate already sees patterns such as a link plus "your account is blocked"; warn instead of silently dropping | Device | P3 |

### 5.3 Hybrid by design: automatic routing, no mode switch

Coach does **not** ask the user to pick offline or online. For each request, the **Privacy Router** looks at what the request needs and what is available, then picks a route.

| Route | When | What happens | Badge |
|---|---|---|---|
| **LOCAL** | The request is about the user's own data only (spend, merchants, lists, comparisons, analytics), or it names a merchant, person or account that cannot be redacted | Gemma picks a tool, SQL runs, Gemma writes the answer. Nothing leaves the phone. | On-device |
| **HYBRID** | Personal data **and** knowledge are both needed ("How healthy are my finances and what should I fix?", "Write my monthly report") | SQL tools compute facts on the device → Redactor turns them into a `SanitizedPrompt` → Gemini explains → NumberVerifier checks the answer against the facts | Hybrid |
| **CLOUD** | General knowledge only, no personal data ("What is compound interest?") | The scrubbed question goes to Gemini | Cloud |
| **Fallback** | No network, the request times out (about 6 s), or the cloud returns an error | The same request runs LOCAL: Gemma plus the bundled lesson pack. The answer is marked "Answered on-device". | On-device |

How the router decides (rules first, Gemma second, like the pipeline):
0. **Cloud switch:** if the user turned cloud AI off in the Privacy Center, every request is LOCAL.
1. **Sensitivity check (rules):** if the question contains a known merchant key, a person name from transfers, 4 or more consecutive digits, or a phone number, it is LOCAL only.
2. **Intent (Gemma):** the router JSON from section 4 gains one field: `{"tool": "...", "needs_knowledge": true|false}`. No tool and `needs_knowledge` gives CLOUD. A tool without knowledge gives LOCAL. A tool with knowledge gives HYBRID.
3. **Availability:** `ConnectivityMonitor` (Android `NetworkCallback`) says whether the cloud is reachable. If it is not, HYBRID and CLOUD fall back to LOCAL automatically.

Every answer shows its badge. Tapping it opens **"What was shared?"**: the exact payload sent (or "nothing left this phone"), the model, and the time.

### 5.4 The privacy boundary

**What may leave the phone** (allowlist, not blocklist): the scrubbed question; category names; percentages; amounts **rounded** (for example to the nearest 1,000) or bucketed; period labels ("last 30 days", "October"); counts; month-based ratios such as runway; the currency code.

**What never leaves:** raw SMS or notification text, merchant names, person names, account or card digits, phone numbers, exact dates, exact balances, file contents and the transaction list.

How it is enforced:
- `SafeFact` is a sealed type (`Percentage`, `RoundedAmount`, `CategoryShare`, `Count`, `MonthsRatio`, `PeriodLabel`). The Redactor builds a `SanitizedPrompt` from a scrubbed question and a list of `SafeFact`s. `CloudLlm` accepts nothing else. **The compiler enforces the privacy promise.**
- **NumberVerifier ("Gemini proposes, the device verifies"):** every amount or percentage about the user in the cloud answer must match a fact we sent. A sentence that fails the check is dropped. The charts and cards on screen always come from local SQL, never from the cloud text.
- **Cloud log:** every cloud call is written to `coach_log` (route, payload, model, latency) on the device, and shown in Privacy → Cloud log. The user can clear it.
- The architecture test from section 2 keeps network code out of `pipeline/`, `data/` and `sms/`.

Example payload for "How healthy are my finances?":
```json
{"question": "How healthy are my finances and what should I fix first?",
 "facts": {"period": "last 90 days", "savings_rate": "12%", "essentials_share": "64%",
           "runway_months": 1.4, "debt_payments_share": "18%", "health_score": 58,
           "top_categories": ["food 28%", "transport 14%", "bills 12%"]}}
```

### 5.5 Financial Health Score (on-device, deterministic)

> **Superseded by Health 2.0 (section 6.5):** profile-aware thresholds, missing data is not poor health, data sufficiency and coverage shown. The table below is the starting formula.

The score is plain arithmetic in `commonMain`, unit-tested on the JVM. No model computes it; the models only explain it. It needs at least 30 days of data and uses the last 90.

| Component | Measure | Full marks at (starting point, tune) | Weight |
|---|---|---|---|
| Savings rate | (income − spend) / income | ≥ 20% | 25 |
| Emergency runway | latest known balance / average monthly essential spend | ≥ 3 months | 20 |
| Debt load | loan, EMI and card payments / income | ≤ 10% (zero marks at ≥ 40%) | 15 |
| Essentials share | essentials (bills, housing, groceries, transport, health) / total spend | 50 to 70% | 15 |
| Spending stability | variation in weekly spend | low variation | 15 |
| Budget adherence | categories within budget | all within budget | 10 |

Each component scores 0 to 1 on a linear scale between its zero point and its full-marks point. Components with no data (for example no budgets set, or no balance seen in SMS) are left out, and the weights are spread over the rest. Coach says so ("based on 5 of 6 checks"). The bands reuse the confidence colours: **80+ Healthy** (green), **60 to 79 Fair** (yellow), **under 60 Needs attention** (red). Each sub-score has a "Why?" that shows the inputs, the same as the transactions do.

### 5.6 Financial education (Learn)

- **Lesson pack, bundled in `composeResources/lessons/*.json`:** 10 to 15 two-minute lessons (budgeting, the 50/30/20 rule, emergency fund, debt, compound interest, inflation, saving options, Zakat basics, scams) with a 3-question quiz each. They work fully offline.
- **Nudges from the user's own data:** when food delivery is 30% of spend, show the budgeting lesson; when runway is under 1 month, show the emergency fund lesson. Triggers are rules on the device.
- **Open questions:** anything outside the pack goes CLOUD (online) or to Gemma plus the closest lesson (offline).
- **Rates and rules change:** never hard-code tax rates, savings scheme rates or the Zakat nisab. Coach explains the concept and points to the official source (for example FBR, SBP or National Savings), and says the figure must be checked there.

### 5.7 Coach tools (extending section 4)

All tools are fixed SQL or Kotlin functions on the device. They are the only source of numbers.
- Existing: `spend_by_category`, `top_merchants`, `compare_periods`, `find_transactions`, `budget_status`.
- New: `health_score()`, `trend(category|all, period, granularity)`, `income_vs_spend(period)`, `recurring_payments()`, `unusual_spend(period)`, `cashflow_forecast(to_date)`, `goal_progress(goal)`, `affordability(amount, by_date)`, `build_report(period)`.

`build_report` runs a fixed set of tools, renders the charts, then asks the model for the summary and next steps over the redacted facts. The report is saved in `reports` and can be exported as a PDF with `android.graphics.pdf.PdfDocument` and the share sheet.

### 5.8 Cloud prompt (starting point)

```
You are FinSight Coach, a financial-education assistant inside a budgeting app.
You get a question and a few facts about the user, computed on their phone
(percentages and rounded amounts only). For anything about the user, use only
these facts; never invent figures. Answer in plain language in at most 120 words,
then give up to 3 concrete next steps. This is education, not regulated financial
advice: do not recommend specific stocks, funds or financial products. For tax,
Zakat or savings-scheme rates, say that they change and name the official source.
Reply in the language of the question.
Facts: <json>
Question: <scrubbed question>
```

### 5.9 Guardrails

- **Numbers come from the device:** tools compute them, the NumberVerifier checks cloud text, and charts never come from model output.
- **Education, not advice:** a one-line disclaimer under Coach answers and reports. No product recommendations.
- **Fail safe:** if the cloud is down or slow, the user still gets an on-device answer. Coach never shows an error where an answer is possible.
- **Q&A prep:** *"Didn't you say nothing leaves the phone?"* → "Your transactions never do. The capture pipeline has no network code, and an architecture test proves it. Coach sends only redacted facts like 'savings rate 12%', and you can see every payload in the Cloud log."

---

## 6. v2 enhancements: from tracker to trusted financial companion

These changes come from the team review. The rule: **one complete, trusted experience, not more AI features.** Each item is placed in the scope (section 7).

### 6.1 One product journey

**Capture → Understand → Diagnose → Plan → Improve**

| Step | What it means | Where it lives |
|---|---|---|
| Capture | Allowed notifications, statements, share-in, manual entry | Input channels (section 3) |
| Understand | Extract, categorize, verify, find duplicates and uncertain records | Pipeline (section 4) + Integrity Layer (6.2) |
| Diagnose | Spending patterns, savings, debt, Health Score, data coverage | Coach analytics + Health 2.0 (6.5) |
| Plan | Goals, cash-flow forecast, What If? simulator | Simulator (6.7), Goals screen |
| Improve | Proactive insights, track whether habits improve | FinSight Pulse (6.6) |

**Navigation (5 tabs):** Home (spend, income, Health Score, data coverage, top Pulse card) · Transactions (records, confidence, Review Inbox, "Why?") · Coach (chat, Pulse, reports, What If?) · Goals (budgets, savings goals, recurring payments) · Privacy (Privacy Center, 6.4).

### 6.2 Transaction Integrity Layer

A notification is not a complete or unique financial record. One payment can send two notifications, a pending payment can be reversed, a refund looks like income, and a transfer between the user's own accounts looks like spending. The Integrity Layer runs **after Extract and before Categorize**.

Lifecycle of a record:
1. **Detected:** store source and original evidence locally.
2. **Duplicate and status check:** repeat event, pending, reversal or refund?
3. **Verification:** amount, direction, currency, merchant, balance evidence (the existing verifier).
4. **Financial classification:** `INCOME`, `EXPENSE`, `REFUND`, `TRANSFER_OWN`, `TRANSFER_OUT`, `FEE`, `ADJUSTMENT`.
5. **Reconciliation:** link related records (payment ↔ refund, debit ↔ credit of a self-transfer), flag unresolved conflicts.
6. **Final:** auto-accept, or send to the Review Inbox.

**Fingerprint:** `hash(normalized merchant, amount_minor, account_last4, reference_id if present, 10-minute time bucket)`. Same fingerprint from two sources = one record with two pieces of evidence, not two transactions. Rules first (keywords such as "reversed", "refund", "pending", reference IDs); Gemma only when unsure.

New columns: `fingerprint`, `txn_kind`, `status` (`PENDING|POSTED|REVERSED`), `linked_txn_id`, `evidence_count`.

**Data coverage indicator:** shown next to every analytic and the Health Score. It estimates how much of the user's money FinSight actually sees: sources enabled vs installed finance apps, days with no captured activity, and known balance changes that no captured transaction explains. Show "Partial data, about 84%" with the reason ("cash purchases and apps without notifications may be missing"). The figure must be computed, never invented. **FinSight never says your finances are healthy when it has seen only part of them.**

### 6.3 Pakistan-first

Pakistan's retail payments are now mostly digital (teammate cites the SBP FY2024-25 review: over 88% of retail payment transactions on digital channels; check the source before quoting it on stage).

| Feature | Why | Where |
|---|---|---|
| PKR-first accounting and display | Correct currency, lakh/crore-friendly formatting | Core, P0 |
| Raast transfers | Recognize incoming and outgoing Raast transfers in bank SMS | Rules in the pipeline, P1 |
| Wallet intelligence | Easypaisa, JazzCash and bank wallet notifications where accessible (real package names from the phone) | Bank profiles, P1 |
| Urdu and Roman Urdu | Natural chat ("Is mahine khana pe kitna kharch hua?") | Coach, P1 |
| Utility bill tracking | Electricity, gas, internet, mobile as recurring bills | Recurring detection, P1/P2 |
| Committee tracking | Monthly contributions, missed payments, payout forecast ("Meri committee mein kitni payments baqi hain?") | Optional Goals module, P2 |
| Household budgeting | Shared and family expenses | P2 |
| Zakat planning | Locally relevant planning; user enters nisab from an official source | P2 |

### 6.4 Privacy Center

Privacy becomes something users can inspect and control, not just an architecture claim.

| Feature | What it does | Hackathon |
|---|---|---|
| Data access dashboard | Exactly which sources FinSight reads | Minimal |
| Per-app sources | Enable or disable each bank, wallet or messaging app | Minimal (Sources screen) |
| Cloud switch | Hybrid is automatic by default, but the user can turn cloud AI off entirely. Coach then runs on-device only (Gemma + lesson pack). | Build |
| Payload preview | Exactly what will leave or did leave the device ("What was shared?") | Build |
| Local deletion | Delete one transaction, its raw text, or all data | Build |
| Privacy audit | Local processing, cloud requests, errors (Cloud log) | Minimal |
| App lock | Optional PIN or biometric | Roadmap (high) |

Also:
- **Prominent disclosure before notification access.** Google Play requires prominent disclosure and affirmative consent before accessing sensitive data. Onboarding explains what is read and why, then sends the user to Android settings.
- **Encrypt local data:** SQLCipher + Android Keystore moves up to the first production milestone.
- **Third-party handling:** Firebase AI Logic does not store requests itself, but the Gemini API provider's retention policy applies, and AI monitoring can log prompts and responses to Cloud Logging. Turn prompt/response logging off, review retention, keep payloads minimal.
- **App Check:** the teammate reports Firebase now requires App Check enforcement for AI Logic (since 2 Nov 2026). Set it up today; check the Firebase docs.

### 6.5 Financial Health 2.0

Replaces the one-size formula in 5.5:
1. **Profile-aware:** the user picks Salaried, Freelancer, Student, Self-employed or Household manager. Thresholds change with the profile. A freelancer is not penalized for irregular income; a student with no own income is not scored as "poor".
2. **Missing ≠ poor:** no known balance means "Emergency savings: unknown", not zero. The score shows its data sufficiency ("based on 4 of 6 checks, coverage 84%") and refuses to score with too little data.
3. **Explained, not just a number:** score + trend ("improving") + what is good + what needs attention + 3 next actions.
4. **Assumptions are labelled:** the 50 to 70% essentials band and "lower variation is better" are starting points, not truths; tune per profile and validate.

### 6.6 FinSight Pulse (proactive insights)

Coach stops being only reactive. Pulse finds patterns **on the device** and shows cards:
- "Food spending is up 22% against last month."
- "Your internet bill recurs around the 12th."
- "Saving PKR 2,000 more a month gets your goal 3 months closer."

Other detectors: unusual transactions against personal history, recurring subscriptions, high fees, possible duplicates, budget suggestions, goals falling behind.

Every card shows **why** it appeared (the numbers and rule), and can be dismissed or acted on. Pulse never makes a financial decision on its own. Hackathon: build 1 or 2 detectors.

### 6.7 What If? simulator

A real planning tool, in chat and as a screen:
- "Can I afford a new laptop for PKR 150,000?"
- "What if I save PKR 5,000 every month?"
- "What if my income drops 20%?"
- "Can I pay the electricity bill and still hit my savings target?"

It uses existing transactions, known balances, upcoming recurring bills, goals and an emergency reserve, not just three typed numbers. Output: monthly surplus, months to target, the effect on goals and runway, and its assumptions in plain words. Pure Kotlin math on the device; the model only explains. Hackathon: one affordability scenario.

### 6.8 AI engineering upgrades

| Current | Upgrade |
|---|---|
| Regex + Gemma extraction | Strict structured output + schema validation; try LiteRT-LM **constrained decoding** |
| Gemma router writes JSON | Try LiteRT-LM **function calling** for the Coach tool router |
| Merchant memory | User-editable, reversible merchant rules (Settings → Rules) |
| Rule/Gemma agreement = confidence | Measure accuracy on labelled data; confidence is a heuristic until calibrated |
| Cloud number check | Verify every personal numeric claim against local facts |
| Notification capture | Plus dedup, refunds, reversals, status (6.2) |

- **Adversarial tests:** SMS are untrusted input. Test malformed text, scam messages, embedded instructions ("ignore previous instructions…"), contradictory amounts, odd separators, unfamiliar currencies. Extraction returns only a validated structure and never follows instructions inside a message.
- **Evaluation dashboard (judges love numbers):** amount accuracy, debit/credit accuracy, category precision and recall, duplicate detection accuracy, % sent to review, average on-device time. Run it on a held-out set of formats Gemma has not seen in the prompt, not only the 58 samples.
- **Hardware reality:** do not assume one model fits every phone. The teammate notes LiteRT-LM lists Gemma 4 E2B at about 2.6 GB and E4B at about 3.7 GB. Measure load time, peak RAM, latency, battery and heat on the demo phone before choosing.
- **Capture fallback:** NotificationListenerService has limits (some low-RAM devices, work profiles). Statement import and share-in are the reliable fallback, not an extra.

### 6.9 Honest claims (use this wording on stage)

| Old claim | Say instead |
|---|---|
| "Records every transaction automatically" | "Captures transactions from the sources you enable, and shows how complete its picture is" |
| "Any bank, any format, any language" | "Handles formats it has never seen; here are our measured results on unseen formats" |
| "Private and secure by default" | Show the full data flow, the permissions, the Privacy Center, deletion and the cloud switch |
| "Confidence means correct" | "Confidence is a signal; uncertain records go to you for review" |

**New pitch:** *FinSight is a privacy-first AI financial companion. It turns everyday transaction notifications and financial records into a verified picture of your money. Powered by on-device Gemma and a hybrid AI coach, it helps you understand spending, check your financial health and plan smarter decisions, without handing your transaction history to the cloud.*

---

## 7. One-day scope

Principle: one complete, reliable story beats many half-features. Capture, the pipeline and local Coach answers must work in airplane mode; the cloud only adds to them.

### P0: must ship
1. KMP app runs on the phone with the InvesTerra dark theme. Network code lives only in `cloud/`, and the architecture test passes.
2. SQLDelight schema with provenance columns: `transactions` (amount_minor, currency, type, merchant, merchant_key, category_id, confidence, category_source, parse_source, channel, source_app, needs_review, raw_text, created_at), `categories`, `accounts` (minimal), `merchant_memory`, `allowed_sources`.
3. **Automatic capture:** NotificationListenerService for SMS apps, ported from the old `BankNotificationService.kt` (it is already Kotlin). Plus **Demo Replay** (the 58 templates) as the reliable demo input.
4. **Manual entry:** a simple Add Transaction form (amount, type, merchant, category, date).
5. Pipeline stages 1, 2 and 4 with **rules only**, plus parser unit tests against `sms_test_templates.json`.
6. Dashboard (month spent and income, category breakdown, recent transactions) and a transaction list with source and channel badges and confidence colours. PKR-first formatting.
7. **Integrity minimum (6.2):** fingerprint duplicate check, refund/reversal keywords, `txn_kind`, and the **data coverage indicator** on Home.
8. **Notification-access onboarding** with prominent disclosure before sending the user to Android settings.

### P1: the AI core (this is the differentiator, so do not let it slip)
1. `LiteRtLlm` loads Gemma and returns completions on the phone.
2. Gemma categorization with hybrid scoring (stage 3 and 4 fully).
3. Gemma extraction with the verifier for unknown formats (stage 2, step 3 to 5).
4. **Quick add:** one typed line ("lunch 850 card") through the same pipeline. Cheap once 2 and 3 work, and a good demo moment.
5. Review Inbox: yellow and red items, one-tap correct, writes merchant memory (stage 5).
6. "Why?" explanation sheet.
7. **FinSight Coach chat** with the Privacy Router: the LOCAL route (Gemma + 3 tools: spend by category, top merchants, compare periods), the HYBRID and CLOUD routes through `GeminiCloudLlm`, automatic fallback to the device when offline, route badges and the "What was shared?" sheet.
8. **Financial Health Score** card (on-device formula with unit tests) and a hybrid "what should I fix first?" answer.
9. **Monthly report** from chat ("make my October report"): numbers and charts on the device, summary from the router, saved in the app.
10. **Minimal Privacy Center (6.4):** sources list, cloud off switch, payload preview, delete data.
11. **Health 2.0 (6.5):** profile picker, "unknown" instead of zero, data sufficiency.
12. **FinSight Pulse (6.6):** 1 or 2 detectors (category up vs last month, recurring bill).
13. **What If? (6.7):** one affordability scenario.
14. **Urdu / Roman Urdu** chat, Raast and Easypaisa/JazzCash recognition in rules.
15. **Evaluation screen (6.8):** accuracy numbers on a held-out set, for the pitch.

### P2: if ahead
1. **Sources screen:** toggles for SMS and for each installed bank, wallet and email app (allowlist stored in `allowed_sources`). Email notifications then work through the same listener.
2. **File upload:** statement CSV through the system file picker, with Gemma mapping the columns and duplicate flagging against captured transactions.
3. **Share in:** share text, an email or a payment screenshot into FinSight (text first; images need a multimodal model).
4. **Analytics screen:** trends, income against spend, month against month, unusual spend, cash-flow forecast, plus the matching Coach tools.
5. **Learn:** bundled lesson pack with quizzes and data-based nudges.
6. **Planning:** savings goals with what-if, recurring bill detection, "Can I afford it?".
7. Report PDF export through the share sheet, and the Privacy → Cloud log screen.
8. Batch import progress screen with the rule/Gemma/review counts.
9. Budget with a progress bar.
10. Committee tracking, household budgeting, Zakat planning (6.3); full reconciliation of self-transfers; user-editable merchant rules; constrained decoding and function calling in LiteRT-LM.

### P3: only if everything above is solid
- EmbeddingGemma semantic search, receipt photo extraction with a multimodal Gemma (test that the chosen model supports image input first), voice questions via on-device speech recognition.
- Coach extras: debt payoff planner, Zakat calculator, scam SMS warning, scheduled weekly reports.
- Agora voice ("talk to your money") only if sponsors push for it. Voice audio is personal data, so it would need its own privacy story beyond the redacted-facts boundary.

### Explicitly cut (say "roadmap" in the pitch)
PIN and biometrics and SQLCipher encryption (first production milestone, not hackathon), receipt and screenshot reading (multimodal Gemma, not a separate OCR engine), full self-transfer reconciliation beyond the 6.2 minimum, unknown-account merging, raw data export (report PDF export stays in P2), the iOS build.

---

## 8. AI setup: LiteRT-LM on device, Gemini in the cloud

Why it is better than our old setup: no hand-built `.so` files (the Flutter version needed `jniLibs` and patched Dart bindings in `finsight/scripts/llama_patches/`), a Kotlin API, GPU support, and Google maintenance.

Before 9:00 AM or in the first hour:
1. Open the LiteRT-LM GitHub README and its Android sample. **Confirm the current Maven coordinates and Kotlin API names.** I have not verified them, so treat any snippet here as shape only.
2. Download a Gemma model in `.litertlm` format from the `litert-community` org on Hugging Face. A 1B-class model is the safe choice for per-transaction calls; a Gemma 3n E2B-class model is bigger but more capable. You may need to accept the Gemma licence on Hugging Face first. Do this on your own connection; venue Wi-Fi will be loaded.
3. `adb push <model>.litertlm /sdcard/Android/data/<package>/files/` and load it from there. Never bundle it in the APK. Use `adb install -r` so the model folder survives reinstalls.
4. Measure on the real phone: load time, and seconds per categorization prompt. If one call takes more than about 3 seconds, use the smaller model and shorter prompts.

Wrapper shape (verify names against the README):
```kotlin
// androidMain
class LiteRtLlm(private val modelPath: String) : LocalLlm {
    // Engine/EngineConfig/Conversation are LiteRT-LM concepts; confirm exact API.
    // Load once (lazy), prefer GPU backend, fall back to CPU.
    // complete(): one-shot request for pipeline calls; low temperature.
    // stream(): Flow<String> for on-device Coach chat answers.
    // All calls go through one serial queue.
}
```

Reference code: `AgoraIO-Conversational-AI/agent-quickstart-android`, branch `codex/on-device-rag-voice-demo`. It has Gemma + EmbeddingGemma + retrieval in Kotlin and Compose, which is good for borrowing model-loading code and RAG patterns.

Do **not** use the old GGUF files (`finsight/models/`, `backend/local_models/`). LiteRT-LM cannot load GGUF.

### Cloud model setup: Gemini via Firebase AI Logic

Used only by FinSight Coach's HYBRID and CLOUD routes.
1. Create a Firebase project, register the Android app, and add `google-services.json`. Enable **Firebase AI Logic** with the Gemini Developer API (it has a free tier, which is enough for a demo).
2. Add the Firebase AI Logic Android dependency. **Confirm the current artifact name and Kotlin API in the Firebase docs**; treat the snippet below as shape only.
3. Pick a current **Gemini Flash-class** model from the model list in the Firebase console. Fast and cheap matters more than size here, because prompts are only a few redacted facts.
4. **Never put an API key in the APK or in git.** Firebase AI Logic avoids shipping a raw Gemini key. Set up App Check now: the team review reports it is enforced for Firebase AI Logic since 2 Nov 2026 (verify in the Firebase docs). Turn off AI monitoring prompt/response logging.
5. Test one call on your own connection before the event, and measure latency. The router timeout (about 6 s) must be longer than a normal call.

```kotlin
// androidMain/cloud: the only file allowed to talk to the network
class GeminiCloudLlm(/* Firebase AI Logic model handle */) : CloudLlm {
    // complete(prompt: SanitizedPrompt): String
    // Sends prompt.render() only; writes route, payload, model and latency to coach_log.
    // Timeout -> throw CloudUnavailable, so the router falls back to LocalLlm.
}
```

---

## 9. What to reuse from this repo

| Need | Where | How |
|---|---|---|
| Pitch facts, persona, thresholds | `PRD_FinSight_Complete.md` (sections 1, 2.1, 8.2, appendix A taxonomy) | Pitch wording, confidence bands, categories |
| Screen designs | `finsight_designs_v2/screens/*.png`: 09 dashboard, 10 list, 11 detail, 21 chatbot, 22 SMS import | Visual reference |
| Old hybrid engine | `finsight/lib/ml/categorization/categorization_orchestrator.dart` (thresholds, ensemble), `rule_based_categorizer.dart` (962 lines of rules), `llm_categorizer.dart` (prompt) | Port logic, simplified |
| SMS parser and confidence weights | `finsight/lib/data/parsers/sms_parser.dart` | Port to Kotlin `RegexExtractor` |
| Bank profiles | `finsight/assets/data/bank_profiles/*.json` | Copy into `composeResources` as the rule fast path; Gemma covers every format not in them |
| Notification capture (already Kotlin) | `finsight/android/app/src/main/kotlin/com/finsight/sms/`: `BankNotificationService.kt` (SMS app package list), `BootReceiver.kt`, `ServiceWatchdog.kt`, `AlarmReceiver.kt`, OEM battery settings in `SmsMethodChannelHandler.kt` | Copy into `androidMain`; replace the Flutter method channel with a direct call into the pipeline |
| Why notifications, not READ_SMS | `NOTIFICATION_SMS_TRANSITION_ANALYSIS.md` | Pitch and Q&A answers on Play policy and privacy |
| 58 real SMS with expected output | `finsight/assets/data/sms_test_templates.json` | Demo Replay data and unit tests |
| More SMS formats | `Kimi_Agent_SMS Parsing Data Collection/` | Extra patterns |
| Category keywords | `finsight/scripts/training_data/processed/category_mapping.json` | Rule keyword map |
| Labelled examples | `finsight/scripts/training_data/processed/unified_transactions.csv` | Few-shot examples, quick accuracy check |
| Schema | `finsight/assets/database/schema_v1.sql` | Translate to SQLDelight |
| Account matching | `finsight/lib/services/account_resolver.dart` | Order: bank, then sender, then card last 4 |
| Receipts/screenshots | `screenshots_transaction_recipt/`, `transaction_reciept_data/` | P3 multimodal demo |

Skip on purpose: full self-transfer reconciliation and unknown-account merging. They were the most fiddly features last time; only the 6.2 minimum (fingerprint, refund/reversal keywords) is in scope.

### Design tokens: InvesTerra

Private repo: `gh repo clone faraz152/investerra`. From `core/designsystem/src/main/kotlin/pk/investerra/core/designsystem/`, take:
- `theme/InvesTerraColors.kt` (Indigo Night, dark default, role-based colours, gain/loss/warning tones). These map well to green, yellow and red confidence bands.
- `theme/Typography.kt` (Manrope, tabular figures).
- `theme/Shapes.kt`, `Dimensions.kt`, `ComponentSizes.kt`, `Motion*.kt`.
- `component/`: Button, Chip, ChangeChip, FinanceRows, AllocationBar, EdgeSurface, IndexCard.
- `docs/adr/0004-money-and-numerics.md`.

InvesTerra is Android-only Compose, so port the theme into `commonMain` and replace `R.font.*` with Compose Multiplatform resources. Allow 45 minutes. If it drags, take only colours and typography and use stock Material3 components.

---

## 10. Team split

| Role | Owns |
|---|---|
| **A: KMP/data lead** | Project skeleton (KMP wizard), Koin, SQLDelight, repositories, navigation, merges |
| **B: UI** | Theme port, Dashboard, list with badges, Review Inbox, "Why?" sheet, Coach chat screen with route badges, "What was shared?" sheet, Health Score card, report view, polish |
| **C: AI runtime + prompts** | LiteRtLlm, model loading, prompt tuning on device, `GeminiCloudLlm` (Firebase AI Logic), Privacy Router, Redactor, NumberVerifier, Coach agent |
| **D: Pipeline + data** | Gate, RegexExtractor port, Verifier, RuleCategorizer, Scorer, Health Score formula, analytics SQL tools, report builder, unit tests, demo data, pitch deck |

- With 3 people, merge D into A.
- With 2 people: A+D and B+C.

**First 30 minutes together:** agree on `Transaction`, `CategoryCandidate`, `PipelineResult`, `LocalLlm`, `CloudLlm`, `SanitizedPrompt`/`SafeFact` and the repository interfaces. After that everyone codes against fakes in parallel, and D's pipeline is JVM-testable with `FakeLlm` before the model even works.

## 11. Timeline (adjust to the announced schedule)

| Time | Milestone |
|---|---|
| 0:00 to 0:45 | Team formed, theme heard, scope agreed, repo created, interfaces written |
| +1:30 | App builds on the phone with theme and fake data. Model loads in a scratch screen. |
| +2:30 | Regex pipeline end to end: replay SMS, transaction saved, dashboard shows it |
| +3:30 | Gemma categorization and hybrid scoring live, with badges |
| +4:30 | Gemma extraction with the verifier; Review Inbox and merchant memory |
| +5:30 | FinSight Coach: chat with LOCAL route and tools, Health Score card |
| +6:15 | Coach HYBRID and CLOUD routes, offline fallback, "What was shared?", monthly report; bug-fix pass |
| Last 90 min | **Feature freeze.** Build the release APK, rehearse the demo twice, record a backup video. |

## 12. Demo script (about 3.5 minutes)

1. **Hook (20 s):** "Your phone is already notified about every payment you make. Yet to track your money you either type every transaction by hand, or hand your financial data to someone's server. And even then, nobody tells you whether you're doing OK. FinSight fixes all three."
2. **Privacy proof (15 s):** airplane mode on. Show the app's permissions: no SMS inbox access. "Capture and analysis run entirely on this phone, with no network at all."
3. **Live capture (20 s):** a transaction notification arrives and appears in FinSight within seconds, categorized, with no typing. For a reliable trigger, post a test notification with `adb shell cmd notification post ...` (verify the exact syntax on the phone, and allowlist the shell package in debug builds only), or use Demo Replay.
4. **Import (30 s):** tap Import and the 58 SMS flow in. Read out the real counts from the screen, for example "52 by rules instantly, 6 by Gemma, 3 for review."
5. **Explain three transactions (50 s):**
   - **Green, Rule:** a wallet transfer in a known format; instant.
   - **Green, Rule+Gemma:** an unknown merchant where Gemma and the keyword rules agree, so confidence goes up.
   - **Yellow or red:** a new bank format. Gemma extracted it, and the verifier confirmed the amount appears in the text. Open "Why?" to show the checks.
6. **Learn and quick add (15 s):** correct one category, replay a similar SMS, and it comes back green as "You". Then type "chai 150 cash" in Quick Add: same pipeline, same result.
7. **Coach, still offline (20 s):** "What did I spend most on this week?" Gemma picks the tool, the SQL runs, and the answer appears with a chart and the **On-device** badge.
8. **Coach goes hybrid (40 s):** turn airplane mode off; nobody switches a mode. Ask "How healthy are my finances and what should I fix first?" The Health Score card (computed on the phone) appears with a **Hybrid** answer from Gemini. Open **"What was shared?"**: only "savings rate 12%, runway 1.4 months…", with no merchants, names or accounts. Then "make my October report" produces the report in seconds.
8b. **Pulse, What If? and Privacy (30 s):** show a Pulse card with its "why", ask "Can I afford a laptop for PKR 150,000?", then open the Privacy Center: coverage, sources, cloud switch, what was shared.
9. **Close (15 s):** "Every way your money shows up, captured in one place, plus a coach that tells you how you're doing. Your data stays on the phone; only knowledge comes from the cloud. Gemma proposes, rules verify, you decide. Built on Gemma, Gemini, LiteRT-LM and Kotlin Multiplatform." Optionally end with the new pitch line from 6.9.

If venue Wi-Fi fails at step 8, use the hotspot. If both fail, the router falls back to the device anyway: say so, and show the hybrid path in the backup video.

Record a backup screen capture before presenting.

## 13. If the theme is not finance

Keep the engine and change the data. The reusable pattern is: **messy text on the phone → hybrid rules + on-device Gemma extraction → verified, scored, explainable records → an agent that answers questions over them.** Examples: medicine schedules from pharmacy SMS, parcel and order tracking, utility-bill reminders, school fee tracking. Decide within 30 minutes.

## 14. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Model download or loading fails | Download on your own connection before the event; `FakeLlm` keeps the app demoable |
| LiteRT-LM API differs from expectations | Read the README first thing; everything else depends only on `LocalLlm` |
| Gemma too slow per transaction | Fast path skips Gemma for confident rule hits; use the smaller model; short prompts; batch in the background |
| Gemma returns bad JSON or invents values | Defensive parsing, one retry, allowed-list validation, verifier against the SMS text |
| KMP/Gradle setup eats an hour | Start from the official KMP wizard template; do not hand-edit Gradle beyond adding dependencies |
| InvesTerra port drags | Colours and typography only, stock Material3 |
| Venue Wi-Fi down during the Coach demo | Hotspot first; the router falls back to the device automatically; backup video shows the hybrid route |
| Gemini API key leaks | Use Firebase AI Logic; no raw key in the APK or git; App Check before release |
| Judges challenge the privacy claim now that the app has `INTERNET` | Show the architecture test, the `SanitizedPrompt` type, the "What was shared?" sheet and the Cloud log |
| Coach gives wrong or harmful money advice | Numbers only from device tools, NumberVerifier on cloud text, education framing with a disclaimer, no product recommendations, official sources for rates |
| Coach scope creeps | Chat + Health Score + monthly report is the P1 line; everything else in 5.2 is P2 or P3 |
| Overclaiming on stage | Use the honest wording in 6.9; show measured accuracy and the coverage indicator |
| Model too big for the demo phone | Measure RAM, latency and heat first; fall back to the smaller model |
| SMS with embedded instructions (prompt injection) | Schema-validated output only; adversarial test set (6.8) |
| Running out of time | Cut P2 and P3 first, then P1 items 12 to 15. Never cut the hybrid scoring or the integrity minimum: they are the trust story. |

## 15. Morning checklist (laptop, before 9:00)

- [ ] Android Studio (stable), JDK 17+, Android SDK, Kotlin Multiplatform plugin installed.
- [ ] `gh auth login`, then `gh repo clone faraz152/finsight-complete-project` (`main` has everything) and `gh repo clone faraz152/investerra`.
- [ ] Phone: USB debugging on, `adb devices` shows it. Bring the charger, a cable and a hotspot.
- [ ] Gemma `.litertlm` model downloaded on your own connection and copied to the phone.
- [ ] Firebase project created, Firebase AI Logic enabled, `google-services.json` ready (not committed if the repo is public), one Gemini test call working, App Check set up, AI monitoring logging off.
- [ ] Demo phone: model load time, peak RAM and seconds per call measured.
- [ ] Agora sample branch cloned for reference.
- [ ] This doc open, plus `PRD_FinSight_Complete.md` section 1 for pitch facts.

## 16. What is deliberately not on GitHub

These stay on the old PC and are not needed today: the GGUF models (`finsight/models/`, `backend/local_models/`, `finsight/scripts/exported_model/`; wrong format for LiteRT-LM), raw training data (re-downloadable with `STEP1_download_datasets.py`), Python venvs, build output, and `backend/.env`. The useful small training files (`category_mapping.json`, `unified_transactions.csv`) are on GitHub.
