package com.soundstage.mixer.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 6 Material You styles inspired by Android / ColorBlendr:
 * - Vibrant
 * - Arc-en-ciel
 * - Expressif
 * - Fidèle
 * - Contenu
 * - Salade de fruits
 */
enum class MaterialYouStyle(
    val id: String,
    val title: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral1: Color,
    val neutral2: Color,
    val quadrantColors: List<Color>,
    val trackColors: List<Color>
) {
    VIBRANT(
        id = "vibrant",
        title = "Vibrant",
        description = "Combinaison de couleurs énergique et dynamique qui dégage vivacité et intensité.",
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF818CF8),
        tertiary = Color(0xFFC084FC),
        neutral1 = Color(0xFF94A3B8),
        neutral2 = Color(0xFF475569),
        quadrantColors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFFF472B6)),
        trackColors = listOf(
            Color(0xFFE09F3E), // 1. Gold/Amber
            Color(0xFF84CC16), // 2. Lime
            Color(0xFF10B981), // 3. Mint
            Color(0xFF06B6D4), // 4. Cyan
            Color(0xFF3B82F6), // 5. Blue
            Color(0xFF8B5CF6), // 6. Violet
            Color(0xFFEC4899), // 7. Pink
            Color(0xFFF97316)  // 8. Coral
        )
    ),
    RAINBOW(
        id = "rainbow",
        title = "Arc-en-ciel",
        description = "Diverses gammes de couleurs, symbolisant la diversité, le jeu et un large éventail de possibilités.",
        primary = Color(0xFFFF6B8B),
        secondary = Color(0xFF38BDF8),
        tertiary = Color(0xFFFBBF24),
        neutral1 = Color(0xFFCBD5E1),
        neutral2 = Color(0xFF64748B),
        quadrantColors = listOf(Color(0xFFFF7A8A), Color(0xFF60A5FA), Color(0xFFFBBF24), Color(0xFF34D399)),
        trackColors = listOf(
            Color(0xFFEF4444),
            Color(0xFFF97316),
            Color(0xFFFBBF24),
            Color(0xFF10B981),
            Color(0xFF06B6D4),
            Color(0xFF3B82F6),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899)
        )
    ),
    EXPRESSIVE(
        id = "expressive",
        title = "Expressif",
        description = "Mélange de couleurs audacieux et expressif qui transmet l'émotion et la créativité.",
        primary = Color(0xFF4ADE80),
        secondary = Color(0xFFE879F9),
        tertiary = Color(0xFFF472B6),
        neutral1 = Color(0xFFA3E635),
        neutral2 = Color(0xFF701A75),
        quadrantColors = listOf(Color(0xFF4ADE80), Color(0xFFE879F9), Color(0xFFF472B6), Color(0xFF818CF8)),
        trackColors = listOf(
            Color(0xFF22C55E),
            Color(0xFFA855F7),
            Color(0xFFEC4899),
            Color(0xFF14B8A6),
            Color(0xFFF59E0B),
            Color(0xFF6366F1),
            Color(0xFF06B6D4),
            Color(0xFFF43F5E)
        )
    ),
    FIDELITY(
        id = "fidelity",
        title = "Fidèle",
        description = "Représentation fidèle des couleurs, assurant précision et authenticité dans la représentation visuelle.",
        primary = Color(0xFFFB923C),
        secondary = Color(0xFF60A5FA),
        tertiary = Color(0xFFA78BFA),
        neutral1 = Color(0xFFE2E8F0),
        neutral2 = Color(0xFF475569),
        quadrantColors = listOf(Color(0xFFFB923C), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFF94A3B8)),
        trackColors = listOf(
            Color(0xFFEA580C),
            Color(0xFF2563EB),
            Color(0xFF7C3AED),
            Color(0xFF059669),
            Color(0xFFD97706),
            Color(0xFF4F46E5),
            Color(0xFFDB2777),
            Color(0xFF0284C7)
        )
    ),
    CONTENT(
        id = "content",
        title = "Contenu",
        description = "Palette équilibrée et apaisante, favorisant un sentiment de tranquillité et de détente.",
        primary = Color(0xFFE2B4BD),
        secondary = Color(0xFFC7D2FE),
        tertiary = Color(0xFFA7F3D0),
        neutral1 = Color(0xFF9CA3AF),
        neutral2 = Color(0xFF374151),
        quadrantColors = listOf(Color(0xFFE2B4BD), Color(0xFFC7D2FE), Color(0xFFA7F3D0), Color(0xFFFED7AA)),
        trackColors = listOf(
            Color(0xFFDDA2AF),
            Color(0xFFA7F3D0),
            Color(0xFFBFDBFE),
            Color(0xFFFDE68A),
            Color(0xFFDDD6FE),
            Color(0xFFBAE6FD),
            Color(0xFFFECDD3),
            Color(0xFFFED7AA)
        )
    ),
    FRUIT_SALAD(
        id = "fruit_salad",
        title = "Salade de fruits",
        description = "Mélange éclectique de couleurs rappelant un assortiment vibrant de fruits frais et juteux.",
        primary = Color(0xFF00ADB5),
        secondary = Color(0xFFFF6584),
        tertiary = Color(0xFFFFB830),
        neutral1 = Color(0xFFEEEEEE),
        neutral2 = Color(0xFF393E46),
        quadrantColors = listOf(Color(0xFF00ADB5), Color(0xFFFF6584), Color(0xFFFFB830), Color(0xFF38EF7D)),
        trackColors = listOf(
            Color(0xFFFFB830), // Mangue
            Color(0xFF38EF7D), // Kiwi
            Color(0xFF00ADB5), // Lagon
            Color(0xFFFF6584), // Pastèque
            Color(0xFF9B51E0), // Myrtille
            Color(0xFFFF7675), // Pamplemousse
            Color(0xFFFDCB6E), // Ananas
            Color(0xFF00CEC9)  // Menthe d'eau
        )
    )
}

