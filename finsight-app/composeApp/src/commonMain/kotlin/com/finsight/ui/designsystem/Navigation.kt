package com.finsight.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// FinSight design system.
// Design = FinSight. Function = FinSight (the actions are passed in by App.kt).

data class NavItem(val label: String, val glyph: String)

/**
 * C-10 docked bar: 2 tabs · raised hexagon · 2 tabs. The raised band above the bar is transparent and lets touches
 * through; only the hexagon itself is tappable there. `selected = -1` (or any out-of-range index) highlights no tab,
 * e.g. while a full-screen page such as Goals is open.
 */
@Composable
fun FsBottomBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hexagonOpen: Boolean,
    onHexagon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.size == 4) { "FsBottomBar has 4 tabs + the centre hexagon" }
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(Sizes.hexagonRaise))
            Box(Modifier.fillMaxWidth().height(Edges.outline).background(FsColors.role.edge.navBar))
            Row(
                Modifier.fillMaxWidth().background(FsColors.role.fill.navBar).navigationBarsPadding().height(Sizes.navBarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { i, item ->
                    if (i == 2) Spacer(Modifier.weight(1f)) // hexagon slot
                    NavSlot(item, selected == i, Modifier.weight(1f)) { onSelect(i) }
                }
            }
        }
        FsHexagonButton(hexagonOpen, onHexagon, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun NavSlot(item: NavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) FsColors.brandText else FsColors.role.content.tertiary
    Column(
        modifier.fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(Radius.full)).background(if (selected) FsColors.brandSubtle else FsColors.role.fill.navBar)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s1),
        ) { Text(item.glyph, style = FsTheme.type.text.titleSmall, color = color) }
        Text(item.label, style = FsTheme.type.text.labelSmall, color = color, maxLines = 1, modifier = Modifier.padding(top = Spacing.s1))
    }
}

/**
 * C-11 hexagon: pointy-top, rounded corners, brand face over a solid edge hexagon 4 dp below it. No outline, no shadow.
 * Press: face drops 3 dp. Open: hexagon turns 60° (same silhouette) and "+" turns 45° into "×".
 */
@Composable
fun FsHexagonButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = Spring.StiffnessHigh), label = "hexPress")
    val turn by animateFloatAsState(if (open) 60f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "hexTurn")
    val glyphTurn by animateFloatAsState(if (open) 45f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "glyphTurn")
    val face = FsColors.role.fill.hexagon
    val edge = FsColors.role.edge.hexagon
    Box(
        modifier
            .size(Sizes.hexagonWidth, Sizes.hexagonHeight)
            .clickable(interaction, indication = null) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .drawBehind {
                val edgeDrop = Edges.bottom.hexagon.toPx()
                val radius = (size.height - edgeDrop - Sizes.hexagonSlack.toPx()) / 2f
                val cx = size.width / 2f
                val cy = radius
                val path = roundedHexagon(radius, cx, cy, Radius.hexagonCorner.toPx())
                val pivot = Offset(cx, cy)
                translate(top = edgeDrop) { rotate(turn, pivot) { drawPath(path, edge) } }
                translate(top = press * Edges.pressTravel.hexagon.toPx()) { rotate(turn, pivot) { drawPath(path, face) } }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.size(Sizes.hexagonWidth, Sizes.hexagonHeight - Edges.bottom.hexagon - Sizes.hexagonSlack)
                .offset(y = Edges.pressTravel.hexagon * press),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = FsTheme.type.text.headlineLarge, color = FsColors.role.content.onBrand, textAlign = TextAlign.Center, modifier = Modifier.rotate(glyphTurn))
        }
    }
}

/** Pointy-top hexagon with each corner rounded by [corner] (quadratic corners; no extra graphics library). */
internal fun roundedHexagon(radius: Float, cx: Float, cy: Float, corner: Float): Path {
    val v = (0 until 6).map { i ->
        val a = (-90.0 + 60.0 * i) * PI / 180.0
        Offset(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat())
    }
    fun toward(from: Offset, to: Offset, d: Float): Offset {
        val dx = to.x - from.x; val dy = to.y - from.y; val len = sqrt(dx * dx + dy * dy)
        return Offset(from.x + dx / len * d, from.y + dy / len * d)
    }
    return Path().apply {
        for (i in 0 until 6) {
            val cur = v[i]; val prev = v[(i + 5) % 6]; val next = v[(i + 1) % 6]
            val a = toward(cur, prev, corner); val b = toward(cur, next, corner)
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticTo(cur.x, cur.y, b.x, b.y)
        }
        close()
    }
}

enum class QuickActionTone { Gain, Loss, Info, Warning, Brand, Neutral }

data class QuickAction(
    val title: String,
    val subtitle: String,
    val glyph: String,
    val tone: QuickActionTone,
    val locked: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * C-12 quick actions: flat scrim (no blur) + a card of action rows sitting just above the hexagon. Draw it BELOW the
 * bottom bar so the hexagon (now "×") stays on top and closes it.
 */
@Composable
fun QuickActionsOverlay(open: Boolean, actions: List<QuickAction>, onDismiss: () -> Unit) {
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(FsColors.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            AnimatedVisibility(
                open, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                EdgeSurface(
                    Modifier
                        .navigationBarsPadding()
                        .padding(start = Sizes.quickActionsSideInset, end = Sizes.quickActionsSideInset,
                            bottom = Sizes.navBarHeight + Edges.outline + Sizes.hexagonRaise + Sizes.quickActionsGap)
                        .widthIn(max = Sizes.popoverWidth)
                        .fillMaxWidth(),
                    fill = FsColors.role.fill.sheet, edgeColor = FsColors.role.edge.card,
                    shape = RoundedCornerShape(Radius.popup), contentPadding = PaddingValues(Spacing.s2),
                ) {
                    actions.forEach { QuickActionRow(it) }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(a: QuickAction) {
    val (fg, disc, edge) = when (a.tone) {
        QuickActionTone.Gain -> FsColors.role.quickAction.gain
        QuickActionTone.Loss -> FsColors.role.quickAction.loss
        QuickActionTone.Info -> FsColors.role.quickAction.info
        QuickActionTone.Warning -> FsColors.role.quickAction.warning
        QuickActionTone.Brand -> FsColors.role.quickAction.brand
        QuickActionTone.Neutral -> FsColors.role.quickAction.neutral
    }
    Row(
        Modifier.fillMaxWidth().height(Sizes.quickActionRow).clip(RoundedCornerShape(Radius.control))
            .clickable(enabled = !a.locked, onClick = a.onClick).padding(horizontal = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeSurface(
            Modifier.size(Sizes.iconDisc), fill = disc, edgeColor = edge, edge = EdgeStyle.Disc, shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(a.glyph, style = FsTheme.type.text.titleMedium, color = if (a.locked) FsColors.role.content.disabled else fg)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.s3)) {
            Text(a.title, style = FsTheme.type.text.labelLarge, color = if (a.locked) FsColors.role.content.disabled else FsColors.role.content.primary)
            Text(a.subtitle, style = FsTheme.type.text.caption, color = FsColors.role.content.tertiary, maxLines = 1)
        }
        Text(if (a.locked) "Soon" else "›", style = FsTheme.type.text.labelMedium, color = FsColors.role.content.tertiary)
    }
}
