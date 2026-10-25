package com.soundstage.mixer.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Track colouring: mode, palettes, and the resolution of the 8 lane colours.
 *
 * The mixer strips never read a colour directly: they read it from the
 * [LocalTrackColors] CompositionLocal, which the mixer screen provides from the
 * persisted settings. That keeps the resolution in one place and lets the
 * "dynamic" mode animate without touching any strip code.
 */

/** How the 8 lane colours are chosen. */
enum class TrackColorMode { Palette, Random, Single }

/**
 * A named set of 8 lane colours. `id` is persisted; the colours are code so a
 * palette stays stable across versions.
 */
data class TrackPalette(
    val id: String,
    val name: String,
    val colors: List<Color>
) {
    /** Lane colour for a 1-based track id, wrapping after 8. */
    fun colorFor(trackId: Int): Color =
        colors[((trackId - 1) % colors.size + colors.size) % colors.size]
}

object TrackPalettes {
    val Default = TrackPalette(
        "default", "Default",
        listOf(
            Color(0xFFE58929), Color(0xFF36B37E), Color(0xFF3B82F6), Color(0xFF9333EA),
            Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF59E0B), Color(0xFF6366F1)
        )
    )

    /** Mirrors the reference "Rainbow" ramp: a full hue sweep, evenly spaced. */
    val Rainbow = TrackPalette(
        "rainbow", "Rainbow",
        listOf(
            Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF22C55E),
            Color(0xFF14B8A6), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899)
        )
    )

    /** Cool analogue console: teals and blues. */
    val Ocean = TrackPalette(
        "ocean", "Ocean",
        listOf(
            Color(0xFF0EA5E9), Color(0xFF06B6D4), Color(0xFF14B8A6), Color(0xFF0D9488),
            Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF22D3EE), Color(0xFF2DD4BF)
        )
    )

    /** Warm analogue console: ambers and reds. */
    val Sunset = TrackPalette(
        "sunset", "Sunset",
        listOf(
            Color(0xFFF59E0B), Color(0xFFEA580C), Color(0xFFEF4444), Color(0xFFDC2626),
            Color(0xFFF97316), Color(0xFFFB923C), Color(0xFFE11D48), Color(0xFFFBBF24)
        )
    )

    /** Low-saturation studio grey: neutral, lets the meters carry the eye. */
    val Mono = TrackPalette(
        "mono", "Mono",
        listOf(
            Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFFB0BEC5), Color(0xFF8FA0B5),
            Color(0xFFD5DEE9), Color(0xFF7E8CA0), Color(0xFFC0CBD8), Color(0xFF9AA7B8)
        )
    )

    /** Deep greens: natural, easy to tell apart from the warm palettes. */
    val Forest = TrackPalette(
        "forest", "Forest",
        listOf(
            Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFF4ADE80), Color(0xFF15803D),
            Color(0xFF34D399), Color(0xFF059669), Color(0xFF84CC16), Color(0xFF10B981)
        )
    )

    /** High-voltage neon: maximum separation on a dark panel. */
    val Neon = TrackPalette(
        "neon", "Neon",
        listOf(
            Color(0xFFFF2D95), Color(0xFF00E5FF), Color(0xFF39FF14), Color(0xFFFFD300),
            Color(0xFFB026FF), Color(0xFFFF6EC7), Color(0xFF00FFA3), Color(0xFFFF3131)
        )
    )

    /** Soft pastels: gentle lanes for long sessions. */
    val Candy = TrackPalette(
        "candy", "Candy",
        listOf(
            Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFFDFD96), Color(0xFFBAFFC9),
            Color(0xFFBAE1FF), Color(0xFFE0BBE4), Color(0xFFFFC8DD), Color(0xFFC7F9CC)
        )
    )

    /** Cool arctic blues and cyans. */
    val Ice = TrackPalette(
        "ice", "Ice",
        listOf(
            Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFF7DD3FC), Color(0xFF38BDF8),
            Color(0xFF0EA5E9), Color(0xFF0284C7), Color(0xFF67E8F9), Color(0xFFA5F3FC)
        )
    )

    /** Deep fire: dark reds up to bright amber. */
    val Ember = TrackPalette(
        "ember", "Ember",
        listOf(
            Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFFDC2626), Color(0xFFEA580C),
            Color(0xFFF59E0B), Color(0xFFB45309), Color(0xFFF97316), Color(0xFFFBBF24)
        )
    )

    /** Retro sepia: muted, warm, analogue tape feel. */
    val Vintage = TrackPalette(
        "vintage", "Vintage",
        listOf(
            Color(0xFFD9A066), Color(0xFF8C6B4F), Color(0xFFBFA58A), Color(0xFFA67C52),
            Color(0xFFC9A227), Color(0xFF6B705C), Color(0xFFA5A58D), Color(0xFFCB997E)
        )
    )

    /** All selectable palettes, in display order. */
    val all: List<TrackPalette> = listOf(
        Default, Rainbow, Ocean, Sunset, Mono,
        Forest, Neon, Candy, Ice, Ember, Vintage
    )

    fun byId(id: String): TrackPalette = all.firstOrNull { it.id == id } ?: Default
}

