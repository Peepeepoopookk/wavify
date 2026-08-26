package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = SoftGreyCard,
    onSecondary = DarkText,
    secondaryContainer = SoftGreyCard,
    onSecondaryContainer = DarkText,
    tertiary = AccentBlue,
    onTertiary = PureWhite,
    background = PureWhite,
    onBackground = DarkText,
    surface = PureWhite,
    onSurface = DarkText,
    surfaceVariant = SoftGreyCard,
    onSurfaceVariant = SecondaryGrey,
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFD70015),
    onError = PureWhite,
    errorContainer = Color(0xFFFFE1E3),
    onErrorContainer = Color(0xFF5F1118)
)

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenContainer,
    onPrimaryContainer = SpotifyGreen,
    inversePrimary = SpotifyGreenPressed,
    secondary = SpotifyDarkSurfaceHigh,
    onSecondary = SpotifyText,
    secondaryContainer = SpotifyDarkSurfaceHigher,
    onSecondaryContainer = SpotifyText,
    tertiary = SpotifyGreen,
    onTertiary = Color.Black,
    tertiaryContainer = SpotifyGreenContainer,
    onTertiaryContainer = SpotifyGreen,
    background = AmoledBlack, // Pure black
    onBackground = SpotifyText,
    surface = AmoledBlack, // Pure black cards
    onSurface = SpotifyText,
    surfaceVariant = AmoledBlack, // Pure black variants
    onSurfaceVariant = SpotifySubtleText,
    inverseSurface = SpotifyText,
    inverseOnSurface = AmoledBlack,
    error = SpotifyError,
    onError = Color.Black,
    errorContainer = SpotifyErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    outline = WhiteOutline,
    outlineVariant = WhiteOutline,
    scrim = Color.Black
)

val AppCornerRadius = 8.dp
val OutlineWidth = 1.dp

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    accentColor: Color = AccentBlue,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customColorScheme = if (darkTheme) {
        colorScheme.copy(primary = SpotifyGreen)
    } else {
        colorScheme.copy(primary = accentColor)
    }

    MaterialTheme(
        colorScheme = customColorScheme,
        typography = Typography,
        content = content
    )
}
