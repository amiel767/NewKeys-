package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry and colours of the FX window, ported unchanged from
 * `fx-window-preview.html`: logical phone region 825x343, content 779x263 at
 * (20, 58), bottom rail from y = 214. One canvas px is one dp.
 */
object FxStyle {
    const val PWL = 825f
    const val PHL = 343f
    const val OXL = 20f
    const val OYL = 58f
    const val RAIL_Y = 214f

    private val accents = mapOf(
        0 to Color(0xFFA99AD8), 1 to Color(0xFFD4B06A), 2 to Color(0xFF8FD0FF),
        3 to Color(0xFFB8C2D4), 4 to Color(0xFFC9852C), 5 to Color(0xFFE8C98A),
        6 to Color(0xFFD9C08A), 7 to Color(0xFFE0A45A), 8 to Color(0xFFECB05C),
        9 to Color(0xFFE08A4A), 10 to Color(0xFFFF9A3C), 11 to Color(0xFFF0C060),
        12 to Color(0xFFE8C060), 13 to Color(0xFFC9B8FF), 14 to Color(0xFF7AD0FF),
    )

    fun accent(id: Int): Color = accents[id] ?: Color(0xFFA99AD8)

    /** Rail material per instrument id, from `RS` in the preview. */
    data class Rail(val bgTop: Color, val bgBottom: Color, val line: Color, val tx: Color, val pattern: String)

    /**
     * True when the instrument draws the bottom rail. `RS` in the preview has no
     * entry for 10 (amp) and 11 (bass): they carry a regular "Level" knob
     * (FxKnobBoxes) and fill the panel to the bottom, so calling `rail()` for
     * every id >= 5 drew a second, wrong LEVEL slider under those two.
     */
    fun hasRail(id: Int): Boolean = id == 5 || id == 6 || id == 7 || id == 8 ||
        id == 9 || id == 12 || id == 13 || id == 14

    /**
     * Parameter index driven by the rail's LEVEL slider, from the
     * `rail(x, v[n], id)` calls in the preview. It differs per instrument: the
     * slider is not always the last parameter (piano uses v[8], organ v[30]…).
     */
    fun railLevelIndex(id: Int): Int = when (id) {
        6 -> 30
        8 -> 9
        9 -> 10
        14 -> 31
        else -> 8
    }

    fun rail(id: Int): Rail = when (id) {
        6 -> Rail(Color(0xFF4A2A16), Color(0xFF1A0D07), Color(0xFFD9C08A), Color(0xFFF3E6CC), "wood")
        7 -> Rail(Color(0xFF2A1810), Color(0xFF0E0806), Color(0xFFE0A45A), Color(0xFFF4E4D0), "copper")
        8 -> Rail(Color(0xFF4A3A20), Color(0xFF1C1408), Color(0xFFD9A05B), Color(0xFFF0E2C8), "wood")
        9 -> Rail(Color(0xFF2A2E36), Color(0xFF0A0B0E), Color(0xFFC0C8D0), Color(0xFFDFE4EA), "steel")
        12 -> Rail(Color(0xFF4A0E1C), Color(0xFF14060A), Color(0xFFE8C060), Color(0xFFF4E4C0), "velvet")
        13 -> Rail(Color(0x22FFFFFF), Color(0x08FFFFFF), Color(0xFFC9B8FF), Color(0xFFE8E4FF), "glass")
        14 -> Rail(Color(0xFF0A1220), Color(0xFF04060C), Color(0xFF7AD0FF), Color(0xFFA8D8F0), "led")
        else -> Rail(Color(0xFF1F1F26), Color(0xFF07070A), Color(0xFFE8C98A), Color(0xFFE8E0D0), "lacquer")
    }
}

/** Knob geometry as declared by `AB(...)` in the preview, per effect id. */
data class FxKnobSpec(val param: Int, val x: Float, val y: Float, val size: Float)

