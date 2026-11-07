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