/**
 * Universal runtime palette representation for the entire app.
 */
data class DynamicPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val neutral1: Color,
    val neutral2: Color,
    val trackColors: List<Color>
)

/**
 * Adjust saturation and lightness via HSL color space.
 */
fun Color.adjustSaturationAndLightness(satMultiplier: Float, lightMultiplier: Float): Color {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var l = (max + min) / 2f
    var s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
    var h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta) % 6f
        max == g -> ((b - r) / delta) + 2f
        else -> ((r - g) / delta) + 4f
    } * 60f
    if (h < 0f) h += 360f

    val finalS = (s * satMultiplier).coerceIn(0f, 1f)
    val finalL = (l * lightMultiplier).coerceIn(0f, 1f)

    return hslToComposeColor(h, finalS, finalL, alpha)
}

fun hslToComposeColor(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = a
    )
}

/**
 * Computes a coherent dynamic palette based on the selected MaterialYouStyle
 * and the 3 user multipliers from the ColorBlendr interface:
 * - accentSaturation (0.50x to 2.00x)
 * - backgroundSaturation (0.50x to 2.00x)
 * - backgroundBrightness (0.60x to 1.40x)
 */
fun computeDynamicPalette(
    style: MaterialYouStyle,
    accentSaturation: Float = 1.0f,
    backgroundSaturation: Float = 1.0f,
    backgroundBrightness: Float = 1.0f
): DynamicPalette {
    val adjPrimary = style.primary.adjustSaturationAndLightness(accentSaturation, 1.0f)
    val adjSecondary = style.secondary.adjustSaturationAndLightness(accentSaturation, 1.0f)
    val adjTertiary = style.tertiary.adjustSaturationAndLightness(accentSaturation, 1.0f)

    // Base dark canvas colors matching mixer_material_you.svg
    val baseBg = Color(0xFF13131C).adjustSaturationAndLightness(backgroundSaturation, backgroundBrightness)
    val baseSurface = Color(0xFF1E202C).adjustSaturationAndLightness(backgroundSaturation, backgroundBrightness)
    val baseSurfaceVariant = Color(0xFF282A3A).adjustSaturationAndLightness(backgroundSaturation, backgroundBrightness)
    val baseSurfaceContainer = Color(0xFF191B26).adjustSaturationAndLightness(backgroundSaturation, backgroundBrightness)

    val adjTrackColors = style.trackColors.map {
        it.adjustSaturationAndLightness(accentSaturation, 1.0f)
    }

    return DynamicPalette(
        primary = adjPrimary,
        onPrimary = Color(0xFF0F172A),
        primaryContainer = adjPrimary.copy(alpha = 0.25f),
        onPrimaryContainer = Color.White,
        secondary = adjSecondary,
        onSecondary = Color(0xFF0F172A),
        secondaryContainer = adjSecondary.copy(alpha = 0.25f),
        onSecondaryContainer = Color.White,
        tertiary = adjTertiary,
        onTertiary = Color(0xFF0F172A),
        background = baseBg,
        onBackground = Color(0xFFF1F5F9),
        surface = baseSurface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = baseSurfaceVariant,
        onSurfaceVariant = Color(0xFF94A3B8),
        surfaceContainer = baseSurfaceContainer,
        outline = Color(0xFF33384F),
        outlineVariant = Color(0xFF252A3D),
        neutral1 = style.neutral1.adjustSaturationAndLightness(backgroundSaturation, backgroundBrightness),
        neutral2 = style.neutral2.adjustSaturationAndLightness(backgroundSaturation, backgroundBrightness),
        trackColors = adjTrackColors
    )
}

val LocalDynamicPalette = staticCompositionLocalOf {
    computeDynamicPalette(MaterialYouStyle.VIBRANT, 1.0f, 1.0f, 1.0f)
}
