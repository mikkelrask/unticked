package xyz.raske.unticked

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp

val VesperBackground = Color(0xFF101010)
val VesperAccent = Color(0xFFFFC799)
private val vesper = darkColorScheme(
    background = VesperBackground, surface = Color(0xFF1C1C1C),
    onBackground = Color(0xFFEAEAEA), onSurface = Color(0xFFEAEAEA),
    primary = VesperAccent, onPrimary = Color(0xFF101010),
    secondary = Color(0xFF99FFE4), onSecondary = Color(0xFF101010),
    surfaceVariant = Color(0xFF252525), onSurfaceVariant = Color(0xFFA0A0A0),
    outline = Color(0xFF555555), outlineVariant = Color(0xFF333333),
    surfaceDim = Color(0xFF101010), surfaceBright = Color(0xFF282828),
    surfaceContainerLowest = Color(0xFF101010), surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF222222), surfaceContainerHighest = Color(0xFF282828),
    primaryContainer = Color(0xFF3D3026), onPrimaryContainer = VesperAccent,
    secondaryContainer = Color(0xFF1E3931), onSecondaryContainer = Color(0xFF99FFE4),
    error = Color(0xFFFF8080), onError = Color(0xFF101010),
)
private val terminalFont = FontFamily(Font(R.font.jetbrains_mono_regular))
private fun mono(size: Int, line: Int = size + 7) = TextStyle(fontFamily = terminalFont, fontSize = size.sp, lineHeight = line.sp)
private val type = Typography(
    headlineSmall = mono(24, 30), titleLarge = mono(20, 27), titleMedium = mono(16, 23),
    bodyLarge = mono(16, 24), bodyMedium = mono(14, 21), bodySmall = mono(13, 20),
    labelLarge = mono(14, 20), labelMedium = mono(13, 18), labelSmall = mono(12, 17),
)
@Composable fun TerminalTheme(choice: ThemeChoice = ThemeChoice.VESPER, content: @Composable () -> Unit) {
    val scheme = when (choice) { ThemeChoice.VESPER -> vesper; ThemeChoice.DARK -> darkColorScheme(); ThemeChoice.LIGHT -> lightColorScheme() }
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
