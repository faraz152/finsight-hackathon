---
name: finsight-pipeline
description: Work on the FinSight Transaction Intelligence Pipeline (Gate, RegexExtractor, Integrity layer, RuleCategorizer, Scorer, merchant memory). Use when adding SMS formats or banks/wallets (JazzCash, Easypaisa, Raast, HBL, Meezan...), fixing wrong amount/merchant/type extraction, adding categories or keywords, duplicate/refund/reversal handling, or checking accuracy against sms_test_templates.json.
---

# FinSight pipeline

Code: `finsight-app/composeApp/src/commonMain/kotlin/com/finsight/pipeline/`.

Flow (`Pipeline.process(RawInput)`):
1. `Gate.check` — drop OTP/promo/no-amount; flag scams.
2. `RegexExtractor.extract` — amount, type, merchant, account last4, balance, reference; confidence weights amount .40, merchant .20, date .15, type .15, balance .10.
3. `Integrity` — fingerprint `merchantKey|amount|last4|ref|10-min bucket`; duplicate → merge evidence; refund/reversal/own-transfer/fee → `TxnKind`.
4. `RuleCategorizer` — merchant memory (0.98) → exact merchant (0.95) → keywords (0.75–0.85) → transfer heuristic (0.80).
5. `Scorer` — bands: ≥0.90 green auto-accept, 0.75–0.89 yellow, <0.75 red → Review Inbox.
6. Learn — `MerchantMemory.correct(merchantKey, category)` on user correction.

## Rules
- Pure Kotlin in `commonMain`; no Android imports. Money is `Long` paisa.
- Amount must literally exist in the text (anti-hallucination). Never take the balance as the amount.
- Add a keyword before adding a regex; add a regex before calling an LLM.
- Every decision appends a human-readable line to `Transaction.explanation` (shown in the "Why?" sheet).

## Test data
- `composeApp/src/androidMain/assets/sms_test_templates.json` — 58 messages with `expected` {amount, transactionType, merchantName, balance, transactionId}.
- Reference (read-only): `ref/finsight/finsight/assets/data/bank_profiles/pakistan_banks.json`, `ref/finsight/finsight/scripts/training_data/processed/category_mapping.json`, old parser `ref/finsight/finsight/lib/data/parsers/sms_parser.dart`.
- Quick accuracy check: `./gradlew :composeApp:testDebugUnitTest` (commonTest `PipelineTest`). Report amount accuracy and type accuracy; aim ≥ 90% amount on PK templates.

## Pakistan specifics
- Senders: JazzCash `8558`, Easypaisa `3737`, banks use short codes/names. Wallet "sent to X Account: 03xx" = TRANSFER_OUT. "received from" = INCOME unless it matches the user's own account (TRANSFER_IN).
- Raast: "Raast", "IBFT", "funds transfer" → transfer heuristics.
- Utility keywords: K-Electric, LESCO, IESCO, SNGPL, SSGC, PTCL, Nayatel, Jazz/Zong/Telenor/Ufone top-up.
