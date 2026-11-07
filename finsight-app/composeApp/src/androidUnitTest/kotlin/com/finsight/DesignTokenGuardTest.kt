package com.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeps every screen on the InvesTerra design tokens. Screens (ui/screens) must not hard-code colours, sizes,
 * font sizes, shadows or elevation; use FsColors.role.*, Spacing.*, Radius.*, Sizes.*, FsTheme.type.* and the
 * components in ui/designsystem instead. If this fails, the message tells you the file and line to fix.
 */
class DesignTokenGuardTest {
    private val banned = listOf(
        Regex("""Color\(0x""") to "raw colour: use FsColors.role.*",
        Regex("""\b\d+(\.\d+)?\.dp\b""") to "raw dp: use Spacing / Radius / Sizes",
        Regex("""\b\d+(\.\d+)?\.sp\b""") to "raw sp: use FsTheme.type.*",
        Regex("""\.shadow\(|elevation\s*=|tonalElevation|shadowElevation""") to "shadows/elevation are banned (InvesTerra DD-08): use EdgeSurface",
        Regex("""Brush\.(linear|radial|vertical|horizontal)Gradient""") to "gradients are banned (DD-08)",
        Regex("""fontSize\s*=""") to "set text style via FsTheme.type.*, not fontSize",
        Regex("""Color\.(Red|Green|Blue|Black|White|Gray|Yellow)""") to "named colour: use FsColors.role.*",
    )

    @Test
    fun screensUseDesignTokensOnly() {
        val dir = File("src/commonMain/kotlin/com/finsight/ui/screens")
        val problems = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (line.trimStart().startsWith("//")) return@mapIndexedNotNull null
                banned.firstOrNull { it.first.containsMatchIn(line) }?.let { "${f.name}:${i + 1}: ${it.second}\n    ${line.trim()}" }
            }
        }.toList()
        assertTrue(problems.isEmpty(), "Design token violations:\n" + problems.joinToString("\n"))
    }
}