private val knobLayout: Map<Int, List<FxKnobSpec>> = buildMap {
    put(0, (0..6).map { k ->
        val a = Math.toRadians(-90.0 + k * 360.0 / 7.0)
        FxKnobSpec(k, (389 + 285 * cos(a)).toFloat(), (114 + 80 * sin(a)).toFloat(), 54f)
    })
    put(1, listOf(FxKnobSpec(8, 650f, 70f, 56f), FxKnobSpec(9, 735f, 70f, 56f),
                  FxKnobSpec(10, 692f, 188f, 56f), FxKnobSpec(11, 95f, 205f, 52f)))
    put(2, listOf(FxKnobSpec(1, 66f, 126f, 100f), FxKnobSpec(3, 330f, 214f, 60f),
                  FxKnobSpec(6, 664f, 122f, 78f), FxKnobSpec(4, 430f, 216f, 44f)))
    put(3, listOf(FxKnobSpec(1, 628f, 78f, 78f), FxKnobSpec(4, 725f, 64f, 50f),
                  FxKnobSpec(2, 598f, 200f, 54f), FxKnobSpec(3, 670f, 200f, 54f),
                  FxKnobSpec(5, 742f, 200f, 54f)))
    put(4, listOf(FxKnobSpec(0, 125f, 128f, 136f), FxKnobSpec(1, 262f, 78f, 76f),
                  FxKnobSpec(2, 262f, 196f, 50f), FxKnobSpec(3, 340f, 196f, 50f),
                  FxKnobSpec(4, 340f, 70f, 50f), FxKnobSpec(5, 735f, 128f, 62f)))
    // Effects 5..14, from the `AB(id,param,x,y,z)` calls in the reference BODY table.
    // Positions are window coordinates (the -OXL/-OYL translate), sizes are the
    // knob diameter `z` — not the wider label box from fx_layout_spec.json.
    put(5, listOf(FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(6, listOf(FxKnobSpec(26, 742f, 106f, 40f), FxKnobSpec(27, 150f, 252f, 42f),
                  FxKnobSpec(28, 290f, 252f, 42f), FxKnobSpec(29, 430f, 252f, 42f)))
    put(7, listOf(FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(8, listOf(FxKnobSpec(5, 96f, 176f, 40f), FxKnobSpec(4, 212f, 176f, 40f), FxKnobSpec(3, 389f, 168f, 62f),
                  FxKnobSpec(2, 566f, 176f, 40f), FxKnobSpec(0, 680f, 176f, 40f), FxKnobSpec(1, 756f, 176f, 40f),
                  FxKnobSpec(6, 150f, 252f, 42f), FxKnobSpec(7, 290f, 252f, 42f), FxKnobSpec(8, 430f, 252f, 42f)))
    put(9, listOf(FxKnobSpec(7, 150f, 252f, 42f), FxKnobSpec(8, 290f, 252f, 42f), FxKnobSpec(9, 430f, 252f, 42f)))
    // Effect 10 (Aether Amp): AB(10,j,70+j*80,86,46) for j=0..5, plus Level (param 11)
    // at AB(10,11,730,86,46). Params 6..10 are scene hotspots, not DOM knobs.
    put(10, (0..5).map { j -> FxKnobSpec(j, 70f + j * 80f, 86f, 46f) } + FxKnobSpec(11, 730f, 86f, 46f))
    put(11, listOf(FxKnobSpec(0, 330f, 72f, 44f), FxKnobSpec(1, 400f, 72f, 44f),
                   FxKnobSpec(2, 470f, 72f, 44f), FxKnobSpec(9, 730f, 206f, 46f)))
    put(12, listOf(FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(13, listOf(FxKnobSpec(2, 70f, 56f, 44f), FxKnobSpec(3, 70f, 150f, 44f), FxKnobSpec(4, 700f, 60f, 44f),
                   FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(14, listOf(FxKnobSpec(0, 45f, 102f, 40f), FxKnobSpec(1, 125f, 102f, 40f), FxKnobSpec(2, 205f, 102f, 40f),
                   FxKnobSpec(3, 45f, 170f, 40f), FxKnobSpec(4, 125f, 170f, 40f), FxKnobSpec(5, 205f, 170f, 40f),
                   FxKnobSpec(9, 630f, 64f, 40f), FxKnobSpec(10, 700f, 64f, 40f), FxKnobSpec(6, 764f, 64f, 40f),
                   FxKnobSpec(16, 50f, 128f, 40f), FxKnobSpec(17, 130f, 128f, 40f),
                   FxKnobSpec(20, 290f, 128f, 40f), FxKnobSpec(21, 370f, 128f, 40f),
                   FxKnobSpec(25, 650f, 176f, 40f), FxKnobSpec(27, 60f, 104f, 44f), FxKnobSpec(28, 140f, 104f, 44f),
                   FxKnobSpec(29, 220f, 104f, 44f), FxKnobSpec(30, 300f, 104f, 44f),
                   FxKnobSpec(22, 150f, 252f, 42f), FxKnobSpec(23, 290f, 252f, 42f), FxKnobSpec(24, 430f, 252f, 42f)))
}

fun knobsFor(effectId: Int): List<FxKnobSpec> = knobLayout[effectId] ?: emptyList()

private const val TAU = 6.2831855f

private fun dash(a: Float, b: Float) = PathEffect.dashPathEffect(floatArrayOf(a, b))

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float,
                           color: Color, width: Float, effect: PathEffect?) {
    val path = Path()
    for (i in 0..64) {
        val a = i * TAU / 64
        val x = rx * cos(a); val y = ry * sin(a)
        val px = cx + x * cos(rot) - y * sin(rot)
        val py = cy + x * sin(rot) + y * cos(rot)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = color, style = Stroke(width = width, pathEffect = effect))
}

private fun DrawScope.label(tm: TextMeasurer, text: String, cx: Float, cy: Float,
                            color: Color, sp: Float, bold: Boolean = false,
                            align: LabelAlign = LabelAlign.CENTER) {
    // Measured at density 1 on purpose: the scene canvas is already scaled by the
    // display density (`scale(d, d)` in FxOpenPanel/RackCard), so a `sp` resolved at
    // the real density was multiplied by it a second time — every scene label came
    // out 2.75x too large on a 440dpi device. At density 1 the `sp` is 1:1 with the
    // scene's logical units, which is what the preview's `9px` means.
    val layout = tm.measure(text, style = TextStyle(color = color, fontSize = sp.sp,
        fontFamily = FxFonts.mono,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        density = Density(1f, 1f))
    val x = when (align) {
        LabelAlign.CENTER -> cx - layout.size.width / 2f
        LabelAlign.LEFT -> cx
        LabelAlign.RIGHT -> cx - layout.size.width
    }
    drawText(layout, topLeft = Offset(x, cy - layout.size.height / 2f))
}

/** `textAlign` of a canvas `fillText`. */
enum class LabelAlign { LEFT, CENTER, RIGHT }

// ---------------------------------------------------------------------------
// Scenes, ported 1:1 from the preview functions of the same name.
// ---------------------------------------------------------------------------

/**
 * Per-effect panel background, from the `.xN` rules of `fx-window-preview.html`.
 * The effects 0..4 are painted by `bg()`; the instruments (5..14) are painted by
 * their own scene functions. `.x2` is declared twice in the reference (a slate
 * grid at line 54, a red amp face at line 104) and the later rule wins in CSS:
 * ref-02-prism-tone.png samples (180,26,43), so the red face is the one to keep.
 */
internal val PANEL_BG: Map<Int, Color> = mapOf(
    0 to Color(0xFF0F1120),
    1 to Color(0xFF1F1B14),
    2 to Color(0xFF93111F),
    3 to Color(0xFF23262C),
    4 to Color(0xFFD6CDB9),
    10 to Color(0xFF0A0806),
    11 to Color(0xFF0A0806),
    12 to Color(0xFF0A0806),
    13 to Color(0xFF05040C),
    14 to Color(0xFF04060C),
)

/**
 * Per-effect text colour, from the `color` of the same `.xN` rule that gives the
 * panel its background. The port had `#DFE4EE` / `#E6EAF3` hard-coded for every
 * readout, so solar (`.x4`, background `#efe9db`) painted cream text on cream and
 * the page looked empty. `.x4` declares `#2a241b`; ids 7/8/9 have no rule of their
 * own and fall back to the `#st` colour the preview inherits from `body`.
 */
internal val PANEL_TEXT: Map<Int, Color> = mapOf(
    0 to Color(0xFFE7E3F6),
    1 to Color(0xFFF0E4C8),
    2 to Color(0xFFFFF1EA),   // the later `.x2` rule (red face) wins over the first
    3 to Color(0xFFE3E8F0),
    4 to Color(0xFF2A241B),
    5 to Color(0xFFF3E6CC),
    6 to Color(0xFFF3E6CC),
    10 to Color(0xFFF3E6CC),
    11 to Color(0xFFF3E6CC),
    12 to Color(0xFFF3E6CC),
    13 to Color(0xFFE8E4FF),
    14 to Color(0xFFE0F2FF),
)

/** Text colour of [effectId]'s panel, or the `body` colour for the ids without a rule. */
fun fxPanelText(effectId: Int): Color = PANEL_TEXT[effectId] ?: Color(0xFFE6EAF3)

/**
 * Panel background for [effectId] as the `.xN` rule declares it. Those rules are
 * gradients, not flat colours — `.x1` is `linear-gradient(#41382a,#1f1b14)` — so a
 * single colour renders too dark and flat. Top and bottom are sampled from the
 * references; the accent overlay (`.x0`, `.x2`) is left to `bg()`.
 */
internal val PANEL_BG_GRADIENT: Map<Int, Pair<Color, Color>> = mapOf(
    // `.x0` is a flat #0f1120 under two radial washes; the washes live in `bg()`
    // because a single Brush cannot stack a radial over a linear.
    0 to (Color(0xFF0F1120) to Color(0xFF0F1120)),
    1 to (Color(0xFF41382A) to Color(0xFF1F1B14)),
    2 to (Color(0xFFC4182C) to Color(0xFF5A0A14)),
    3 to (Color(0xFF3F444D) to Color(0xFF23262C)),
    4 to (Color(0xFFEFE9DB) to Color(0xFFD6CDB9)),
    // `.x5`/`.x6` are a flat #120a06.
    5 to (Color(0xFF120A06) to Color(0xFF120A06)),
    6 to (Color(0xFF120A06) to Color(0xFF120A06)),
    // 7, 8 and 9 have no `.xN` rule at all, so they keep `#win`'s own gradient.
    7 to (Color(0xFF1F2635) to Color(0xFF131925)),
    8 to (Color(0xFF1F2635) to Color(0xFF131925)),
    9 to (Color(0xFF1F2635) to Color(0xFF131925)),
    10 to (Color(0xFF0A0806) to Color(0xFF0A0806)),
    11 to (Color(0xFF0A0806) to Color(0xFF0A0806)),
    12 to (Color(0xFF0A0806) to Color(0xFF0A0806)),
    13 to (Color(0xFF05040C) to Color(0xFF05040C)),
    14 to (Color(0xFF04060C) to Color(0xFF04060C)),
)

/** Vertical gradient of the panel, or null for an unknown id (shared fallback). */
fun fxPanelGradient(effectId: Int): Pair<Color, Color>? = PANEL_BG_GRADIENT[effectId]

/** Base colour of the panel for [effectId], from its `.xN` rule; the shared
 * gradient is the fallback. The scene canvas does not cover the whole window, so
 * the host needs the same colour to paint the surrounding band. */
fun fxPanelBackground(effectId: Int): Color =
    fxPanelGradient(effectId)?.first ?: Color(0xFF171C28)

/** Panel background as a brush, used both by the host (whole window) and by the
 * scene (`bg()`), so the two agree. */
@Composable
fun fxPanelBrush(effectId: Int): Brush {
    val g = fxPanelGradient(effectId)
    return if (g != null) Brush.verticalGradient(listOf(g.first, g.second))
    else Brush.verticalGradient(listOf(Color(0xFF171C28), Color(0xFF0B0E16)))
}


/**
 * A CSS `radial-gradient(<rx> <ry> at <cx> <cy>, colour, transparent <stop>)`.
 * Compose only has circular radial gradients, so the circle is drawn with radius
 * [rx] inside a space scaled on Y by `ry/rx`: a circle of radius rx becomes an
 * ellipse with radii (rx, ry). Skipping the scale made every wash reach too far
 * sideways at mid-height (the reference ellipse is 110% wide but only 90% tall).
 */
private fun DrawScope.radialWash(
    cx: Float, cy: Float, rx: Float, ry: Float, color: Color, stop: Float,
) {
    scale(1f, ry / rx, pivot = Offset(cx, cy)) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color, stop to color.copy(alpha = 0f),
                    1f to color.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = rx,
            ),
            radius = rx, center = Offset(cx, cy),
        )
    }
}


/**
 * A CSS `linear-gradient(<deg>, ...)` over the box (x0, y0, w, h). CSS angles run
 * clockwise from "to top", so the direction is `(sin a, -cos a)` and the line is
 * `|w·sin a| + |h·cos a|` long. Hand-picking a start/end pair for 160deg put the
 * ramp almost horizontal, which washed the header of effect 2 out.
 */
private fun cssLinearGradient(
    x0: Float, y0: Float, w: Float, h: Float, deg: Float, colors: List<Color>,
): Brush {
    val a = Math.toRadians(deg.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val len = abs(w * dx) + abs(h * dy)
    val cx = x0 + w / 2f
    val cy = y0 + h / 2f
    return Brush.linearGradient(
        colors,
        start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
        end = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
    )
}

private fun DrawScope.bg(effectId: Int, v: FloatArray, t: Float, accent: Color) {
    val X0 = -FxStyle.OXL
    val Y0 = -FxStyle.OYL
    val w = FxStyle.PWL
    val h = FxStyle.PHL

    // `bg(i,x,t,v,a)` only draws for effects 0 and 1. Everything else is the `.xN`
    // CSS rule on the canvas, which covers the whole window.
    if (effectId == 0) {
        radialWash(X0 + w * 0.12f, Y0, 1.10f * w, 0.90f * h, Color(0xFF2B2C58), 0.60f)
        radialWash(X0 + w, Y0 + h, 0.90f * w, 0.70f * h, Color(0xFF1C3B4A), 0.55f)
        // The four specks are CSS `radial-gradient(1.2px 1.2px at ...)` layers, not
        // a starfield: exactly four, at fixed positions, never animated. The old
        // loop drew 70 twinkling dots for every effect, which the reference has
        // nowhere.
        listOf(
            0.12f to 0.22f to 1.2f, 0.58f to 0.12f to 1.0f,
            0.86f to 0.40f to 1.4f, 0.38f to 0.80f to 1.0f,
        ).forEach { (p, r) ->
            drawCircle(Color(0xE6FFFFFF), radius = r, center = Offset(X0 + w * p.first, Y0 + h * p.second))
        }
        // `bg(0,...)`: a soft glow around the orb, driven by the damp and the mix.
        // The reference is `hsla(268-v[3]*75,45%,30%,.18+.35*v[5])`: a dark, low
        // saturation purple, not the screen accent. Using the accent at the same
        // alpha washed the whole corner out, +12 per channel.
        val mix = v.getOrElse(5) { 0f }
        val damp = v.getOrElse(3) { 0f }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(hsl(268f - damp * 75f, 0.45f, 0.30f).copy(alpha = 0.18f + 0.35f * mix),
                    Color.Transparent),
                center = Offset(389f, 114f),
                radius = 520f,
            ),
            radius = 520f, center = Offset(389f, 114f),
        )
    }

    if (effectId == 2) {
        // `.x2` (the later rule, the red amp face) is three layers, not the flat
        // two-stop gradient the host paints: a 160deg ramp with a 46% stop, 3px
        // hairlines, and a radial highlight at 18% -10%. Missing them made the
        // panel uniformly lighter — measured (233,113,118) against the reference's
        // (200,63,77) across the whole top band.
        drawRect(brush = Brush.verticalGradient(
            listOf(Color(0xFFC4182C), Color(0xFF93111F), Color(0xFF5A0A14)),
            startY = Y0, endY = Y0 + h), topLeft = Offset(X0, Y0), size = Size(w, h))
        var hx = X0
        while (hx < X0 + w) {
            drawRect(Color(0x07FFFFFF), topLeft = Offset(hx, Y0), size = Size(1f, h))
            hx += 3f
        }
        radialWash(X0 + w * 0.18f, Y0 - h * 0.10f, 1.30f * w, 0.70f * h, Color(0x33FF8A8A), 0.60f)
    }

    if (effectId == 1) {
        // `.x1` base: `linear-gradient(#41382a,#1f1b14)` with hairline stripes.
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF41382A), Color(0xFF1F1B14)), startY = Y0, endY = Y0 + h),
            topLeft = Offset(X0, Y0), size = Size(w, h),
        )
        var x = X0
        while (x < X0 + w) {
            drawRect(Color(0x08FFFFFF), topLeft = Offset(x, Y0), size = Size(1f, h))
            x += 3f
        }
        // `bg(1,...)`: four expanding rings and two wave lines.
        val fb = 0.2f + 0.8f * v.getOrElse(8) { 0f }
        for (k in 0 until 4) {
            val u = (t * 0.22f + k / 4f) % 1f
            drawCircle(accent.copy(alpha = (1f - u) * 0.2f * fb), radius = 20f + u * 640f,
                center = Offset(95f, 80f), style = Stroke(width = 1.5f))
        }
        for (r in 0 until 2) {
            val path = Path()
            var q = X0
            var first = true
            while (q <= X0 + w) {
                val y = (if (r == 1) Y0 + h - 22f else Y0 + 20f) +
                    sin(q * 0.02f + t * (1f + v.getOrElse(10) { 0f } * 3f) + r * 2f) * (4f + v.getOrElse(10) { 0f } * 10f)
                if (first) { path.moveTo(q, y); first = false } else path.lineTo(q, y)
                q += 8f
            }
            drawPath(path, accent.copy(alpha = 0.18f), style = Stroke(width = 1f))
        }
    }

    if (effectId == 2) {
        // The second `.x2` rule wins over the first: a 160deg three-stop ramp, with
        // 1px vertical stripes on a 3px pitch over it. Drawn here rather than as the
        // host brush because a Brush cannot stack a repeating pattern on a ramp.
        drawRect(
            brush = cssLinearGradient(X0, Y0, w, h, 160f,
                listOf(Color(0xFFC4182C), Color(0xFF93111F), Color(0xFF5A0A14))),
            topLeft = Offset(X0, Y0), size = Size(w, h),
        )
        var sx = X0
        while (sx < X0 + w) {
            drawRect(Color(0x07FFFFFF), topLeft = Offset(sx, Y0), size = Size(1f, h))
            sx += 3f
        }
        radialWash(X0 + w * 0.18f, Y0 - 0.10f * h, 1.30f * w, 0.70f * h, Color(0xFFFF8A8A), 0.60f)
    }

    if (effectId == 3) {
        // `.x3`: `box-shadow:inset 0 2px 0 #ffffff22`, a 2 px highlight along the top.
        drawRect(Color(0x22FFFFFF), topLeft = Offset(X0, Y0), size = Size(w, 2f))
    }
}

