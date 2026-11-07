---
name: finsight-coach
description: Work on FinSight Coach, the hybrid AI finance assistant (chat intents, SQL-like tools, monthly report, Financial Health 2.0, FinSight Pulse insights, What If? simulator, Privacy Router, Redactor, SanitizedPrompt, NumberVerifier, Gemini cloud call, Privacy Center). Use when adding a Coach question type, Urdu/Roman Urdu intents, a new insight, or anything that might send data to the cloud.
---

# FinSight Coach

Code: `finsight-app/composeApp/src/commonMain/kotlin/com/finsight/coach/`; cloud client `androidMain/.../GeminiCloudLlm.kt`.

## Request flow (`CoachAgent.ask`)
1. Intent by rules (English + Roman Urdu keywords) → a tool (`spend_by_category`, `top_merchants`, `compare_months`, `health`, `pulse`, `what_if`, `report`, `explain`).
2. Tool runs in Kotlin over the repository → typed result + `SafeFact`s.
3. `PrivacyRouter.route`:
   - cloud switch off, no key, or offline → **LOCAL**
   - question mentions a known merchant/person/4+ digits → **LOCAL**
   - personal tool + knowledge needed (health advice, report summary, what-if advice) → **HYBRID**
   - no tool, general money question → **CLOUD**
4. LOCAL answers are Kotlin templates over tool numbers. HYBRID/CLOUD send `SanitizedPrompt` to Gemini; `NumberVerifier` drops sentences with figures not in the facts; timeout/error → LOCAL fallback.
5. Every cloud call is appended to `CoachLog` (shown in Privacy tab "What was shared?").

## Privacy rules (never break)
- `SanitizedPrompt` has a private constructor; build only via `Redactor.build(question, facts)`.
- `SafeFact` values: percentages, amounts rounded to nearest 1,000, category names, counts, period labels, months ratio. Never merchant names, people, digits ≥4, raw SMS, exact balances, dates.
- Charts/cards use tool numbers, never cloud text.

## Health 2.0
Profile (Salaried/Freelancer/Student/Self-employed/Household) changes thresholds. Missing data = "unknown", excluded and reweighted, not zero. Show "based on N of 6 checks" + data coverage. Refuse a score with < 7 days data.

## Tone
Education, not advice. One-line disclaimer. No specific products. Reply in the user's language (Roman Urdu in → Roman Urdu out for template answers where available).
