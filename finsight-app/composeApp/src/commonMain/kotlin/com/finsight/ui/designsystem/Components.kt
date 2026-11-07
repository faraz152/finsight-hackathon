package com.finsight.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Ported from InvesTerra components (C-01 creem edge, C-20 button, chips/tags, finance rows, allocation bar, states).
// RULES: no shadow(), no elevation, no gradients, no blur. Every surface is an EdgeSurface. Every money value uses
// FsTheme.type.numeric.*. Screens compose these; they do not restyle them.

/** Creem edge class: bottom thickness at rest + how far the face travels when pressed. */
@Immutable
class EdgeStyle(val bottom: Dp, val pressTravel: Dp) {
    companion object {
        val Card = EdgeStyle(Edges.bottom.card, Edges.pressTravel.card)
        val Control = EdgeStyle(Edges.bottom.control, Edges.pressTravel.control)
        val Disc = EdgeStyle(Edges.bottom.disc, 0.dp)
        val Outline = EdgeStyle(Edges.bottom.none, 0.dp)
    }
}

/**
 * C-01: 1 dp outline on every side + a thicker bottom edge in [edgeColor]. On press the face moves down and the bottom
 * edge collapses; the footprint never changes.
 */
@Composable
fun EdgeSurface(
    modifier: Modifier = Modifier,
    fill: Color = FsColors.role.fill.card,
    edgeColor: Color = FsColors.role.edge.card,
    edge: EdgeStyle = EdgeStyle.Card,
    shape: Shape = RoundedCornerShape(Radius.card),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(Spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel = if (pressed && onClick != null && enabled) edge.pressTravel else 0.dp
    Box(
        modifier
            .clip(shape)
            .background(edgeColor)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, enabled = enabled, onClick = onClick) else Modifier)
            .padding(start = Edges.outline, end = Edges.outline, top = Edges.outline + travel, bottom = edge.bottom - travel),
        // The face always fills the footprint (fixed/weighted sizes), but still wraps for chips and tags.
        propagateMinConstraints = true,
    ) {
        Column(Modifier.clip(shape).background(fill).padding(contentPadding), content = content)
    }
}

/** Standard card. */
@Composable
fun FsCard(
    modifier: Modifier = Modifier,
    tone: NoticeTone? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (fill, edge) = when (tone) {
        null -> FsColors.role.fill.card to FsColors.role.edge.card
        NoticeTone.Info -> FsColors.role.status.infoContainer to FsColors.role.edge.noticeInfo
        NoticeTone.Success -> FsColors.role.status.successContainer to FsColors.role.edge.noticeSuccess
        NoticeTone.Warning -> FsColors.role.status.warningContainer to FsColors.role.edge.noticeWarning
        NoticeTone.Error -> FsColors.role.status.errorContainer to FsColors.role.edge.noticeError
    }
    EdgeSurface(modifier.fillMaxWidth(), fill = fill, edgeColor = edge, onClick = onClick, content = content)
}

enum class NoticeTone { Info, Success, Warning, Error }

enum class FsButtonVariant { Primary, Secondary, Destructive, Text }

/** C-20: 52 dp, radius 14, control edge (1 + 3 dp), presses down 2 dp. */
@Composable
fun FsButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FsButtonVariant = FsButtonVariant.Primary,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val type = FsTheme.type.text
    if (variant == FsButtonVariant.Text) {
        Box(modifier.heightIn(min = Sizes.touchTarget).clip(RoundedCornerShape(Radius.control)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = Spacing.s3),
            contentAlignment = Alignment.Center) {
            Text(label, style = type.labelMedium, color = if (enabled) FsColors.role.content.link else FsColors.role.content.disabled)
        }
        return
    }
    val (fill, edge, fg) = when {
        !enabled -> Triple(FsColors.role.fill.disabled, FsColors.borderDefault, FsColors.role.content.disabled)
        variant == FsButtonVariant.Primary -> Triple(FsColors.role.fill.buttonPrimary, FsColors.role.edge.buttonPrimary, FsColors.role.content.onBrand)
        variant == FsButtonVariant.Secondary -> Triple(FsColors.role.fill.buttonSecondary, FsColors.role.edge.buttonSecondary, FsColors.role.content.primary)
        else -> Triple(FsColors.role.fill.buttonDestructive, FsColors.role.edge.buttonDestructive, FsColors.role.content.destructive)
    }
    EdgeSurface(
        modifier.height(if (small) Sizes.buttonHeightSmall else Sizes.buttonHeight),
        fill = fill, edgeColor = edge, edge = EdgeStyle.Control, shape = RoundedCornerShape(Radius.control),
        onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = Spacing.s4),
    ) {
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(label, style = if (small) type.labelMedium else type.labelLarge, color = fg, maxLines = 1)
        }
    }
}