private fun DrawScope.sceneOrb(v: FloatArray, a: Color, t: Float) {
    val cx = FxStyle.PWL / 2f
    val cy = 114f
    val sz = v[0]; val dc = v[1]; val pd = v[2]; val dm = v[3]; val wd = v[4]; val mx = v[5]; val df = v[6]
    val r = 40f * (0.7f + sz * 0.8f) * (1f + 0.03f * sin(t * 2f))
    val col = hsl(268f - dm * 75f, 0.48f, 0.74f)
    for (w in 0 until 3) {
        val u = (t * (0.12f + sz * 0.2f) + w / 3f) % 1f
        if (u < pd * 0.4f) continue
        drawCircle(col.copy(alpha = (1f - u) * 0.4f), radius = r * (1.1f + u * 5f),
            center = Offset(cx, cy), style = Stroke(width = 1.6f))
    }
    var rx = r * 1.75f
    oval(cx, cy, rx, rx * (0.3f + wd * 0.18f), -0.35f + sin(t * 0.25f) * wd * 0.55f,
        col.copy(alpha = 0.3f + 0.5f * mx), 2.2f, PathEffect.dashPathEffect(floatArrayOf(4f + dc * 36f, 9f), -t * 30f * (0.3f + wd)))
    rx = r * 2.7f
    val ry = rx * 0.36f
    val tl1 = 0.25f + cos(t * 0.2f) * wd * 0.4f
    val n = 22 + (sz * 14f).roundToInt()
    val l = 3 + (dc * 6f).roundToInt()
    for (p in 0 until n) {
        val an = t * (0.25f + wd * 0.9f) + p * TAU / n
        for (s in 0 until l) {
            val aa = an - s * 0.08f * (0.4f + dc)
            val wob = sin(aa * 5f + t * 3f) * df * r * 0.45f
            val x = (rx + wob) * cos(aa); val y = (ry + wob * 0.4f) * sin(aa)
            drawCircle(if (s == 0) Color.White else col, radius = 2.1f * (1f - s.toFloat() / l * 0.5f),
                center = Offset(cx + x * cos(tl1) - y * sin(tl1), cy + x * sin(tl1) + y * cos(tl1)),
                alpha = (1f - s.toFloat() / l) * 0.85f)
        }
    }
    rx = min(r * 3.7f, 250f)
    val ry2 = rx * 0.3f
    val tl2 = -0.6f + sin(t * 0.17f) * wd * 0.3f
    for (e in 0 until 3) {
        oval(cx, cy, rx * (1f + e * 0.06f), ry2 * (1f + e * 0.06f), tl2,
            col.copy(alpha = 0.05f + 0.25f * dc * (1f - e / 3f)), 1f, null)
    }
    for (c in 0 until 3) {
        val an = -t * (0.18f + wd * 0.6f) + c * 2.094f
        val t2 = 8 + (dc * 20f).roundToInt()
        for (s in 0 until t2) {
            val aa = an + s * 0.05f
            drawCircle(if (s == 0) Color.White else col, radius = 3f * (1f - s.toFloat() / t2 * 0.6f),
                center = Offset(cx + rx * cos(aa) * cos(tl2) - ry2 * sin(aa) * sin(tl2),
                                cy + rx * cos(aa) * sin(tl2) + ry2 * sin(aa) * cos(tl2)),
                alpha = (1f - s.toFloat() / t2) * (0.3f + 0.6f * mx))
        }
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFEEE8FF), a, Color(0xFF1D1A38)),
        center = Offset(cx - r * 0.35f, cy - r * 0.4f), radius = r * 1.4f),
        radius = r, center = Offset(cx, cy))
    for (q in 0 until 3) {
        oval(cx, cy, r * abs(cos(t * 0.4f * (0.5f + wd) + q * 1.05f)), r, 0f,
            Color.White.copy(alpha = 0.1f + 0.1f * df), 0.8f, null)
    }
    drawCircle(Color.White.copy(alpha = 0.08f + 0.4f * mx), radius = r * 1.08f,
        center = Offset(cx, cy), style = Stroke(width = 1f))
}

