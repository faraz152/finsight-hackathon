package com.finsight.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.finsight.resources.Res
import com.finsight.resources.manrope_bold
import com.finsight.resources.manrope_extrabold
import com.finsight.resources.manrope_medium
import com.finsight.resources.manrope_regular
import com.finsight.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

// Ported from InvesTerra `theme/Typography.kt` (DD-02: Manrope everywhere; every figure uses tabular numbers).

/** Word styles (`typography.latin.*`). */
@Immutable
class TextRamp(
    val headlineLarge: TextStyle, val headlineMedium: TextStyle, val titleLarge: TextStyle, val titleMedium: TextStyle,
    val titleSmall: TextStyle, val bodyLarge: TextStyle, val bodyMedium: TextStyle, val labelLarge: TextStyle,
    val labelMedium: TextStyle, val labelSmall: TextStyle, val caption: TextStyle,
)

/** Figure styles (`typography.numeric.*`): tabular, so amounts never jump width. Use for EVERY money value. */
@Immutable
class NumericRamp(val hero: TextStyle, val large: TextStyle, val title: TextStyle, val body: TextStyle, val label: TextStyle, val caption: TextStyle)

@Immutable
class FsTypography(val text: TextRamp, val numeric: NumericRamp)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(f: FontFamily, size: Int, lh: Float, w: Int, ls: Float, tabular: Boolean = false) = TextStyle(
    fontFamily = f, fontSize = size.sp, lineHeight = (size * lh).sp, fontWeight = FontWeight(w), letterSpacing = ls.sp,
    fontFeatureSettings = if (tabular) "tnum" else null, lineHeightStyle = Centered,
)

@Composable
private fun manrope() = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal), Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold), Font(Res.font.manrope_bold, FontWeight.Bold),
    Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun buildTypography(): FsTypography {
    val f = manrope()
    return FsTypography(
        text = TextRamp(
            headlineLarge = style(f, 28, 1.2857f, 700, -0.2f), headlineMedium = style(f, 24, 1.3333f, 700, 0f),
            titleLarge = style(f, 20, 1.4f, 700, 0f), titleMedium = style(f, 18, 1.4444f, 600, 0f), titleSmall = style(f, 16, 1.5f, 600, 0f),
            bodyLarge = style(f, 16, 1.5f, 400, 0f), bodyMedium = style(f, 14, 1.4286f, 400, 0f),
            labelLarge = style(f, 16, 1.5f, 600, 0f), labelMedium = style(f, 14, 1.4286f, 600, 0f), labelSmall = style(f, 12, 1.3333f, 600, 0.2f),
            caption = style(f, 12, 1.3333f, 500, 0f),
        ),
        numeric = NumericRamp(
            hero = style(f, 32, 1.25f, 700, -0.5f, true), large = style(f, 28, 1.2857f, 700, -0.2f, true),
            title = style(f, 20, 1.4f, 700, 0f, true), body = style(f, 16, 1.5f, 600, 0f, true),
            label = style(f, 14, 1.4286f, 600, 0f, true), caption = style(f, 12, 1.3333f, 500, 0f, true),
        ),
    )
}

private val LocalFsTypography = staticCompositionLocalOf<FsTypography> { error("Wrap the UI in FsTheme { }") }

/** Access tokens: `FsTheme.type.text.titleLarge`, `FsTheme.type.numeric.hero`, `FsColors.role.fill.card`, `Spacing.s4`. */
object FsTheme {
    val type: FsTypography @Composable get() = LocalFsTypography.current
    val colors: FsColors get() = FsColors
}

@Composable
fun FsTheme(content: @Composable () -> Unit) {
    val t = buildTypography()
    CompositionLocalProvider(LocalFsTypography provides t) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = FsColors.brandFill, onPrimary = FsColors.brandOnFill,
                background = FsColors.canvas, onBackground = FsColors.textPrimary,
                surface = FsColors.surface, onSurface = FsColors.textPrimary,
                surfaceVariant = FsColors.subtle, onSurfaceVariant = FsColors.textSecondary,
                surfaceContainer = FsColors.surface, surfaceContainerHigh = FsColors.surface, surfaceContainerLow = FsColors.surface,
                outline = FsColors.borderDefault, outlineVariant = FsColors.borderDefault,
                error = FsColors.lossText, secondaryContainer = FsColors.brandSubtle, onSecondaryContainer = FsColors.textPrimary,
                scrim = FsColors.scrim,
            ),
            // Material components inherit Manrope and our sizes.
            typography = Typography(
                displayLarge = t.numeric.hero, displayMedium = t.numeric.large, displaySmall = t.text.headlineLarge,
                headlineLarge = t.text.headlineLarge, headlineMedium = t.text.headlineMedium, headlineSmall = t.text.titleLarge,
                titleLarge = t.text.titleLarge, titleMedium = t.text.titleMedium, titleSmall = t.text.titleSmall,
                bodyLarge = t.text.bodyLarge, bodyMedium = t.text.bodyMedium, bodySmall = t.text.caption,
                labelLarge = t.text.labelLarge, labelMedium = t.text.labelMedium, labelSmall = t.text.labelSmall,
            ),
            content = content,
        )
    }
}
