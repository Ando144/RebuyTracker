package dev.rebuytracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta sobria: grafito con un verde tapete apagado y un toque latón.
val Ink = Color(0xFF0E1113)
val Panel = Color(0xFF161A1D)
val PanelHigh = Color(0xFF1E2327)
val PanelHighest = Color(0xFF272D32)
val Line = Color(0xFF252B30)
val Felt = Color(0xFF3E9C77)
val FeltDeep = Color(0xFF1C4A39)
val Brass = Color(0xFFC8A464)
val TextHi = Color(0xFFE9EDF0)
val TextLo = Color(0xFF8F9AA3)
val Gain = Color(0xFF4FC08D)
val Loss = Color(0xFFE26D64)

private val Scheme = darkColorScheme(
    primary = Felt,
    onPrimary = Color(0xFF04140D),
    primaryContainer = FeltDeep,
    onPrimaryContainer = Color(0xFFCDEBDD),
    secondary = Brass,
    onSecondary = Color(0xFF1A1407),
    secondaryContainer = Color(0xFF213A30),
    onSecondaryContainer = Color(0xFFCDEBDD),
    background = Ink,
    onBackground = TextHi,
    surface = Panel,
    onSurface = TextHi,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextLo,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Panel,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHighest,
    outline = Color(0xFF3A424A),
    outlineVariant = Line,
    error = Loss,
    onError = Color.White,
)

private val Base = Typography()

private val AppTypography = Base.copy(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun RebuyTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