private fun DrawScope.sceneEcho(v: FloatArray, a: Color, t: Float) {
    val px0 = 190f; val pw = 400f; val py0 = 18f; val ph = 218f; val pb = py0 + ph
    val rr = 62f
    val sp = 1.7f - (v[0] + v[2] + v[4] + v[6]) / 4f * 1.3f
    // The whole wheel — gradient disc, the six ports and the hub — is drawn inside
    // the wheel's own rotation. Drawing the ports in a separate rotate block left
    // them at absolute angles while the reference spins them with the disc, which
    // was most of the 10.8/255 error on the wheel.
    val wheelGrad = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFFE8D2A0), 0.40f to Color(0xFF7D6335),
            0.70f to Color(0xFFD9BF86), 1f to Color(0xFF5F4A28)),
        start = Offset(95f - rr, 80f - rr), end = Offset(95f + rr, 80f + rr),
    )
    rotate((t * sp + sin(t * 6f) * v[10] * 0.1f) * 57.29578f, pivot = Offset(95f, 80f)) {
        drawCircle(brush = wheelGrad, radius = rr, center = Offset(95f, 80f))
        for (k in 0 until 6) {
            rotate(k * 60f, pivot = Offset(95f, 80f)) {
                drawOval(Color(0xFF14110C), topLeft = Offset(95f + rr * 0.29f, 80f - rr * 0.17f),
                    size = Size(rr * 0.52f, rr * 0.34f))
            }
        }
        // The hub reuses the disc gradient, not a flat dark fill.
        drawCircle(brush = wheelGrad, radius = rr * 0.22f, center = Offset(95f, 80f))
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent),
        center = Offset(95f - rr * 0.4f, 80f - rr * 0.45f), radius = rr * 1.2f),
        radius = rr, center = Offset(95f, 80f))
    drawRect(Color(0xFF0D0B08), topLeft = Offset(px0, py0), size = Size(pw, ph))
    for (k in 1 until 5) {
        drawRect(Color(0x12FFFFFF), topLeft = Offset(px0 + pw * k / 5f, py0), size = Size(1f, ph))
        drawRect(Color(0x12FFFFFF), topLeft = Offset(px0, py0 + ph * k / 5f), size = Size(pw, 1f))
    }
    val p = (0 until 4).map { n -> floatArrayOf(px0 + v[2 * n] * pw, pb - v[2 * n + 1] * ph) }.sortedBy { it[0] }
    val fb = 0.2f + v[8] * 0.8f
    for (q in p) drawRect(a.copy(alpha = 0.25f * fb), topLeft = Offset(q[0] - 4f, q[1]), size = Size(8f, pb - q[1]))
    val sp2 = (p[3][0] - p[0][0]) + pw * 0.1f
    for (r in 1 until 4) for (q in p) {
        val gx = q[0] + sp2 * r
        if (gx < px0 + pw) drawRect(a.copy(alpha = 0.18f * fb.pow(r + 1)),
            topLeft = Offset(gx - 3f, q[1] + (pb - q[1]) * (1f - fb.pow(r))),
            size = Size(6f, (pb - q[1]) * fb.pow(r)))
    }
    val path = Path().apply {
        moveTo(px0, pb)
        p.forEach { lineTo(it[0], it[1]) }
        lineTo(px0 + pw, pb)
    }
    drawPath(path, a.copy(alpha = 0.5f + 0.5f * fb), style = Stroke(width = 2f))
    val dx = px0 + ((t * 0.28f) % 1f) * pw
    var dy = pb
    val pts = listOf(floatArrayOf(px0, pb)) + p + listOf(floatArrayOf(px0 + pw, pb))
    for (k in 0 until pts.size - 1) {
        val aa = pts[k]; val bb = pts[k + 1]
        if (dx >= aa[0] && dx <= bb[0]) {
            dy = aa[1] + (bb[1] - aa[1]) * (dx - aa[0]) / max(1f, bb[0] - aa[0]); break
        }
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color.White, a, Color.Transparent),
        center = Offset(dx, dy), radius = 14f), radius = 14f, center = Offset(dx, dy))
    for (n in 0 until 4) {
        val x = px0 + v[2 * n] * pw
        val y = pb - v[2 * n + 1] * ph
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(0xFFFFF0C8), Color(0xFFC9A45C), Color(0xFF4A3A1C)),
            center = Offset(x - 2.7f, y - 2.7f), radius = 9f), radius = 9f, center = Offset(x, y))
    }
}

