package com.aetherkeys.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Skirt shape of a knob, from `KSH` in the reference. */
enum class KnobStyle {
    FLUTE, KNURL, CHROME, GLASS, FLAT, ROUND;

    companion object {
        /** `KSH={6:'flute',10:'knurl',11:'chrome',13:'glass',14:'flat'}`. */
        fun forEffect(id: Int): KnobStyle = when (id) {
            6 -> FLUTE; 10 -> KNURL; 11 -> CHROME; 13 -> GLASS; 14 -> FLAT; else -> ROUND
        }
    }
}


/**
 * `.kn label` and `.vl` in the preview are re-typed per effect: the base rule is
 * `700 9px 'Plus Jakarta Sans'` / `700 11px`, but `.x2 .kn label{font-family:'Share
 * Tech Mono';font-weight:400}`, `.x10 .kn label{font-family:'Orbitron'}`, `.x6
 * .vl{font-size:12px}`, and so on override both the family and the weight. Hard-coding
 * Plus Jakarta Sans everywhere is what made the readouts look out of proportion and
 * what clipped the `E` off the amp's `PRESENCE` (Orbitron is far wider at 9 px).
 */
data class ReadoutFont(val family: androidx.compose.ui.text.font.FontFamily, val weight: FontWeight, val size: Float)

private val READOUT_FONTS: Map<Int, ReadoutFont> = mapOf(
    // Effect 0 (space) had no entry, so it took the 11 sp default. Its readouts
    // read a touch large against the reference, so they are set to 10 sp.
    0 to ReadoutFont(FxFonts.ui, FontWeight.Bold, 10f),
    2 to ReadoutFont(FxFonts.mono, FontWeight.Normal, 11f),
    4 to ReadoutFont(FxFonts.ui, FontWeight.Bold, 10f),
    5 to ReadoutFont(FxFonts.sora, FontWeight.Bold, 11f),
    6 to ReadoutFont(FxFonts.mono, FontWeight.Normal, 12f),
    7 to ReadoutFont(FxFonts.sora, FontWeight.Bold, 11f),
    8 to ReadoutFont(FxFonts.spaceGrotesk, FontWeight.Bold, 11f),
    9 to ReadoutFont(FxFonts.jetBrainsMono, FontWeight.Bold, 11f),
    10 to ReadoutFont(FxFonts.orbitron, FontWeight.Bold, 11f),
    11 to ReadoutFont(FxFonts.sora, FontWeight.Bold, 11f),
    12 to ReadoutFont(FxFonts.spaceGrotesk, FontWeight.Bold, 11f),
    13 to ReadoutFont(FxFonts.spaceGrotesk, FontWeight.Light, 11f),
    14 to ReadoutFont(FxFonts.mono, FontWeight.Normal, 12f),
)

/** The label is the same face as the value, but the base weight is 700 9px. */
private fun labelFont(id: Int): ReadoutFont {
    val f = READOUT_FONTS[id] ?: return ReadoutFont(FxFonts.ui, FontWeight.Bold, LABEL_SIZE)
    return f.copy(size = LABEL_SIZE, weight = if (f.family == FxFonts.mono) FontWeight.Normal else FontWeight.Bold)
}

/** Base label size is 9px; EQ and solar read a step large against their bodies. */
private const val LABEL_SIZE = 9f
private val SMALL_LABELS = setOf(2, 4)

/** `LIGHT=[1,3,4,8,10,11,12,13]`: dark knob bodies, so the pointer turns dark too. */
private val DARK_BODIES = setOf(1, 3, 4, 8, 10, 11, 12, 13)