enum class TagTone { Neutral, Brand, Info, Success, Warning, Error }

/** Small static label (category, source badge, kind). Outline-only edge. */
@Composable
fun FsTag(text: String, tone: TagTone = TagTone.Neutral, modifier: Modifier = Modifier) {
    val (fg, bg) = when (tone) {
        TagTone.Neutral -> FsColors.role.content.secondary to FsColors.role.fill.cardInset
        TagTone.Brand -> FsColors.brandText to FsColors.brandSubtle
        TagTone.Info -> FsColors.role.status.infoContent to FsColors.role.status.infoContainer
        TagTone.Success -> FsColors.gainText to FsColors.gainSubtle
        TagTone.Warning -> FsColors.role.status.warningContent to FsColors.role.status.warningContainer
        TagTone.Error -> FsColors.role.status.errorContent to FsColors.role.status.errorContainer
    }
    Text(text, style = FsTheme.type.text.labelSmall, color = fg, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(Radius.xs)).background(bg).padding(horizontal = Spacing.s2, vertical = 2.dp))
}

/** Selectable chip (filters, suggestions, category picker). */
@Composable
fun FsChip(text: String, selected: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EdgeSurface(
        modifier.height(Sizes.chipHeight),
        fill = if (selected) FsColors.role.fill.chipSelected else FsColors.role.fill.chipUnselected,
        edgeColor = if (selected) FsColors.role.edge.cardSelected else FsColors.borderDefault,
        edge = EdgeStyle.Outline, shape = RoundedCornerShape(Radius.full), onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.s3),
    ) {
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(text, style = FsTheme.type.text.labelSmall, color = if (selected) FsColors.brandText else FsColors.role.content.secondary, maxLines = 1)
        }
    }
}

/** Money amount with direction colour. Always tabular figures. */
@Composable
fun AmountText(text: String, credit: Boolean? = null, style: TextStyleSize = TextStyleSize.Body, modifier: Modifier = Modifier) {
    val n = FsTheme.type.numeric
    val s = when (style) { TextStyleSize.Hero -> n.hero; TextStyleSize.Large -> n.large; TextStyleSize.Title -> n.title; TextStyleSize.Body -> n.body; TextStyleSize.Label -> n.label; TextStyleSize.Caption -> n.caption }
    val c = when (credit) { true -> FsColors.role.change.upContent; false -> FsColors.role.content.primary; null -> FsColors.role.content.primary }
    Text(text, style = s, color = c, modifier = modifier, maxLines = 1)
}

enum class TextStyleSize { Hero, Large, Title, Body, Label, Caption }

/** Screen title + optional subtitle. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FsTheme.type.text.headlineMedium, color = FsColors.role.content.primary)
            subtitle?.let { Text(it, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.tertiary) }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) =
    Text(text, style = FsTheme.type.text.titleSmall, color = FsColors.role.content.primary, modifier = modifier.padding(bottom = Spacing.s2))

/** Small uppercase-style label above a figure ("THIS MONTH'S SPENDING"). */
@Composable
fun Overline(text: String) = Text(text.uppercase(), style = FsTheme.type.text.labelSmall, color = FsColors.role.content.tertiary)

