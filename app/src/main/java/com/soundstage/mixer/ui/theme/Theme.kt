package com.soundstage.mixer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.soundstage.mixer.model.AppTheme
import kotlin.math.abs

/**
 * Pure Kotlin HSV to Compose Color converter.
 * Deterministic, zero-dependency, compatible with all Android API levels.
 */
fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    val h = (hue % 360f + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

data class ThemePalettePreset(
    val id: String,
    val name: String,
    val hue: Float,
    val saturation: Float,
    val nuance: Float,
    val primaryPreview: Color,
    val secondaryPreview: Color
)

val HarmoniousPalettes = listOf(
    ThemePalettePreset("midnight_slate", "Midnight Slate (Défaut)", 220f, 0.40f, 0.45f, Color(0xFF4A5F7E), Color(0xFF36B37E)),
    ThemePalettePreset("cyber_neon", "Cyber Neon", 185f, 0.85f, 0.50f, Color(0xFF00E5FF), Color(0xFF8B5CF6)),
    ThemePalettePreset("electric_ocean", "Océan Électrique", 210f, 0.90f, 0.50f, Color(0xFF0091FF), Color(0xFF00E5FF)),
    ThemePalettePreset("dusk_purple", "Violet Crépuscule", 275f, 0.85f, 0.48f, Color(0xFFA855F7), Color(0xFFEC4899)),
    ThemePalettePreset("analog_amber", "Ambre Analogique", 38f, 0.95f, 0.52f, Color(0xFFFFB300), Color(0xFFFF6D00)),
    ThemePalettePreset("studio_emerald", "Émeraude Studio", 155f, 0.85f, 0.46f, Color(0xFF10B981), Color(0xFF06B6D4)),
    ThemePalettePreset("synthwave_ruby", "Rubis Synthwave", 345f, 0.90f, 0.48f, Color(0xFFF43F5E), Color(0xFF9333EA)),
    ThemePalettePreset("pure_titanium", "Titane Pur", 215f, 0.15f, 0.50f, Color(0xFFCBD5E1), Color(0xFF64748B))
)

/**
 * Dynamically computes a natural, high-end Sunday Sound studio color scheme from spectral parameters.
 * Anchors the background and channel cards to clean studio slate-navy, eliminating muddy brown and pitch black.
 */
fun generateDynamicColorScheme(
    hue: Float,
    saturation: Float,
    nuance: Float,
    accentSaturation: Float = 1.0f,
    bgSaturation: Float = 1.0f,
    bgLightness: Float = 1.0f,
    modulesLightness: Float = 1.0f
): ColorScheme {
    val normSat = (saturation * accentSaturation).coerceIn(0.05f, 1.2f)
    val normNuance = nuance.coerceIn(0.1f, 0.9f)

    // Dynamic vivid accent colors matching user-selected hue
    val primaryColor = hsvToColor(hue, (normSat * 0.75f).coerceIn(0.10f, 1f), 0.92f)
    val secondaryColor = hsvToColor((hue + 40f) % 360f, (normSat * 0.70f).coerceIn(0.10f, 1f), 0.86f)
    val tertiaryColor = hsvToColor((hue + 140f) % 360f, (normSat * 0.65f).coerceIn(0.10f, 1f), 0.84f)

    // Cool Sunday Sound Studio Slate base hue (222° slate-navy ensures zero brown/sepia tint)
    val slateBaseHue = 222f
    val subtleHueBlend = (slateBaseHue * 0.85f + (hue % 360f) * 0.15f)

    // Deep natural studio slate background (#181C27: clearly separated, never pitch black)
    val bgVal = ((0.14f + (normNuance - 0.5f) * 0.03f) * bgLightness).coerceIn(0.09f, 0.22f)
    val bgSat = (0.12f * bgSaturation).coerceIn(0.03f, 0.20f)
    val bgColor = hsvToColor(subtleHueBlend, bgSat, bgVal)

    // Smooth slate channel cards & racks (#22293A: distinct contrast against background)
    val surfVal = ((0.21f + (normNuance - 0.5f) * 0.04f) * modulesLightness).coerceIn(0.15f, 0.32f)
    val surfSat = (0.14f * bgSaturation).coerceIn(0.03f, 0.22f)
    val surfaceColor = hsvToColor(subtleHueBlend, surfSat, surfVal)

    val surfVarVal = ((0.25f + (normNuance - 0.5f) * 0.05f) * modulesLightness).coerceIn(0.18f, 0.36f)
    val surfaceVariantColor = hsvToColor(subtleHueBlend, surfSat, surfVarVal)
    
    // Crisp studio hardware border (#2E374D)
    val outlineVal = (0.30f * modulesLightness).coerceIn(0.20f, 0.42f)
    val outlineColor = hsvToColor(subtleHueBlend, (0.16f * bgSaturation).coerceIn(0.04f, 0.24f), outlineVal)

    return darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.Black,
        primaryContainer = hsvToColor(hue, (normSat * 0.50f).coerceIn(0.05f, 1f), 0.30f),
        onPrimaryContainer = Color.White,
        secondary = secondaryColor,
        onSecondary = Color.Black,
        secondaryContainer = hsvToColor((hue + 40f) % 360f, (normSat * 0.45f).coerceIn(0.05f, 1f), 0.28f),
        onSecondaryContainer = Color.White,
        tertiary = tertiaryColor,
        background = bgColor,
        onBackground = Color(0xFFF1F5F9),
        surface = surfaceColor,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = surfaceVariantColor,
        onSurfaceVariant = Color(0xFF8C96A8),
        outline = outlineColor,
        error = MuteRed,
        onError = Color.White
    )
}

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

@Composable
fun SoundfontLiveMixerTheme(
    appTheme: AppTheme = AppTheme.CYBER_NEON,
    hue: Float = 220f,
    saturation: Float = 0.85f,
    nuance: Float = 0.50f,
    accentSaturation: Float = 1.0f,
    bgSaturation: Float = 1.0f,
    bgLightness: Float = 1.0f,
    modulesLightness: Float = 1.0f,
    useCustomDynamicTheme: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useCustomDynamicTheme) {
        generateDynamicColorScheme(
            hue = hue,
            saturation = saturation,
            nuance = nuance,
            accentSaturation = accentSaturation,
            bgSaturation = bgSaturation,
            bgLightness = bgLightness,
            modulesLightness = modulesLightness
        )
    } else {
        when (appTheme) {
            AppTheme.CYBER_NEON -> CyberNeonColorScheme
            else -> generateDynamicColorScheme(
                hue = hue,
                saturation = saturation,
                nuance = nuance,
                accentSaturation = accentSaturation,
                bgSaturation = bgSaturation,
                bgLightness = bgLightness,
                modulesLightness = modulesLightness
            )
        }
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
    SoundfontLiveMixerTheme(AppTheme.CYBER_NEON, darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