private fun DrawScope.rrect(x0: Float, y0: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    moveTo(x0 + r, y0)
    arcTo(Rect(x0 + w - r * 2, y0, x0 + w, y0 + r * 2), -90f, 90f, false)
    arcTo(Rect(x0 + w - r * 2, y0 + h - r * 2, x0 + w, y0 + h), 0f, 90f, false)
    arcTo(Rect(x0, y0 + h - r * 2, x0 + r * 2, y0 + h), 90f, 90f, false)
    arcTo(Rect(x0, y0, x0 + r * 2, y0 + r * 2), 180f, 90f, false)
    close()
}

private fun DrawScope.sceneEq(v: FloatArray, t: Float, tm: TextMeasurer) {
    val x0 = 134f; val wd = 466f; val y0 = 12f; val hh = 138f; val my = y0 + hh / 2f
    val sg = 0.05f + 0.14f / (0.3f + v[4] * 3.7f)
    fun cv(u: Float): Float =
        (v[1] - 0.5f) * 2f / (1f + exp(((u - v[0]) * 16f).toDouble())).toFloat() +
        (v[3] - 0.5f) * 2f * exp(-((u - v[2]) / sg).toDouble().pow(2.0)).toFloat() +
        (v[6] - 0.5f) * 2f / (1f + exp((-(u - v[5]) * 16f).toDouble())).toFloat()
    fun yy(u: Float) = my - cv(u) * hh * 0.4f
    drawPath(rrect(x0 - 4f, y0 - 4f, wd + 8f, hh + 8f, 16f), color = Color(0x6A000000))
    drawPath(rrect(x0, y0, wd, hh, 13f), brush = Brush.linearGradient(
        listOf(Color(0xFF1B070C), Color(0xFF0C0305)), start = Offset(0f, y0), end = Offset(0f, y0 + hh)))
    clipPath(rrect(x0, y0, wd, hh, 13f)) {
        drawRect(brush = Brush.linearGradient(
            listOf(Color(0x2AFF2D4A), Color.Transparent, Color(0x2A6FC8FF)),
            start = Offset(0f, y0), end = Offset(0f, y0 + hh)),
            topLeft = Offset(x0, y0), size = Size(wd, hh))
        for (f in floatArrayOf(0.25f, 0.75f)) drawRect(Color(0x1AFFFFFF), topLeft = Offset(x0, y0 + hh * f), size = Size(wd, 1f))
        for (f in floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f)) drawRect(Color(0x1AFFFFFF), topLeft = Offset(x0 + f * wd, y0), size = Size(1f, hh))
        drawLine(Color(0x59FFFFFF), Offset(x0, my), Offset(x0 + wd, my), 1f, pathEffect = dash(5f, 5f))
        val nb = 70
        for (k in 0 until nb) {
            val u = k.toFloat() / nb
            val h = hh * 0.3f * (0.35f + 0.65f * abs(sin(k * 0.6f + t * 2.1f) * sin(k * 0.17f + t * 0.7f))) *
                    (1f - u * 0.45f) * (0.55f + v[7] * 0.9f) * (1f + cv(u) * 0.8f)
            drawRect(if (cv(u) > 0f) Color(0xFFFF6B7E) else Color(0xFF7FD0FF),
                topLeft = Offset(x0 + u * wd, y0 + hh - max(1f, h)), size = Size(wd / nb - 1f, max(1f, h)),
                alpha = 0.24f)
        }
        fun curve(): Path = Path().apply {
            moveTo(x0, yy(0f))
            var k = 0f
            while (k <= wd) { lineTo(x0 + k, yy(k / wd)); k += 3f }
        }
        clipRect(x0, y0, x0 + wd, my) {
            val p = curve(); p.lineTo(x0 + wd, my); p.lineTo(x0, my); p.close()
            drawPath(p, brush = Brush.linearGradient(listOf(Color(0xAAFF3D58), Color(0x15FF3D58)),
                start = Offset(0f, y0), end = Offset(0f, my)))
        }
        clipRect(x0, my, x0 + wd, y0 + hh) {
            val p = curve(); p.lineTo(x0 + wd, my); p.lineTo(x0, my); p.close()
            drawPath(p, brush = Brush.linearGradient(listOf(Color(0x157FD0FF), Color(0xAA7FD0FF)),
                start = Offset(0f, my), end = Offset(0f, y0 + hh)))
        }
        translate(0f, 5f) { drawPath(curve(), Color(0x99000000), style = Stroke(width = 3.4f)) }
        clipRect(x0, y0, x0 + wd, my) { drawPath(curve(), Color(0xFFFF5468), style = Stroke(width = 3.4f)) }
        clipRect(x0, my, x0 + wd, y0 + hh) { drawPath(curve(), Color(0xFF86D6FF), style = Stroke(width = 3.4f)) }
    }
    for ((q, f) in listOf("100" to 0.3f, "1k" to 0.6f, "10k" to 0.9f)) {
        label(tm, q, x0 + f * wd, y0 + hh + 16f, Color(0xBFFFE9E2), 9f)
    }
    // Left axis: the preview draws `+12 / 0 / -12` right-aligned at x0-9. The port
    // had no vertical scale at all, so the curve's dB range was unreadable.
    for ((q, f) in listOf("+12" to 0f, "0" to 0.5f, "-12" to 1f)) {
        label(tm, q, x0 - 9f, y0 + hh * f + (if (f != 0f) 3f else 9f),
            Color(0xBFFFE9E2), 9f, align = LabelAlign.RIGHT)
    }
    // `L`/`M`/`H` inside each band dot, and the per-band readout at the top left of
    // the curve: `LOW 200 Hz +0.0 dB`. Both were missing from the port.
    for ((b, fg) in listOf(0 to (0 to 1), 1 to (2 to 3), 2 to (5 to 6))) {
        val nx = x0 + v[fg.first] * wd
        val ny = y0 + (1f - v[fg.second]) * hh
        label(tm, "LMH"[b].toString(), nx, ny + 3.2f, Color(0xFF1A0508), 9f, bold = true)
        val hz = 20f * 1000f.pow(v[fg.first])
        val gd = (v[fg.second] - 0.5f) * 24f
        val hzTxt = if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "${hz.roundToInt()} Hz"
        val gdTxt = (if (gd >= 0f) "+" else "") + "%.1f dB".format(gd)
        label(tm, "${listOf("LOW", "MID", "HIGH")[b]}  $hzTxt  $gdTxt",
            x0 + 12f, y0 + 12f, Color(0xFFFFE9E2), 10f, align = LabelAlign.LEFT)
    }
    for (b in 0 until 3) {
        val fg = listOf(0 to 1, 2 to 3, 5 to 6)[b]
        val nx = x0 + v[fg.first] * wd
        val ny = y0 + (1f - v[fg.second]) * hh
        val up = v[fg.second] >= 0.5f
        drawCircle(brush = Brush.radialGradient(
            listOf(Color.White, if (up) Color(0xFFFF6B7E) else Color(0xFF86D6FF),
                   if (up) Color(0xFF6A0B18) else Color(0xFF124A6A)),
            center = Offset(nx - 3.8f, ny - 4.4f), radius = 11f),
            radius = 11f, center = Offset(nx, ny))
    }

    // `if(!m)`: the OUT fader at the far right of the curve. The port had no fader
    // at all — `sceneEq` stopped at the band dots — so prism-tone rendered with a
    // bare right edge. Geometry and colours are the preview's `eq()` block.
    run {
        val fx = 752f; val ty = 26f; val by = 232f
        val th = ty + (1f - v[7]) * (by - ty)
        drawPath(rrect(fx - 4f, ty - 6f, 8f, by - ty + 12f, 4f), color = Color(0x66000000))
        drawPath(rrect(fx - 2f, th, 4f, by - th, 2f), brush = Brush.linearGradient(
            listOf(Color(0xFFFFE9E2), Color(0xFFFFB9A8)),
            start = Offset(0f, th), end = Offset(0f, by)))
        // The preview saves/restores a `shadowColor '#000a', shadowBlur 7,
        // shadowOffsetY 4` around the cap. The port dropped that block, so the
        // handle looked pasted on: measured under it, the reference ramps down
        // 116 -> 223 over 9 px while the capture fell straight to the 233
        // background. The scene canvas has no blur, so the penumbra is painted as
        // a band with a vertical gradient, the way `raised()` fakes it.
        // Six stops approximating a Gaussian falloff. A two-stop ramp read as a hard
        // grey bar; a blurred shadow decays fast at the edge then tails off, which
        // this curve follows (0.20, 0.18, 0.13, 0.08, 0.03, 0).
        drawPath(rrect(fx - 17f, th + 11f, 34f, 13f, 6f), brush = Brush.verticalGradient(
            listOf(Color(0x94000000), Color(0x78000000), Color(0x5C000000), Color(0x42000000),
                   Color(0x2B000000), Color(0x17000000), Color(0x08000000), Color(0x00000000)),
            startY = th + 11f, endY = th + 24f))
        drawPath(rrect(fx - 15f, th - 11f, 30f, 22f, 5f), brush = Brush.linearGradient(
            listOf(Color(0xFFF4F0EA), Color(0xFFA39B95), Color(0xFF5C5550)),
            start = Offset(0f, th - 11f), end = Offset(0f, th + 11f)))
        drawRect(Color(0xFF2A0C10), topLeft = Offset(fx - 10f, th - 1f), size = Size(20f, 2f))
        label(tm, "OUT", fx, ty - 12f, Color(0xFFFFE9E2), 9f)
        label(tm, "%.1f dB".format((v[7] - 0.5f) * 24f), fx, by + 20f, Color(0xFFFFE9E2), 9f)
    }
}