/** Cap gradients `c0..c14` from the reference `<defs>`; true = linear, false = radial. */
private val CAP_GRADIENTS: Map<Int, Pair<Boolean, List<Color>>> = mapOf(
    0 to (false to listOf(Color(0xFF5B5A7C), Color(0xFF23233A))),
    1 to (true to listOf(Color(0xFFE3CC96), Color(0xFF8D7240), Color(0xFFEED9A6), Color(0xFF7A6234), Color(0xFFCDB27A))),
    2 to (false to listOf(Color(0xFF4A6678), Color(0xFF17252F))),
    3 to (true to listOf(Color(0xFFD8DEE8), Color(0xFF8A92A1), Color(0xFFEEF2F7), Color(0xFF737B89))),
    4 to (false to listOf(Color(0xFFFAF3E2), Color(0xFFC9BB9A))),
    5 to (false to listOf(Color(0xFFF6E6BD), Color(0xFFB08A4A))),
    6 to (false to listOf(Color(0xFF7A5238), Color(0xFF2A1A10))),
    7 to (false to listOf(Color(0xFF55555C), Color(0xFF0A0A0C))),
    8 to (true to listOf(Color(0xFFF4F6FA), Color(0xFF9AA2B0), Color(0xFFE6EAF2), Color(0xFF7C8492), Color(0xFFDFE4EC))),
    9 to (false to listOf(Color(0xFF1E2C44), Color(0xFF080D18))),
    10 to (false to listOf(Color(0xFFFFFFFF), Color(0xFFEFE6DC), Color(0xFFB8A998))),
    11 to (true to listOf(Color(0xFFE2BD86), Color(0xFFB98A52), Color(0xFF8A6034))),
    12 to (true to listOf(Color(0xFFEEF1F5), Color(0xFF9AA1AB), Color(0xFFD6DBE2), Color(0xFF6C737D))),
    13 to (true to listOf(Color(0xFFF4D878), Color(0xFFB8902A), Color(0xFFF0CF6C), Color(0xFF8A6A1E))),
    14 to (false to listOf(Color(0x66FFFFFF), Color(0x22C9B8FF))),
)

/**
 * Knob with the reference look (`knob()` in fx-window-preview.html): 270 degree
 * track from -135, accent value arc, body gradient, dashed skirt, glossy highlight
 * and a marker rotated with the value. Vertical drag: 110 px = full range.
 */
@Composable
fun FxKnob(
    label: String,
    value: Float,
    accent: Color,
    diameter: Dp,
    valueText: String,
    style: KnobStyle,
    light: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gradientIndex: Int = 0,
    effectId: Int = -1,
) {
    // `.kn` is a column with `label` first, so the name sits above the body and the
    // readout below. Kept out of the flow here: a real column would push the body
    // down by the label's height and break the `AB()` centre the geometry relies on.
    Box(modifier = modifier.size(diameter)) {
        // The gesture key must NOT be `value`: every drag sample calls onChange(),
        // which changes the key and restarts pointerInput, so detectDragGestures is
        // cancelled on the first move and the knob looks dead to the touch. The drag
        // instead captures the value it started from and applies the total delta.
        // rememberUpdatedState so the gesture reads the latest value without being
        // keyed on it (a changing key would cancel the drag mid-gesture).
        val latest = androidx.compose.runtime.rememberUpdatedState(value)
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(effectId, label) {
                    var start = 0f
                    detectDragGestures(
                        onDragStart = { start = latest.value },
                    ) { change, drag ->
                        change.consume()
                        start = (start - drag.y / 110f).coerceIn(0f, 1f)
                        onChange(start)
                    }
                },
        ) {
            drawKnob(value, accent, style, gradientIndex, light)
        }
        val lf = labelFont(effectId).let { if (effectId in SMALL_LABELS) it.copy(size = 8f) else it }
        val vf = READOUT_FONTS[effectId] ?: ReadoutFont(FxFonts.ui, FontWeight.Bold, 11f)
        // `.xN` sets the text colour per effect; the readouts inherited a hard-coded
        // light grey, which is invisible on the light panels (solar `.x4`).
        val ink = fxPanelText(effectId)
        // The label and the readout hug the body in the reference, which reads as
        // cramped once the knob sits on a busy scene. A few dp of breathing room on
        // each side separates the text from the dial without moving the body itself:
        // the body stays on its `AB()` centre, only the text steps away.
        val labelGap = 18.dp
        val valueGap = 14.dp
        // The box is the knob diameter (46 dp on the amp), but a label like
        // `PRESENCE` in Orbitron 9 sp is wider than that. A plain Text is clipped to
        // the parent's width, which cut the final `E` off; `unbounded` lets the text
        // measure at its natural width and then be centred on the knob, which is the
        // `translate(-50%,-50%)` the reference does.
        Text(label.uppercase(), fontSize = lf.size.sp, fontFamily = lf.family,
            fontWeight = lf.weight, color = ink.copy(alpha = 0.65f),
            softWrap = false, maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = -labelGap)
                .wrapContentWidth(unbounded = true))
        Text(valueText, fontSize = vf.size.sp, fontFamily = vf.family, fontWeight = vf.weight,
            color = ink, softWrap = false, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = valueGap)
                .wrapContentWidth(unbounded = true))
    }
}

