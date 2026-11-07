package com.finsight.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.finsight.domain.Band

/** Ported from InvesTerra "Indigo Night" dark tokens. */
object Fs {
    val canvas = Color(0xFF0D0D14)
    val surface = Color(0xFF16161F)
    val subtle = Color(0xFF1F1F2B)
    val border = Color(0xFF2A2A38)
    val text = Color(0xFFECECF4)
    val text2 = Color(0xFFA6A6BC)
    val text3 = Color(0xFF8A8AA3)
    val brand = Color(0xFF8B8CF8)
    val onBrand = Color(0xFF0D0B2E)
    val brandSubtle = Color(0xFF22214A)
    val gain = Color(0xFF4ADE9B); val gainSubtle = Color(0xFF0F2A1F)
    val loss = Color(0xFFFF7A8A); val lossSubtle = Color(0xFF301620)
    val warn = Color(0xFFFBBF4D); val warnSubtle = Color(0xFF2B2112)
    val info = Color(0xFF7FB0FF); val infoSubtle = Color(0xFF13233D)

    fun band(b: Band) = when (b) { Band.GREEN -> gain; Band.YELLOW -> warn; Band.RED -> loss }
    fun bandSubtle(b: Band) = when (b) { Band.GREEN -> gainSubtle; Band.YELLOW -> warnSubtle; Band.RED -> lossSubtle }
}

@Composable
fun FinSightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Fs.brand, onPrimary = Fs.onBrand, background = Fs.canvas, onBackground = Fs.text,
            surface = Fs.surface, onSurface = Fs.text, surfaceVariant = Fs.subtle, onSurfaceVariant = Fs.text2,
            outline = Fs.border, error = Fs.loss, secondaryContainer = Fs.brandSubtle, onSecondaryContainer = Fs.text,
        ),
        content = content,
    )
}