/** Key–value line (InvesTerra KeyValueRow). */
@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = FsColors.role.content.primary) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary, modifier = Modifier.weight(1f))
        Text(value, style = FsTheme.type.numeric.label, color = valueColor)
    }
}

/** Horizontal share bars (category breakdown). Brand fill on a recessed track. */
@Composable
fun ShareBars(rows: List<Pair<String, Long>>, format: (Long) -> String) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        rows.forEach { (label, v) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = FsTheme.type.text.caption, color = FsColors.role.content.secondary, modifier = Modifier.width(104.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(Sizes.barHeight).clip(RoundedCornerShape(Radius.full)).background(FsColors.role.fill.cardInset)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(v.toFloat() / max).clip(RoundedCornerShape(Radius.full)).background(FsColors.brandFill))
                }
                Text(format(v), style = FsTheme.type.numeric.caption, color = FsColors.role.content.primary, modifier = Modifier.width(84.dp).padding(start = Spacing.s2), maxLines = 1)
            }
        }
    }
}

/** Confidence band dot (green / yellow / red). */
@Composable
fun BandDot(color: Color) = Box(Modifier.size(Sizes.dot).clip(RoundedCornerShape(Radius.full)).background(color))

/** Big metric tile ("Financial health 69/100"). */
@Composable
fun MetricTile(label: String, value: String, caption: String, valueColor: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    EdgeSurface(modifier, onClick = onClick) {
        Overline(label)
        Text(value, style = FsTheme.type.numeric.large, color = valueColor)
        Text(caption, style = FsTheme.type.text.caption, color = FsColors.role.content.secondary)
    }
}

/** Monospace evidence box (raw SMS, cloud payload). */
@Composable
fun CodeBox(text: String) {
    Text(text, style = FsTheme.type.text.caption.copy(fontFamily = FontFamily.Monospace), color = FsColors.role.content.secondary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.control)).background(FsColors.role.fill.cardInset).padding(Spacing.s3))
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(title, style = FsTheme.type.text.titleMedium, color = FsColors.role.content.primary)
        Text(body, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
        action?.invoke()
    }
}

/** Text input in InvesTerra style (radius 14, tertiary rest outline, brand focus). */
@Composable
fun FsTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true, onDone: (() -> Unit)? = null) {
    OutlinedTextField(
        value, onValueChange, modifier = modifier, singleLine = singleLine, maxLines = if (singleLine) 1 else 3,
        textStyle = FsTheme.type.text.bodyLarge,
        placeholder = { Text(placeholder, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.tertiary) },
        shape = RoundedCornerShape(Radius.control),
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FsColors.role.edge.inputFocus, unfocusedBorderColor = FsColors.role.edge.inputRest,
            focusedContainerColor = FsColors.role.fill.input, unfocusedContainerColor = FsColors.role.fill.input,
            cursorColor = FsColors.brandFill, focusedTextColor = FsColors.role.content.primary, unfocusedTextColor = FsColors.role.content.primary,
        ),
    )
}

/**
 * InvesTerra segmented control (recessed track, brand thumb with control edge). Used for the global AI mode switch.
 * [options] are (label, caption) pairs; [selected] is the index.
 */
@Composable
fun FsSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(Radius.control)).background(FsColors.role.fill.cardInset)
            .padding(Spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            EdgeSurface(
                Modifier.weight(1f).height(Sizes.buttonHeightSmall - Spacing.s1),
                fill = if (on) FsColors.brandFill else FsColors.role.fill.cardInset,
                edgeColor = if (on) FsColors.role.edge.buttonPrimary else FsColors.role.fill.cardInset,
                edge = if (on) EdgeStyle.Control else EdgeStyle.Outline,
                shape = RoundedCornerShape(Radius.segmented),
                onClick = { onSelect(i) },
                contentPadding = PaddingValues(horizontal = Spacing.s2),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(label, style = FsTheme.type.text.labelMedium, color = if (on) FsColors.brandOnFill else FsColors.role.content.secondary, maxLines = 1)
                }
            }
        }
    }
}