/**
 * A small, read-only knob for the rack cards: same body, arc and pointer as the
 * full-size [FxKnob], without the drag handling or the label/value readouts. The
 * reference's slot shows the effect's controls under the preview; this is that row.
 */
@Composable
fun FxMiniKnob(
    value: Float,
    accent: Color,
    diameter: Dp,
    style: KnobStyle,
    gradientIndex: Int,
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(diameter)) {
        drawKnob(value, accent, style, gradientIndex, light)
    }
}

private fun DrawScope.drawKnob(value: Float, accent: Color, style: KnobStyle, kk: Int, light: Boolean) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val a = 270f * value
    val flat = style == KnobStyle.FLAT
    val trackW = if (flat) r * 0.10f else r * 0.07f
    val ar = r * 0.92f

    // track (full 270) then value arc, both from -135 (Compose 0 is 3 o'clock).
    drawArc(Color(0x1CFFFFFF), startAngle = -135f, sweepAngle = 270f, useCenter = false,
        style = Stroke(width = trackW, cap = StrokeCap.Round),
        topLeft = Offset(c.x - ar, c.y - ar), size = Size(ar * 2, ar * 2))
    if (a > 0.1f) {
        drawArc(accent, startAngle = -135f, sweepAngle = a, useCenter = false,
            style = Stroke(width = trackW, cap = StrokeCap.Round),
            topLeft = Offset(c.x - ar, c.y - ar), size = Size(ar * 2, ar * 2))
    }

    // `.kb{filter:drop-shadow(0 7px 7px #000a)}`: the body sits on a soft shadow.
    // Drawn here rather than with `Modifier.shadow` so it also shows up in the
    // Roborazzi capture, which does not render platform elevation.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x7A000000), Color(0x00000000)),
            center = Offset(c.x, c.y + r * 0.42f),
            radius = r * 0.98f,
        ),
        topLeft = Offset(c.x - r * 0.92f, c.y + r * 0.10f),
        size = Size(r * 1.84f, r * 0.80f),
    )

    // `dark=LIGHT.includes(kk)`: the reference keys the dark bodies off the cap
    // material index `kk`, not the effect id. Effect 9 (guitar) maps to kk 12 via
    // SKN, which is not in LIGHT, so its pointer stays light.
    val dark = light || kk in DARK_BODIES
    val pointer = when {
        kk == 6 -> Color(0xFFF2EAD4)
        kk == 7 || kk == 9 -> accent
        kk == 14 -> Color.White
        kk == 4 || dark -> Color(0xFF1D1B16)
        else -> Color(0xFFF2F4FA)
    }

    when (style) {
        KnobStyle.FLAT -> {
            drawCircle(capBrush(kk), radius = r * 0.66f, center = c)
            drawCircle(accent.copy(alpha = 0.5f), radius = r * 0.66f, center = c, style = Stroke(width = r * 0.024f))
            drawCircle(Color(0x14FFFFFF), radius = r * 0.52f, center = c, style = Stroke(width = r * 0.02f))
            val mp = markerPoint(c, r, a, 0.42f)
            drawCircle(accent.copy(alpha = 0.25f), radius = r * 0.12f, center = mp)
            drawCircle(accent, radius = r * 0.068f, center = mp)
        }
        KnobStyle.GLASS -> {
            drawCircle(Color(0x10FFFFFF), radius = r * 0.70f, center = c)
            drawCircle(Color(0x40FFFFFF), radius = r * 0.70f, center = c, style = Stroke(width = r * 0.024f))
            drawCircle(capBrush(kk), radius = r * 0.56f, center = c)
            drawCircle(accent.copy(alpha = 0.7f), radius = r * 0.56f, center = c, style = Stroke(width = r * 0.02f))
            drawHighlight(c, r)
            markerBar(c, r, a, Color.White)
        }
        else -> {
            drawCircle(Brush.linearGradient(listOf(Color(0xFF2A2D41), Color(0xFF0C0E17))),
                radius = r * 0.76f, center = c)
            drawSkirt(c, r, style)
            val capR = if (style == KnobStyle.CHROME) r * 0.60f else r * 0.54f
            drawCircle(capBrush(kk), radius = capR, center = c)
            drawCircle(Color(0x38FFFFFF), radius = capR, center = c, style = Stroke(width = r * 0.016f))
            if (style == KnobStyle.CHROME) {
                drawCircle(Color(0x59FFFFFF), radius = r * 0.38f, center = c, style = Stroke(width = r * 0.016f))
                drawCircle(Color(0x40000000), radius = r * 0.24f, center = c, style = Stroke(width = r * 0.016f))
            } else if (style == KnobStyle.KNURL) {
                drawCircle(accent.copy(alpha = 0.6f), radius = r * 0.54f, center = c, style = Stroke(width = r * 0.024f))
            }
            drawHighlight(c, r)
            markerBar(c, r, a, pointer)
        }
    }
}