private fun DrawScope.sceneGrip(v: FloatArray, a: Color, t: Float, tm: TextMeasurer) {
    val th = v[0]
    val ratios = floatArrayOf(1.5f, 2f, 3f, 4f, 8f, 20f)
    val r = ratios[(v[1] * 5f).roundToInt().coerceIn(0, 5)]
    val l = 0.52f + 0.36f * sin(t * 2.3f) * sin(t * 0.7f + 1f) + 0.08f * sin(t * 7.1f)
    val g1 = min(1f, max(0f, l - th * 0.9f) * (1f - 1f / r) * 2.2f * 1.6f)
    val gx = 135f; val gy = 218f; val gR = 118f
    val a0 = Math.PI.toFloat() * 1.17f; val a1 = Math.PI.toFloat() * 1.83f
    drawArc(Color(0xFF1C1E24), 180f, 180f, true,
        topLeft = Offset(gx - gR - 8f, gy - gR - 8f), size = Size((gR + 8f) * 2, (gR + 8f) * 2))
    drawArc(brush = Brush.linearGradient(listOf(Color(0xFFD6D0BB), Color(0xFFA79F86)),
        start = Offset(0f, gy - gR), end = Offset(0f, gy + 8f)),
        180f, 180f, true, topLeft = Offset(gx - gR - 3f, gy - gR - 3f), size = Size((gR + 3f) * 2, (gR + 3f) * 2))
    for (k in 0..20) {
        val an = a0 + (a1 - a0) * k / 20f
        val r1 = gR * 0.84f
        val r2 = gR * (if (k % 5 != 0) 0.9f else 0.96f)
        drawLine(if (k > 15) Color(0xFFAA3333) else Color(0xFF2A2820),
            Offset(gx + r1 * cos(an), gy + r1 * sin(an)),
            Offset(gx + r2 * cos(an), gy + r2 * sin(an)),
            strokeWidth = if (k % 5 != 0) 1f else 2f)
    }
    val na = a0 + (a1 - a0) * g1
    drawLine(Color(0xFF14120E), Offset(gx, gy + 4f),
        Offset(gx + gR * 0.9f * cos(na), gy + gR * 0.9f * sin(na)), 2.6f)
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFAAB2C0), Color(0xFF2A2E38)),
        center = Offset(gx - 3f, gy - 3f), radius = 14f), radius = 13f, center = Offset(gx, gy + 2f))
    label(tm, "GAIN REDUCTION", gx, gy - gR * 0.42f, Color(0xFF2A2820), 10f, true)
    val px = 272f; val pw = 150f; val py = 20f; val ph = 223f
    val gap = ph * (1f - g1 * 0.64f)
    val pc = py + ph / 2f
    drawRect(Color(0xFF0C0D10), topLeft = Offset(px, py), size = Size(pw, ph))
    val wave = Path()
    var k = 0f
    while (k <= pw) {
        val y = pc + sin(k * 0.09f + t * 5f) * (gap / 2f) * 0.9f * (0.6f + 0.4f * sin(k * 0.03f + t))
        if (k == 0f) wave.moveTo(px + k, y) else wave.lineTo(px + k, y)
        k += 2f
    }
    drawPath(wave, a, style = Stroke(width = 2f))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFE1E6EE), Color(0xFF8A92A1), Color(0xFF4D5461)),
        start = Offset(0f, py), end = Offset(0f, py + (ph - gap) / 2f)),
        topLeft = Offset(px, py), size = Size(pw, (ph - gap) / 2f))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFE1E6EE), Color(0xFF8A92A1), Color(0xFF4D5461)),
        start = Offset(0f, py + ph - (ph - gap) / 2f), end = Offset(0f, py + ph)),
        topLeft = Offset(px, py + ph - (ph - gap) / 2f), size = Size(pw, (ph - gap) / 2f))
    for (q in listOf(Offset(px + 10f, py + 8f), Offset(px + pw - 10f, py + 8f),
                     Offset(px + 10f, py + ph - 8f), Offset(px + pw - 10f, py + ph - 8f))) {
        drawCircle(Color(0xFF2A2E38), radius = 3f, center = q)
    }
    val ty = 30f + (1f - th) * 205f
    drawRect(Color(0xFF08090C), topLeft = Offset(452f, 30f), size = Size(8f, 205f))
    drawRect(a.copy(alpha = 0.7f), topLeft = Offset(452f, ty), size = Size(8f, 235f - ty))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFDFE4EC), Color(0xFF8B93A3), Color(0xFF515866)),
        start = Offset(0f, ty - 11f), end = Offset(0f, ty + 11f)),
        topLeft = Offset(438f, ty - 11f), size = Size(36f, 22f))
    drawRect(Color(0xFF14120E), topLeft = Offset(442f, ty - 1f), size = Size(28f, 2f))
    label(tm, "THRESH ${(th * 60f - 60f).roundToInt()} dB", 456f, 252f, Color(0xFFCFD6E6), 9f, true)
    drawLine(Color(0x80E0A24A), Offset(px, pc - th * ph * 0.42f), Offset(px + pw, pc - th * ph * 0.42f),
        1f, pathEffect = dash(4f, 4f))
}

