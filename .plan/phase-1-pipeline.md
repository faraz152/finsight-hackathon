# Phase 1 — Core pipeline, rules only (0:15–0:45) · owner D (+A)

Skill: `finsight-pipeline`.

- [x] `domain/Models.kt` — Money(Long), RawInput, Transaction (kind, status, fingerprint, explanation), Category
- [x] `pipeline/Gate.kt` — OTP / promo / scam / no amount
- [x] `pipeline/RegexExtractor.kt` — amount, type, merchant, last4, balance, ref, confidence weights
- [x] `pipeline/Integrity.kt` — fingerprint, duplicate merge, refund / reversal / fee / own-transfer kinds (plan 6.2)
- [x] `pipeline/RuleCategorizer.kt` — merchant memory, merchant list, keywords (PK utilities, wallets, food), transfer heuristic
- [x] `pipeline/Pipeline.kt` — stages + score bands + explanation lines + scam flag
- [x] `data/Repository.kt` — in-memory StateFlow, merchant memory, corrections, delete, coverage estimate
- [x] `commonTest/PipelineTest.kt` — embedded PK samples; amount + type accuracy printed
- [x] Accuracy ≥ 90% amount on PK samples

**Exit:** tests green, pipeline returns categorized records with explanations.

**Result (rules only):** 59/59 templates amount + direction correct (14/14 PK). `CoachFlowTest` covers import → duplicates merged, OTP dropped, scam flagged → Health/Pulse/What-If/router.

**Result (PK robustness, plan 6.3 / 6.8):** rules only, `PipelineAccuracyTest` + `AdversarialTest` (12 cases) green.
- `sms_test_templates.json` n=59: amount 100%, direction 100%, merchant 100% (37/37 labelled), review 42.4%.
- `pk_extra_templates.json` n=38 (JazzCash, Easypaisa, Raast/IBFT in+out, HBL, Meezan, UBL, MCB, Alfalah, ABL, POS, ATM, K-Electric/LESCO/SNGPL/PTCL/IESCO, top-ups, refund, reversal, pending, fees/WHT, salary, lakh separators, Roman Urdu): amount 100%, direction 100%, merchant 100% (34/34), review 2.6%.
- Adversarial: OTP/promo dropped; phishing links, BISP/prize, prompt injection → ScamWarning; conflicting amounts → review; same TID merged; refund not income; reversal not spend.
