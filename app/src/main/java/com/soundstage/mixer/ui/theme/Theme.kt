package com.soundstage.mixer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.soundstage.mixer.model.AppTheme

private val CyberVioletColorScheme = darkColorScheme(
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

private val RubyVelvetColorScheme = darkColorScheme(
    primary = Color(0xFFF43F5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF881337),
    onPrimaryContainer = Color(0xFFFFE4E6),
    secondary = Color(0xFFFB7185),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4C0519),
    onSecondaryContainer = Color(0xFFFECDD3),
    tertiary = Color(0xFFFF7A8A),
    background = Color(0xFF28070F),
    onBackground = Color(0xFFFFF1F2),
    surface = Color(0xFF3F0B18),
    onSurface = Color(0xFFFFF1F2),
    surfaceVariant = Color(0xFF1F040B),
    onSurfaceVariant = Color(0xFFFDA4AF),
    outline = Color(0x33F43F5E),
    error = Color(0xFFFF5252),
    onError = Color.White
)

private val NeonAmberColorScheme = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF3B2A00),
    primaryContainer = Color(0xFF5E4500),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFFFA000),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF42310A),
    onSecondaryContainer = Color(0xFFFFDE99),
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF140F04),
    onBackground = Color(0xFFFFF8E1),
    surface = Color(0xFF241A06),
    onSurface = Color(0xFFFFF8E1),
    surfaceVariant = Color(0xFF191205),
    onSurfaceVariant = Color(0xFFFFE082),
    outline = Color(0x33FFB300),
    error = MuteRed,
    onError = Color.White
)

private val EmeraldSynthColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF34D399),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF047857),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF061A14),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0F2D24),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF05130E),
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0x3310B981),
    error = MuteRed,
    onError = Color.White
)

private val DeepOceanColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF075985),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF7DD3FC),
    background = Color(0xFF0A192F),
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF112240),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF071224),
    onSurfaceVariant = Color(0xFFBAE6FD),
    outline = Color(0x3338BDF8),
    error = MuteRed,
    onError = Color.White
)

@Composable
fun SoundfontLiveMixerTheme(
    appTheme: AppTheme = AppTheme.CYBER_VIOLET,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.CYBER_VIOLET -> CyberVioletColorScheme
        AppTheme.RUBY_VELVET -> RubyVelvetColorScheme
        AppTheme.NEON_AMBER -> NeonAmberColorScheme
        AppTheme.EMERALD_SYNTH -> EmeraldSynthColorScheme
        AppTheme.DEEP_OCEAN -> DeepOceanColorScheme
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
    SoundfontLiveMixerTheme(AppTheme.CYBER_VIOLET, darkTheme, dynamicColor, content)
}

