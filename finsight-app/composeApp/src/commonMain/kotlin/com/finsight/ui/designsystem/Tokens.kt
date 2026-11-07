package com.finsight.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Ported 1:1 from InvesTerra `core/designsystem/theme` (Indigo Night, dark default, DD-01).
// RULE: screens never use Color(0x...) or raw dp. Use FsColors roles, Spacing, Radius, Sizes, Edges.

/** Base palette (InvesTerra `color.dark.*`). Prefer [FsColors.role] in feature code. */
@Immutable
object FsColors {
    // bg
    val canvas = Color(0xFF0D0D14)
    val surface = Color(0xFF16161F)
    val subtle = Color(0xFF1F1F2B)
    // border
    val borderDefault = Color(0xFF2A2A38)
    val borderStrong = Color(0xFF3A3A4C)
    // text
    val textPrimary = Color(0xFFECECF4)
    val textSecondary = Color(0xFFA6A6BC)
    val textTertiary = Color(0xFF8A8AA3)
    // brand
    val brandFill = Color(0xFF8B8CF8)
    val brandOnFill = Color(0xFF0D0B2E)
    val brandText = Color(0xFFA5A6FF)
    val brandSubtle = Color(0xFF22214A)
    // tones (text + subtle container)
    val gainText = Color(0xFF4ADE9B); val gainSubtle = Color(0xFF0F2A1F)
    val lossText = Color(0xFFFF7A8A); val lossSubtle = Color(0xFF301620)
    val warningText = Color(0xFFFBBF4D); val warningSubtle = Color(0xFF2B2112)
    val infoText = Color(0xFF7FB0FF); val infoSubtle = Color(0xFF13233D)
    // creem edges
    val edgeCard = Color(0xFF3B3A7A)
    val edgeBrand = Color(0xFF5E5FD8)
    val edgeNeutral = Color(0xFF3A3A4C)
    val edgeLoss = Color(0xFF6A2635)
    val edgeGain = Color(0xFF1F5A3E)
    val edgeWarn = Color(0xFF5C4418)
    val edgeSubtle = Color(0xFF3B3A7A)
    val edgeInfo = Color(0xFF2B4A7A)
    /** Flat scrim behind sheets (no blur, DD-08). */
    val scrim = Color(0x9E000000)

    /** Semantic roles: the only colours screens should need. */
    object role {
        object fill {
            val canvas = FsColors.canvas
            val card = surface
            val cardInset = subtle
            val buttonPrimary = brandFill
            val buttonSecondary = surface
            val buttonDestructive = lossSubtle
            val input = surface
            val sheet = surface
            val navBar = surface
            val chipSelected = brandSubtle
            val chipUnselected = surface
            val disabled = subtle
            val hexagon = brandFill
        }
        object edge {
            val card = edgeCard
            val cardSelected = brandFill
            val cardError = edgeLoss
            val buttonPrimary = edgeBrand
            val buttonSecondary = edgeNeutral
            val buttonDestructive = edgeLoss
            val noticeInfo = edgeInfo
            val noticeSuccess = edgeSubtle
            val noticeWarning = edgeWarn
            val noticeError = edgeLoss
            val navBar = edgeCard
            val divider = borderDefault
            val inputRest = textTertiary
            val inputFocus = brandFill
            val hexagon = edgeBrand
        }
        object content {
            val primary = textPrimary
            val secondary = textSecondary
            val tertiary = textTertiary
            val onBrand = brandOnFill
            val link = brandText
            val destructive = lossText
            val disabled = textTertiary
        }
        object status {
            val successContent = brandText; val successContainer = brandSubtle
            val infoContent = infoText; val infoContainer = infoSubtle
            val warningContent = warningText; val warningContainer = warningSubtle
            val errorContent = lossText; val errorContainer = lossSubtle
        }
        /** Money direction: credit = up (gain), debit = down (loss). */
        object change {
            val upContent = gainText; val upContainer = gainSubtle
            val downContent = lossText; val downContainer = lossSubtle
            val flatContent = textSecondary; val flatContainer = subtle
        }
        /** Quick-action icon discs (InvesTerra QuickActionRoles): glyph, disc fill, disc edge. */
        object quickAction {
            val gain = Triple(gainText, gainSubtle, edgeGain)
            val loss = Triple(lossText, lossSubtle, edgeLoss)
            val info = Triple(infoText, infoSubtle, edgeInfo)
            val warning = Triple(warningText, warningSubtle, edgeWarn)
            val brand = Triple(brandText, brandSubtle, edgeBrand)
            val neutral = Triple(textTertiary, subtle, edgeNeutral)
        }
    }
}

/** InvesTerra `Spacing` (4 dp grid). */
object Spacing {
    val s0: Dp = 0.dp; val s1: Dp = 4.dp; val s2: Dp = 8.dp; val s3: Dp = 12.dp; val s4: Dp = 16.dp
    val s5: Dp = 20.dp; val s6: Dp = 24.dp; val s8: Dp = 32.dp; val s10: Dp = 40.dp; val s12: Dp = 48.dp; val s16: Dp = 64.dp
    val gutter: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val sectionGap: Dp = 24.dp
    val rowGapVertical: Dp = 12.dp
    val targetGap: Dp = 8.dp
    val sheetPadding: Dp = 24.dp
}

/** InvesTerra `Radius`. */
object Radius {
    val xs: Dp = 6.dp; val segmented: Dp = 11.dp; val control: Dp = 14.dp; val card: Dp = 20.dp
    val popup: Dp = 22.dp; val docked: Dp = 28.dp; val full: Dp = 999.dp
    val hexagonCorner: Dp = 8.dp
}

/** InvesTerra `Edges`: 1 dp outline + thicker bottom edge; press travel = bottom − outline. */
object Edges {
    val outline: Dp = 1.dp
    object bottom { val card: Dp = 4.dp; val control: Dp = 3.dp; val disc: Dp = 2.dp; val hexagon: Dp = 4.dp; val none: Dp = 1.dp }
    object pressTravel { val card: Dp = 3.dp; val control: Dp = 2.dp; val hexagon: Dp = 3.dp }
}

/** InvesTerra `ComponentSizes` (subset). */
object Sizes {
    val touchTarget: Dp = 48.dp
    val buttonHeight: Dp = 52.dp
    val buttonHeightSmall: Dp = 40.dp
    val chipHeight: Dp = 32.dp
    val inputHeight: Dp = 52.dp
    val listRowMin: Dp = 64.dp
    val iconSm: Dp = 16.dp; val iconMd: Dp = 20.dp; val iconLg: Dp = 24.dp
    val avatar: Dp = 40.dp
    val dot: Dp = 10.dp
    val barHeight: Dp = 10.dp
    val navBarHeight: Dp = 72.dp
    // C-11 hexagon (centre "smart" button)
    val hexagonWidth: Dp = 68.dp
    val hexagonHeight: Dp = 78.dp
    val hexagonRaise: Dp = 38.dp
    val hexagonSlack: Dp = 2.dp
    val hexagonGlyph: Dp = 28.dp
    // C-12 quick actions
    val quickActionsSideInset: Dp = 20.dp
    val quickActionsGap: Dp = 14.dp
    val quickActionRow: Dp = 62.dp
    val iconDisc: Dp = 44.dp
    val popoverWidth: Dp = 360.dp
}