/**
 * The colours the mixer should paint, already resolved for the current settings.
 * [master] is the master strip; [lanes] is indexed by (trackId - 1).
 */
data class TrackColorScheme(
    val lanes: List<Color>,
    val master: Color
) {
    fun lane(trackId: Int): Color {
        if (lanes.isEmpty()) return Color(0xFF3B82F6)
        val i = ((trackId - 1) % lanes.size + lanes.size) % lanes.size
        return lanes[i]
    }
}

object TrackColorsLogic {
    /** The 8 default lane colours (the app's built-in palette). */
    val defaultLanes: List<Color> = TrackPalettes.Default.colors

    /**
     * Builds the effective scheme.
     *
     * @param mode       Palette / Random / Single
     * @param paletteId  palette used when [mode] is Palette
     * @param randomLanes  the frozen random assignment (used when [mode] is Random)
     * @param singleColor the one colour used for every lane when [mode] is Single
     */
    fun scheme(
        mode: TrackColorMode,
        paletteId: String,
        randomLanes: List<Color>,
        singleColor: Color
    ): TrackColorScheme = when (mode) {
        TrackColorMode.Palette -> {
            val p = TrackPalettes.byId(paletteId)
            TrackColorScheme(List(8) { p.colorFor(it + 1) }, MasterColor)
        }
        TrackColorMode.Random -> {
            val base = if (randomLanes.size == 8) randomLanes else defaultLanes
            TrackColorScheme(base, MasterColor)
        }
        TrackColorMode.Single -> TrackColorScheme(List(8) { singleColor }, MasterColor)
    }

    /**
     * Draws 8 distinct colours from [pool] for the random mode. Distinct by
     * construction so two lanes never look identical; falls back to repeats only
     * if the pool is smaller than 8.
     */
    fun randomLanes(pool: List<Color>, random: kotlin.random.Random): List<Color> {
        if (pool.isEmpty()) return defaultLanes
        val picked = pool.shuffled(random)
        return List(8) { picked[it % picked.size] }
    }

    /** A pleasant spread of hues, used as the random pool when no palette fits. */
    fun huePool(): List<Color> = List(12) { i -> hueColor(i * 30f) }

    /** HSV -> RGB with S/V fixed for a readable, saturated lane colour. */
    fun hueColor(hueDeg: Float, sat: Float = 0.72f, value: Float = 0.92f): Color {
        val h = ((hueDeg % 360f) + 360f) % 360f / 60f
        val c = value * sat
        val x = c * (1f - kotlin.math.abs((h % 2f) - 1f))
        val m = value - c
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }

    /**
     * One step of the very slow "dynamic" drift. The phase is a small angle added
     * to the lane's hue; the caller drives it from a low-frequency clock, so the
     * change is imperceptible from frame to frame.
     *
     * Pure Kotlin on purpose: this runs in previews and JVM tests, so it must not
     * touch android.graphics.
     */
    fun driftColor(base: Color, phaseDeg: Float): Color {
        if (phaseDeg == 0f) return base
        val (h, s, v) = rgbToHsv(base.red, base.green, base.blue)
        return hueColor(h + phaseDeg, s, v).copy(alpha = base.alpha)
    }

    /** RGB (0..1) -> HSV with h in degrees, s and v in 0..1. */
    private fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        val s = if (max == 0f) 0f else d / max
        return Triple((h + 360f) % 360f, s, max)
    }

    /** The master strip colour, unchanged by track modes. */
    val MasterColor: Color = Color(0xFF22D3EE)
}

/**
 * Current track colours, provided by the mixer screen. Defaults to the built-in
 * palette so any preview or test renders without the app.
 */
val LocalTrackColors = staticCompositionLocalOf {
    TrackColorScheme(TrackColorsLogic.defaultLanes, TrackColorsLogic.MasterColor)
}