private fun DrawScope.sceneCeil(v: FloatArray, t: Float) {
    val am = v[0]; val cl = v[1]
    val sx = 390f; val sw = 300f; val sy = 20f; val sh = 223f; val my = sy + sh / 2f
    val kx = 125f; val ky = 128f
    val r0 = 50f + am * 130f
    drawCircle(brush = Brush.radialGradient(
        listOf(Color(0xFFE0A24A).copy(alpha = 0.18f + 0.3f * am), Color.Transparent),
        center = Offset(kx, ky), radius = r0), radius = r0, center = Offset(kx, ky))
    for (k in 0 until 26) {
        val an = -Math.PI.toFloat() * 0.95f + k * Math.PI.toFloat() * 1.9f / 26f + sin(t * 0.5f) * 0.05f
        val len = 70f + am * 110f * (0.6f + 0.4f * sin(t * 2f + k))
        drawLine(Color(0xFFE0A24A), Offset(kx + cos(an) * 62f, ky + sin(an) * 62f),
            Offset(kx + cos(an) * (62f + len), ky + sin(an) * (62f + len)), 1.6f,
            alpha = 0.08f + 0.28f * am)
    }
    drawRect(Color(0xFF17140F), topLeft = Offset(sx, sy), size = Size(sw, sh))
    val gn = 1f + am * 2.4f
    val cy = my - cl * sh * 0.42f
    val fy = my + cl * sh * 0.42f
    var hit = 0
    val wave = Path()
    var k = 0f
    while (k <= sw) {
        val u = k / sw
        val y0 = sin(u * 13f + t * 3f) * 0.72f + sin(u * 31f + t) * 0.2f
        val s = max(-cl, min(cl, y0 * gn))
        if (abs(y0 * gn) > cl) hit++
        if (k == 0f) wave.moveTo(sx + k, my - s * sh * 0.42f) else wave.lineTo(sx + k, my - s * sh * 0.42f)
        k += 2f
    }
    drawPath(wave, Color(0xFFE0A24A), style = Stroke(width = 2.2f))
    val hf = min(1f, hit / (sw / 2f))
    drawRect(brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFE0A24A).copy(alpha = 0.15f + 0.5f * hf)),
        start = Offset(0f, cy - 30f), end = Offset(0f, cy)), topLeft = Offset(sx, cy - 30f), size = Size(sw, 30f))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFF1DFAE), Color(0xFFB9934D), Color(0xFF6E5226)),
        start = Offset(0f, sy), end = Offset(0f, cy)), topLeft = Offset(sx, sy), size = Size(sw, max(3f, cy - sy)))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFF1DFAE), Color(0xFFB9934D), Color(0xFF6E5226)),
        start = Offset(0f, fy), end = Offset(0f, sy + sh)), topLeft = Offset(sx, fy), size = Size(sw, max(3f, sy + sh - fy)))
}

