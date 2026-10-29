package app.unitconverter.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Additional monochrome tokens that have no direct Material 3 role.
 *
 * The chromatic values are deliberately kept out of the Material color
 * scheme: red belongs to the favorite heart, and the restrained blue
 * belongs only to the active pin affordance.
 */
data class ExtendedColors(
    val background: Color,
    val bottomBar: Color,
    val panel: Color,
    val panelBorder: Color,
    val outlineStrong: Color,
    val ink: Color,
    val onInk: Color,
    val resultSlab: Color,
    val onResultSlab: Color,
    val onResultSlabVariant: Color,
    val favoriteRed: Color,
    val pinBlue: Color,
    val pinBlueOnInk: Color,
    val disabledContent: Color,
    val hint: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        background = Color(0xFFF5F5F5),
        bottomBar = Color(0xFFFFFFFF),
        panel = Color(0xFFFFFFFF),
        panelBorder = Color(0xFFE2E2E2),
        outlineStrong = Color(0xFFC9C9C9),
        ink = Color(0xFF0A0A0A),
        onInk = Color(0xFFFFFFFF),
        resultSlab = Color(0xFF0A0A0A),
        onResultSlab = Color(0xFFFFFFFF),
        onResultSlabVariant = Color(0xFFB8B8B8),
        favoriteRed = Color(0xFFE5484D),
        pinBlue = Color(0xFF0277BD),
        pinBlueOnInk = Color(0xFF5AC8FA),
        disabledContent = Color(0xFF9A9A9A),
        hint = Color(0xFF9A9A9A)
    )
}

private val LightExtendedColors = ExtendedColors(
    background = Color(0xFFF5F5F5),
    bottomBar = Color(0xFFFFFFFF),
    panel = Color(0xFFFFFFFF),
    panelBorder = Color(0xFFE2E2E2),
    outlineStrong = Color(0xFFC9C9C9),
    ink = Color(0xFF0A0A0A),
    onInk = Color(0xFFFFFFFF),
    resultSlab = Color(0xFF0A0A0A),
    onResultSlab = Color(0xFFFFFFFF),
    onResultSlabVariant = Color(0xFFB8B8B8),
    favoriteRed = Color(0xFFE5484D),
    pinBlue = Color(0xFF0277BD),
    pinBlueOnInk = Color(0xFF5AC8FA),
    disabledContent = Color(0xFF9A9A9A),
    hint = Color(0xFF9A9A9A)
)

private val DarkExtendedColors = ExtendedColors(
    background = Color(0xFF0B0B0B),
    bottomBar = Color(0xFF0B0B0B),
    panel = Color(0xFF151515),
    panelBorder = Color(0xFF262626),
    outlineStrong = Color(0xFF5A5A5A),
    ink = Color(0xFFEDEDED),
    onInk = Color(0xFF0A0A0A),
    resultSlab = Color(0xFF1E1E1E),
    onResultSlab = Color(0xFFFFFFFF),
    onResultSlabVariant = Color(0xFF8A8A8A),
    favoriteRed = Color(0xFFFF6369),
    pinBlue = Color(0xFF5AC8FA),
    pinBlueOnInk = Color(0xFF5AC8FA),
    disabledContent = Color(0xFF6A6A6A),
    hint = Color(0xFF6A6A6A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A0A0A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color(0xFF0A0A0A),
    tertiary = Color(0xFF5A5A5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDEDED),
    onTertiaryContainer = Color(0xFF0A0A0A),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF5A5A5A),
    surfaceTint = Color(0xFF0A0A0A),
    inverseSurface = Color(0xFF0A0A0A),
    inverseOnSurface = Color(0xFFFFFFFF),
    // Errors are monochrome by design; red is reserved for favorites.
    error = Color(0xFF0A0A0A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFEDEDED),
    onErrorContainer = Color(0xFF0A0A0A),
    outline = Color(0xFFD6D6D6),
    outlineVariant = Color(0xFFE2E2E2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE3E3E3),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFE3E3E3),
    surfaceContainerHighest = Color(0xFFD6D6D6),
    surfaceContainerLow = Color(0xFFEDEDED),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDEDED),
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFFEDEDED),
    onPrimaryContainer = Color(0xFF0A0A0A),
    inversePrimary = Color(0xFF0A0A0A),
    secondary = Color(0xFFA6A6A6),
    onSecondary = Color(0xFF0B0B0B),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFA6A6A6),
    onTertiary = Color(0xFF0B0B0B),
    tertiaryContainer = Color(0xFF2A2A2A),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceTint = Color(0xFFEDEDED),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF0A0A0A),
    error = Color(0xFFEDEDED),
    onError = Color(0xFF0A0A0A),
    errorContainer = Color(0xFF2A2A2A),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF2F2F2F),
    outlineVariant = Color(0xFF262626),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF2F2F2F),
    surfaceContainerLow = Color(0xFF151515),
    surfaceContainerLowest = Color(0xFF0B0B0B)
)

private fun textStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Float = 0f
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

private val AppTypography = Typography(
    displayLarge = textStyle(48, 56, FontWeight.Medium),
    displayMedium = textStyle(48, 56, FontWeight.Medium),
    displaySmall = textStyle(44, 52, FontWeight.SemiBold),
    headlineLarge = textStyle(44, 52, FontWeight.SemiBold),
    headlineMedium = textStyle(28, 36, FontWeight.SemiBold),
    headlineSmall = textStyle(24, 32, FontWeight.SemiBold),
    titleLarge = textStyle(22, 28, FontWeight.SemiBold),
    titleMedium = textStyle(16, 24, FontWeight.SemiBold),
    titleSmall = textStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = textStyle(16, 24, FontWeight.Normal),
    bodyMedium = textStyle(14, 20, FontWeight.Normal),
    bodySmall = textStyle(12, 16, FontWeight.Normal),
    labelLarge = textStyle(14, 20, FontWeight.SemiBold),
    labelMedium = textStyle(12, 16, FontWeight.SemiBold, letterSpacing = 0.4f),
    labelSmall = textStyle(11, 16, FontWeight.SemiBold)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Application theme.
 *
 * Phase C6 uses an intentionally strict neutral ramp in both light and
 * dark mode. Dynamic color stays disabled: a colored wallpaper must not
 * be able to recolor a design whose restrained chromatic accents are
 * the favorite heart and active pin. The system font family is used as
 * the Roboto-family
 * fallback, so no font asset or dependency is added.
 */
@Composable
fun UnitConverterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
