# Judge Q&A

Short answers first; the detail line is for follow-ups. Be honest about what is built vs roadmap.

---

### "You say private, but the app has the INTERNET permission."
Your transactions never leave the phone. `INTERNET` is used by exactly one class, `GeminiCloudLlm.kt`, and it only accepts a `SanitizedPrompt`. That type has a private constructor; only the on-device `Redactor` can build one, from an allowlist of facts (percentages, amounts rounded to PKR 1,000, category shares, counts). So the type system makes the Redactor the only way to reach the cloud, and the Redactor only passes allowlisted facts and a scrubbed question (no merchants, phone numbers or account digits).
- Proof on the phone: "What was shared?" on every answer, and Privacy → Cloud log with the exact payload.
- Proof in the repo: grep for network classes finds only that one file. An automated architecture test (Konsist) is the next step; today it is enforced by the type system plus code review.
- One switch in the Privacy Center turns cloud AI off entirely.

### "Why not just read SMS with READ_SMS?"
Three reasons. Privacy: we never want the whole inbox, only payment messages the user already sees. Policy: Google Play restricts `READ_SMS` to default SMS apps and a few approved use cases; a finance tracker would likely be rejected. Coverage: many banks and wallets (JazzCash, Easypaisa) notify through their apps, not SMS, and a notification listener sees both. The listener has a package allowlist; everything else is ignored in memory and never stored.

### "What about Play's policy on notification access?"
Notification listener access is a sensitive capability. Play requires a prominent in-app disclosure and affirmative consent before using it, and a clear core-feature reason. Today the Privacy tab explains what is read and why, and the "Enable" button sends the user to Android's own settings, where they grant it themselves. A dedicated onboarding disclosure screen is on our task list. For release we would also complete the Data safety form: data processed on device, not collected, nothing shared except the redacted Coach facts.

### "How accurate is it, and how did you measure it?"
Rules-only pipeline on 59 labelled bank and wallet SMS templates: 59/59 amounts correct and 59/59 debit/credit direction correct (14/14 Pakistani formats). Measured by a JVM unit test (`PipelineAccuracyTest`) that runs every template through the real pipeline and compares with the labels. Honest caveat: the rules were written with those formats in view, so it is a regression score. The real test is unseen formats, which is where the on-device model comes in. Full method: `docs/ACCURACY.md`.

### "What does Gemma do, and what does Gemini do?"
| | Gemma (on device, LiteRT-LM) | Gemini (cloud) |
|---|---|---|
| Role | Long tail of the pipeline: unseen SMS formats, unknown merchants; local chat phrasing | Knowledge and explanation in Coach ("what should I fix first?", "what is an emergency fund?") |
| Sees | Raw message text, on the phone | Only the redacted `SanitizedPrompt` |
| Status today | In progress. The `LocalLlm` interface is in place; this build ships with rules only (`NoLocalLlm`) | Working via REST when an API key is configured |

Rule: "Gemma proposes, rules verify": an amount from the model is accepted only if it literally appears in the message.

### "Why rules first instead of just asking an LLM?"
Money needs exact numbers. Rules are instant, free, explainable and cannot hallucinate an amount. They handle known formats at near-zero battery cost. A model is slower and can invent values, so it should only handle what the rules are unsure about, and its output is checked against the original text. It also means the app works fully today, before the model is on the phone.

### "Is the coverage indicator real or decorative?"
It is computed, not invented. When we see balances in messages, we check whether each balance change is explained by a captured transaction ("N of M balance changes explained by captured transactions"). Without balances it falls back to days with activity over the date span, and says so. It is an estimate of what we can see, shown next to the Health score so we never call someone healthy on partial data. It does not yet account for other accounts or cash you never told us about.

### "Can Coach give wrong financial advice?"
All personal numbers come from Kotlin tools on the device; the model never computes them. Cloud answers pass a `NumberVerifier` that deletes any sentence with a figure about you that we did not send. The prompt forbids product recommendations and tells it to point to official sources for tax, Zakat or scheme rates. Every answer says "Education, not financial advice."

### "What if the network is down?"
Capture, the pipeline, Health, Pulse, What If and local Coach answers need no network. If the cloud is off, unreachable, slow (9 s) or there is no key, the router answers on the device and says why.

### "What's stored, and where?"
In this prototype, in memory only, inside the app process; `allowBackup="false"` so nothing goes to Android cloud backup. Production: SQLDelight with SQLCipher and a key in Android Keystore, plus an optional biometric lock.

### "iOS?"
The pipeline, Coach, privacy layer and UI are in `commonMain`, so they move to iOS unchanged. iOS does not let apps read other apps' notifications, so iOS input would be share-sheet, statement import, receipt scan and manual entry. Android first because automatic capture is the core experience and Android is the large majority of phones in Pakistan.

### "Business model?"
Free core (capture, review, basic Coach) on device, which costs us almost nothing to serve because inference is local. Premium for advanced planning (goals, reports, What If scenarios, household budgets, committee tracking) as a low monthly subscription. Later, B2B: a white-label privacy-first SDK for banks and wallets that want insights without centralising customer data. Never: selling data or ads based on transactions.

### "Isn't this just another expense tracker?"
Trackers stop at charts and need typing. FinSight captures automatically, verifies and explains each record, tells you how complete its picture is, and then diagnoses (Health 2.0), warns (Pulse) and plans (What If), while keeping your history on the phone.

### "Did you build all this today?"
The code was written fresh today. We reused data and design from the team's earlier work: the labelled SMS templates, the category taxonomy and the InvesTerra design tokens.

### "What's next?"
1. On-device Gemma via LiteRT-LM for unseen formats and unknown merchants, with verification against the text.
2. Persistent encrypted storage (SQLDelight + SQLCipher + Keystore), app lock.
3. Onboarding with prominent disclosure; per-app source toggles.
4. Statement import (CSV/PDF) and receipt scan with multimodal Gemma.
5. Goals, recurring-bill reminders, monthly report with PDF export.
6. Architecture test that fails the build if network code appears outside the cloud class; Firebase AI Logic with App Check for the Gemini call.
7. Evaluation on a held-out set of unseen formats; category precision/recall.
8. iOS with share-sheet and import as input.
