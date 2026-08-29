package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AppTheme

private val CyberNeonColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF003844),
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = NeonCyanLight,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = NeonPurpleDark,
    onSecondaryContainer = NeonPurpleLight,
    tertiary = NeonMagenta,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = TextDim,
    outline = BorderSubtle,
    error = MuteRed,
    onError = Color.White
)

private val ObsidianGoldColorScheme = darkColorScheme(
    primary = Color(0xFFFFC247),
    onPrimary = Color(0xFF3B2A00),
    primaryContainer = Color(0xFF5E4500),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFE5A93C),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF42310A),
    onSecondaryContainer = Color(0xFFFFDE99),
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF100F0D),
    onBackground = Color(0xFFF7F2E8),
    surface = Color(0xFF1E1B15),
    onSurface = Color(0xFFF7F2E8),
    surfaceVariant = Color(0xFF17140F),
    onSurfaceVariant = Color(0xFFA89F8D),
    outline = Color(0x33FFC247),
    error = MuteRed,
    onError = Color.White
)

private val TokyoNightColorScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF0F1426),
    primaryContainer = Color(0xFF1F2F59),
    onPrimaryContainer = Color(0xFFBB9AF7),
    secondary = Color(0xFFF7768E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A1A2C),
    onSecondaryContainer = Color(0xFFFFB3C6),
    tertiary = Color(0xFF2AC3DE),
    background = Color(0xFF1A1B26),
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF24283B),
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF16161E),
    onSurfaceVariant = Color(0xFF7982A9),
    outline = Color(0x337AA2F7),
    error = Color(0xFFF7768E),
    onError = Color.White
)

private val StudioSlateColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D253D),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E4D48),
    onSecondaryContainer = Color(0xFFA7F0E8),
    tertiary = Color(0xFFB0BEC5),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E222A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF161920),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0x2A94A3B8),
    error = MuteRed,
    onError = Color.White
)

private val OledBlackColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363A),
    onPrimaryContainer = NeonCyanLight,
    secondary = NeonPurpleLight,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF261042),
    onSecondaryContainer = NeonPurpleLight,
    tertiary = SoloAmber,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0B0B0E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF888888),
    outline = Color(0x33FFFFFF),
    error = MuteRed,
    onError = Color.White
)

@Composable
fun SoundfontLiveMixerTheme(
    appTheme: AppTheme = AppTheme.CYBER_NEON,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.CYBER_NEON -> CyberNeonColorScheme
        AppTheme.OBSIDIAN_GOLD -> ObsidianGoldColorScheme
        AppTheme.TOKYO_NIGHT -> TokyoNightColorScheme
        AppTheme.STUDIO_SLATE -> StudioSlateColorScheme
        AppTheme.OLED_BLACK -> OledBlackColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SoundfontLiveMixerTheme(AppTheme.CYBER_NEON, darkTheme, dynamicColor, content)
}