/** Bottom rail: material strip plus the Level fader. */
private fun DrawScope.rail(instrumentId: Int, level: Float, tm: TextMeasurer) {
    val r = FxStyle.rail(instrumentId)
    val x0 = -FxStyle.OXL
    val y0 = FxStyle.RAIL_Y
    val rw = FxStyle.PWL
    val rh = max(71f, FxStyle.PHL - FxStyle.OYL - FxStyle.RAIL_Y)
    drawRect(brush = Brush.linearGradient(listOf(r.bgTop, r.bgBottom),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)),
        topLeft = Offset(x0, y0), size = Size(rw, rh))
    clipRect(x0, y0, x0 + rw, y0 + rh) {
        when (r.pattern) {
            "wood" -> for (k in 0 until 8) {
                val p = Path()
                var u = 0f
                while (u <= rw) {
                    val y = y0 + 8 + k * 9 + sin(u * 0.02f + k) * 2
                    if (u == 0f) p.moveTo(x0 + u, y) else p.lineTo(x0 + u, y)
                    u += 10f
                }
                drawPath(p, Color(0x1A000000), style = Stroke(width = 1f))
            }
            "steel" -> {
                var y = 0f
                while (y < rh) { drawLine(Color(0x12FFFFFF), Offset(x0, y0 + y), Offset(x0 + rw, y0 + y), 1f); y += 2f }
                for (k in 0 until 8) drawCircle(Color(0x88000000), radius = 2.4f,
                    center = Offset(x0 + 14 + k * (rw - 28) / 7f, y0 + rh - 10f))
            }
            "velvet" -> {
                var k = -rh
                while (k < rw) { drawLine(Color(0x1F000000), Offset(x0 + k, y0), Offset(x0 + k + rh, y0 + rh), 1f); k += 7f }
            }
            "copper" -> {
                var k = 0f
                while (k < rw) { var rr = 0f
                    while (rr < rh) { drawCircle(Color(0x1AFFD0A0), radius = 1.6f, center = Offset(x0 + k + 7, y0 + rr + 7)); rr += 14f }
                    k += 14f }
            }
            "led" -> {
                var k = 0f
                while (k < rw) { drawLine(Color(0x147AD0FF), Offset(x0 + k, y0), Offset(x0 + k, y0 + rh), 1f); k += 18f }
            }
            "glass" -> drawRect(brush = Brush.linearGradient(listOf(Color(0x22FFFFFF), Color.Transparent),
                start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
        }
    }
    drawRect(brush = Brush.linearGradient(listOf(r.line.copy(alpha = 0f), r.line, r.line.copy(alpha = 0f)),
        start = Offset(x0, 0f), end = Offset(x0 + rw, 0f)),
        topLeft = Offset(x0, y0), size = Size(rw, 2f))
    val sx = 540f; val sw = 200f; val sy = 252f
    drawPath(rrect(sx - 3f, sy - 4f, sw + 6f, 8f, 4f), color = Color(0xE6000000))
    drawRect(brush = Brush.linearGradient(listOf(r.line.copy(alpha = 0.85f), Color.White),
        start = Offset(sx, 0f), end = Offset(sx + sw, 0f)),
        topLeft = Offset(sx, sy - 2f), size = Size(max(2f, sw * level), 4f))
    val tx = sx + sw * level
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFF4EFE6), Color(0xFFA8A090), Color(0xFF5C554A)),
        start = Offset(0f, sy - 11f), end = Offset(0f, sy + 11f)),
        topLeft = Offset(tx - 8f, sy - 11f), size = Size(16f, 22f))
    drawRect(Color(0xFF2A1A10), topLeft = Offset(tx - 1f, sy - 7f), size = Size(2f, 14f))
    label(tm, "LEVEL", sx, sy - 17f, r.tx, 9f)
    label(tm, "${(level * 30f - 24f)}", sx + sw, sy - 17f, r.tx, 9f)
}

// ---------------------------------------------------------------------------
// Knob
// ---------------------------------------------------------------------------

/**
 * One knob, ported from `knob()` in the preview: 270 degrees of travel from
 * -135, accent value arc, metallic cap, white indicator. Drag is relative and
 * mixed (170 px on either axis is the full course). Double tap resets to the
 * registry default.
 */
@Composable
fun FxKnob(value: Float, accent: Color, default: Float, sizeDp: Float, onValue: (Float) -> Unit) {
    var v by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableFloatStateOf(0f) }
    if (dragging == 0f && abs(v - value) > 0.0001f) v = value
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = 1f },
                    onDragEnd = { dragging = 0f },
                    onDragCancel = { dragging = 0f },
                ) { change, drag ->
                    change.consume()
                    val nv = (v + (drag.x - drag.y) / 170f).coerceIn(0f, 1f)
                    v = nv
                    onValue(nv)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    v = default
                    onValue(default)
                })
            },
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(Color(0xFF0A0C12), radius = r, center = Offset(cx, cy))
            drawCircle(Color(0xFF0A0C12), radius = r * 0.9f, center = Offset(cx, cy), style = Stroke(width = 1.6f))
            val rect = Rect(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
            drawArc(accent, -135f, 270f * v, false, topLeft = rect.topLeft, size = rect.size,
                style = Stroke(width = 1.6f, cap = StrokeCap.Round))
            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF9A9AB2), Color(0xFF25263A)),
                center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r * 0.9f),
                radius = r * 0.62f, center = Offset(cx, cy))
            val ang = Math.toRadians((-135f + 270f * v).toDouble()).toFloat()
            drawLine(Color.White, Offset(cx + r * 0.2f * cos(ang), cy + r * 0.2f * sin(ang)),
                Offset(cx + r * 0.55f * cos(ang), cy + r * 0.55f * sin(ang)), strokeWidth = 2f)
        }
    }
}

/** Draws the scene for [effectId] at the given animation time. */
fun DrawScope.drawFxScene(effectId: Int, values: FloatArray, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val accent = FxStyle.accent(effectId)
    if (effectId in 0..4) bg(effectId, values, t, accent)
    when (effectId) {
        0 -> sceneOrb(values, accent, t)
        1 -> sceneEcho(values, accent, t)
        2 -> sceneEq(values, t, tm)
        3 -> sceneGrip(values, accent, t, tm)
        4 -> sceneCeil(values, t)
        5 -> scenePiano(values, accent, t, tm, st)
        6 -> sceneOrgan(values, accent, t, tm, st)
        7 -> sceneSax(values, accent, t, tm, st)
        8 -> sceneStrings(values, accent, t, tm, st)
        9 -> sceneGuitar(values, accent, t, tm, st)
        10 -> sceneAmp(values, accent, t, tm, st)
        11 -> sceneBass(values, accent, t, tm, st)
        12 -> sceneBrass(values, accent, t, tm, st)
        13 -> sceneVoices(values, accent, t, tm, st)
        14 -> sceneSynth(values, accent, t, tm, st)
    }
}

/** Bottom rail, public entry point. */
fun DrawScope.drawFxRail(instrumentId: Int, level: Float, tm: TextMeasurer) = rail(instrumentId, level, tm)
