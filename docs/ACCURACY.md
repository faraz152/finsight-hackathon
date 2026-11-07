# Pipeline accuracy (rules only, no LLM)

Run: `cd finsight-app && ./gradlew :composeApp:testDebugUnitTest -i | grep -E 'TEMPLATES|ACCURACY|MISS'`

| Fixture | n | Amount % | Direction % | Merchant % | Sent to review % |
|---|---|---|---|---|---|
| `sms_test_templates.json` (PK, IN, AE, GB, US, CA) | 59 | 100.0 | 100.0 | 100.0 (37/37) | 42.4 (25/59) |
| `pk_extra_templates.json` (Pakistan only) | 38 | 100.0 | 100.0 | 100.0 (34/34) | 2.6 (1/38) |

- Merchant % counts only samples with a labelled merchant; a match means the normalised extracted name contains the label or the other way round.
- Review % is the share of accepted records with final confidence < 0.90 (yellow/red band), which go to the Review Inbox.
- Adversarial suite (`AdversarialTest`, 12 tests): OTPs and promos are dropped; phishing links, BISP/prize scams and prompt-injection text become `ScamWarning`; conflicting amounts go to review; balance-before-amount, `Rs.1,25,000`, `PKR1500`, and Urdu or Roman Urdu text all parse; the same TID twice is merged into one record; refunds are not income; reversals are excluded from spend.
- Caveat: `pk_extra_templates.json` uses real PK formats from the `ref/finsight` JazzCash/bank analysis, but we wrote and labelled the messages ourselves while tuning the rules, so it is not a blind holdout. Expect lower numbers on unseen real inboxes.
