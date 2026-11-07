# Phase 3 hand-off

> Status 7 Nov: Afza did T1. T1b–T6 were finished by the lead's agents (collaborator had no Java on her laptop), reviewed, merged into main and checked on the phone. T7 (icons) remains optional.
: UI & screens (for building with AI help)

Hi! This phase is about the **screens**. The hard parts (pipeline, Coach logic, privacy, design system) are done.
You will mostly ask Claude Code to build screens, and a test will tell you if anything breaks the InvesTerra design.

## 1. Setup (15 min, once)
1. Install Android Studio (stable) and JDK 17+. Clone: `gh repo clone faraz152/finsight-hackathon && cd finsight-hackathon`
2. Copy `finsight-app/local.properties.example` → `finsight-app/local.properties` and set `sdk.dir` (Android Studio shows it under Settings → Android SDK).
3. Open the **`finsight-app`** folder in Android Studio, wait for Gradle sync.
4. Phone: Developer options → USB debugging ON (Xiaomi: also "Install via USB" ON). Run ▶ `composeApp`.
5. In the app: Home → **Import demo SMS** gives you data to look at.

## 2. How to work with Claude Code
Open a terminal in the repo root and run `claude`. It reads `CLAUDE.md` and the skills automatically.
Always start a task with: **"Use the finsight-design-system skill."**
After every task run: `cd finsight-app && ./gradlew :composeApp:testDebugUnitTest` → must say BUILD SUCCESSFUL.
**Work only on the branch `phase-3-ui`** (never `main`, never the old `phase-3-app`):
```bash
git checkout phase-3-ui && git pull
# ... do one task with Claude, run the tests ...
git add -A && git commit -m "T1: Goals screen" && git push
```
One task = one commit, push after each task. Faraz merges `phase-3-ui` into `main` at checkpoints.
Before starting each new task, get Faraz's latest changes: `git pull origin main`.

## 3. Rules you never have to remember (the test checks them)
No raw colours / dp / sp, no shadows or gradients, money always via `AmountText` / `FsTheme.type.numeric`.
Full rules + component table: `.claude/skills/finsight-design-system/SKILL.md`.

## 4. Tasks (in order). Each has a ready prompt.

- [x] **T1 · Goals screen** — done by Afza, reviewed + merged (review fix: list now refreshes when transactions change)
- [x] **T1b · Back gesture closes Goals** (review follow-up)
  > Use the finsight-design-system skill. When Goals & bills is open, the Android back gesture should close Goals instead of leaving the app. Add an expect/actual `PlatformBackHandler(enabled, onBack)` (androidMain: androidx.activity.compose.BackHandler) and use it in App.kt while goalsOpen. Also show no tab as selected while Goals is open. Run the unit tests.
- [ ] ~~T1 · Goals screen~~ (original prompt kept below for reference) — plan 6.1/6.7
  > Use the finsight-design-system skill. Add a Goals screen in a new file ui/screens/GoalsScreen.kt, opened from a new QuickAction "Goals & bills" in the centre hexagon menu in App.kt (the bottom bar keeps 4 tabs + hexagon). Show: recurring payments from state.tools.recurringPayments() as cards with name, amount (AmountText) and "around day N"; a "What If?" card with an FsTextField for a price and an FsButton "Check" that shows WhatIf.afford(state.tools, priceMinor) verdict and lines. Use only design-system components. Run the unit tests.

- [x] **T2 · Health detail sheet** — plan 6.5
  > Use the finsight-design-system skill. When the Financial health MetricTile on Home is tapped, open a ModalBottomSheet (not go to Coach) showing HealthScore.compute(...) : score with band colour, a profile FsChip row to switch state.profile, then each check as a KeyValueRow-style row with its detail and an FsTag "unknown" when score is null, then "Next steps" list. Run the unit tests.

- [x] **T3 · Onboarding with prominent disclosure** — plan 6.4 (Google Play requirement)
  > Use the finsight-design-system skill. Add ui/screens/OnboardingScreen.kt shown on first launch (keep a Boolean in AppState, in memory is fine). 3 steps with FsCard: what FinSight reads (notifications from SMS + allowed wallet apps only, never the SMS inbox), what never leaves the phone, cloud AI is optional. Final FsButton "Allow notification access" calls state.openNotificationSettings(); FsButton Text variant "Try with demo data" imports demo SMS and closes onboarding. Run the unit tests.

- [x] **T4 · Transactions polish**
  > Use the finsight-design-system skill. In TransactionsScreen group rows by day with a caption header ("Today", "Yesterday", date), add FsChip filters for All / Review / Income / Transfers / Refunds, and an EmptyState per filter. Run the unit tests.

- [x] **T5 · Pulse list on Coach**
  > Use the finsight-design-system skill. At the top of CoachScreen show a horizontally scrolling row of Pulse cards (Pulse.cards(state.tools)) as FsCard(tone = NoticeTone.Info) with title, body, "Why:" caption and a Text-variant FsButton "Dismiss" that hides the card (remember a set of dismissed titles). Run the unit tests.

- [x] **T6 · Accuracy card (evaluation for judges)** — plan 6.8
  > Use the finsight-design-system skill. In PrivacyScreen add an FsCard "How accurate is FinSight?" showing: templates tested 59, amount correct 59/59, direction correct 59/59, transactions sent for review = count of needsReview / total from state.repo.transactions. Use KeyValueRow. Run the unit tests.

- [ ] **T7 · Icons instead of glyphs (stretch)**
  > Use the finsight-design-system skill. Replace the text glyphs in FsBottomBar and the quick-action discs with Material icons (add compose.materialIconsExtended only if needed) tinted with FsColors roles. Run the unit tests and build the APK.

## 5. When stuck
- Red build? Copy the error into Claude: "Fix this build error, keep the design-system rules."
- Test says "Design token violations: X.kt:42 raw dp" → ask Claude to replace it with the right token.
- Don't touch `pipeline/`, `coach/` or `GeminiCloudLlm.kt` in this phase; ask Faraz first.