private fun capBrush(kk: Int): Brush {
    val (linear, colors) = CAP_GRADIENTS[kk] ?: (false to listOf(Color(0xFF5B5A7C), Color(0xFF23233A)))
    return if (linear) Brush.linearGradient(colors) else Brush.radialGradient(colors)
}

private fun DrawScope.drawSkirt(c: Offset, r: Float, style: KnobStyle) {
    val dashes = when (style) {
        KnobStyle.KNURL -> floatArrayOf(r * 0.02f, r * 0.026f)
        KnobStyle.FLUTE -> floatArrayOf(r * 0.08f, r * 0.048f)
        else -> floatArrayOf(r * 0.03f, r * 0.044f)
    }
    val w = if (style == KnobStyle.ROUND) r * 0.10f else r * 0.12f
    drawCircle(Color(0xC0000000), radius = r * 0.72f, center = c,
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(dashes)))
}

/**
 * Soft top-left sheen. The reference's `hl` gradient is `#ffffffa8 -> transparent`
 * with `opacity:.5`, i.e. a gentle wash, not a hard blob; the old values (alpha
 * .4 on an already-opaque white) read as a painted-on oval.
 */
private fun DrawScope.drawHighlight(c: Offset, r: Float) {
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0xB3FFFFFF), Color(0x00FFFFFF)),
            center = Offset(c.x - r * 0.18f, c.y - r * 0.30f), radius = r * 0.62f),
        topLeft = Offset(c.x - r * 0.54f, c.y - r * 0.50f),
        size = Size(r * 0.70f, r * 0.34f),
        alpha = 0.26f,
    )
    // Faint bottom bounce: keeps the cap inside its skirt instead of floating.
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF)),
            center = Offset(c.x, c.y + r * 0.34f), radius = r * 0.44f),
        topLeft = Offset(c.x - r * 0.46f, c.y + r * 0.06f),
        size = Size(r * 0.92f, r * 0.44f),
        alpha = 0.5f,
    )
}

private fun markerPoint(c: Offset, r: Float, a: Float, dist: Float): Offset {
    val th = (a - 135f) * PI.toFloat() / 180f
    return Offset(c.x + r * dist * sin(th), c.y - r * dist * cos(th))
}

private fun DrawScope.markerBar(c: Offset, r: Float, a: Float, color: Color) {
    rotate(degrees = a - 135f, pivot = c) {
        drawRoundRect(
            color = color,
            topLeft = Offset(c.x - r * 0.032f, c.y - r * 0.48f),
            size = Size(r * 0.064f, r * 0.30f),
            cornerRadius = CornerRadius(r * 0.032f),
        )
    }
}
