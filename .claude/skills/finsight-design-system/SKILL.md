---
name: finsight-design-system
description: Build or change ANY FinSight screen or UI element (Compose) using the InvesTerra design tokens and components. Use whenever touching files in finsight-app/.../ui/, adding a screen, card, button, chip, list row, sheet, colour, spacing, font or icon, or when asked to "make it look better", "follow the design", "add a Goals screen", "polish UI".
---

# FinSight design system (ported from InvesTerra "Indigo Night")

Code: `finsight-app/composeApp/src/commonMain/kotlin/com/finsight/ui/designsystem/` (`Tokens.kt`, `Theme.kt`, `Components.kt`).
Screens: `.../ui/screens/*.kt` — one file per screen. Reference (read-only): `ref/investerra/core/designsystem/`.

## The 6 rules (a unit test enforces 1–4)
1. **No raw colours.** Use `FsColors.role.*` (fill / edge / content / status / change). Never `Color(0x…)` or `Color.Red`.
2. **No raw sizes.** Use `Spacing.*` (4 dp grid), `Radius.*`, `Sizes.*`. Never `12.dp` in a screen.
3. **No raw text sizes.** Use `FsTheme.type.text.*` for words and `FsTheme.type.numeric.*` (tabular) for EVERY number / money.
4. **No shadows, elevation, gradients or blur.** Depth = the creem edge: `EdgeSurface` / `FsCard`.
5. **Compose existing components first** (table below). Need something new? Add it to `Components.kt` built on `EdgeSurface` + tokens, then use it.
6. Dark theme only (Indigo Night). Money: `formatPkr(minor)`; credit = `change.upContent` (green), debit = primary text.

Check: `cd finsight-app && ./gradlew :composeApp:testDebugUnitTest` → `DesignTokenGuardTest` lists file:line of any violation.

## Components
| Need | Use |
|---|---|
| Card / panel | `FsCard { }` · tinted notice: `FsCard(tone = NoticeTone.Info/Success/Warning/Error)` · tappable: `onClick =` |
| Any custom surface | `EdgeSurface(fill, edgeColor, edge = EdgeStyle.Card/Control/Outline, shape, onClick)` |
| Button | `FsButton(label, onClick, variant = Primary/Secondary/Destructive/Text, small = true)` |
| Filter / suggestion / picker chip | `FsChip(text, selected, onClick)` |
| Static label (category, source, status) | `FsTag(text, TagTone.Neutral/Brand/Info/Success/Warning/Error)` |
| Money | `AmountText(text, credit = true/false, style = TextStyleSize.Hero/Large/Title/Body/Label/Caption)` |
| Big number tile | `MetricTile(label, value, caption, valueColor)` |
| Screen title | `ScreenHeader(title, subtitle) { trailing }` |
| Section title / overline | `SectionHeader("…")`, `Overline("…")` |
| Label: value line | `KeyValueRow(key, value)` |
| Category bars | `ShareBars(rows) { formatPkr(it) }` |
| Raw text / payload | `CodeBox(text)` |
| Empty list | `EmptyState(title, body) { action }` |
| Input | `FsTextField(value, onChange, placeholder, onDone =)` |
| Status dot | `BandDot(bandColor(band))` |
| Bottom nav + centre smart button | `FsBottomBar(items, selected, onSelect, hexagonOpen, onHexagon)` (InvesTerra C-10/C-11 hexagon) — already wired in `App.kt` |
| Centre button menu | `QuickActionsOverlay(open, listOf(QuickAction(title, subtitle, glyph, QuickActionTone.*, locked) { … }), onDismiss)` — add FinSight actions in `App.kt` |
| Bottom sheet | `ModalBottomSheet(containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim)` + `Spacing.sheetPadding` |

## Layout recipe
```kotlin
Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
       verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical)) {
    ScreenHeader("Goals", "Save for what matters")
    FsCard { Overline("Emergency fund"); AmountText(formatPkr(x), style = TextStyleSize.Large) }
    FsButton("Add goal", onClick = { … }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(Spacing.s6))
}
```
Screens read data from `AppState` (`state.repo`, `state.tools`, `state.coach`) and never compute money themselves: put new numbers in `coach/Tools.kt`.
