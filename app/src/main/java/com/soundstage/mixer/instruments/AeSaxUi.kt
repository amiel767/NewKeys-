package com.soundstage.mixer.instruments

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val GOLD = Color(0xFFE8B66A)

private fun fitRect(aspect: Float, size: Size): Rect {
    val w = min(size.width, size.height * aspect); val h = w / aspect
    return Rect((size.width - w) / 2f, (size.height - h) / 2f, (size.width + w) / 2f, (size.height + h) / 2f)
}

/** Floating dust in the studio light, 1000 x 460 units. */
private fun DrawScope.drawDust(a: AeAnim) = scale(size.width / 1000f, Offset.Zero) {
    for (i in 0 until 22) {
        val an = i * 2.399f + a.t * 0.02f * (1 + i % 3)
        val x = 500f + cos(an) * (200f + i * 11f) + sin(a.t * 0.3f + i) * 8f
        val yy = 60f + ((i * 53f + a.t * 6f * (1 + i % 3)) % 330f)
        drawCircle(Color(1f, 0.78f, 0.51f, 0.05f + 0.04f * sin(a.t + i)), 1.4f + (i % 3) * 0.6f, Offset(x, yy))
    }
}

/** Output LEDs, 360 x 28 units. */
private fun DrawScope.drawSaxVu(a: AeAnim) = scale(size.width / 360f, Offset.Zero) {
    val n = (min(1f, a.lv * 5f) * 24f).roundToInt()
    for (i in 0 until 24) drawRect(if (i < n) (if (i > 19) Color(0xFFFF6A4A) else if (i > 15) Color(0xFFF2B84A) else Color(0xFFE8CF94)) else Color(0xFF2A1A0C), Offset(i * 15f, 4f), Size(11f, 20f))
}

/**
 * The saxophone: the supplied photo (background removed, retouched), recoloured per finish and cross-faded.
 * Lit pearls follow the fingering (higher pitch = fewer keys closed), a specular band sweeps over the metal only,
 * and warm breath rises from the bell with the output level.
 */
@Composable
private fun AeSaxHero(c: AeCtl, a: AeAnim, x: Number, y: Number, w: Number, h: Number) {
    val ctx = LocalContext.current
    val type = (c.v(0) * 3).roundToInt().coerceIn(0, 3); val fin = (c.v(1) * 3).roundToInt().coerceIn(0, 3)
    val geo = AeSaxGeo.of(type)
    Box(Modifier.at(x, y, w, h)) {
        Canvas(Modifier.fillMaxSize()) {
            a.frame.intValue
            val r = fitRect(geo.aspect, size); val l = a.orbLevel()
            drawCircle(Brush.radialGradient(listOf(Color(1f, 0.70f, 0.36f, 0.10f + 0.4f * l), Color(1f, 0.70f, 0.36f, 0f)), r.center, r.height * 0.75f), r.height * 0.75f, r.center)
        }
        Crossfade(targetState = type * 4 + fin, animationSpec = tween(450), label = "sax") { k ->
            val bmp = remember(k) { AeSaxBitmaps.get(ctx, k / 4, k % 4) }
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                a.frame.intValue
                if (bmp != null) {
                    val r = fitRect(bmp.width.toFloat() / bmp.height, size)
                    drawImage(bmp, dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()), filterQuality = FilterQuality.High)
                    val sx = r.left + ((a.t * 0.25f) % 1.4f - 0.2f) * r.width
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x40FFFFFF), Color.Transparent), sx - r.width * 0.12f, sx + r.width * 0.12f), Offset(r.left, r.top), Size(r.width, r.height), blendMode = BlendMode.SrcAtop)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            a.frame.intValue
            val r = fitRect(geo.aspect, size)
            val k = if (c.s.held.isEmpty()) 0 else ((72 - c.s.lastNote) / 2.4f).roundToInt().coerceIn(0, geo.pearls.size)
            for (i in 0 until k) {
                val p = geo.pearls[i]; val ctr = Offset(r.left + p.first * r.width, r.top + p.second * r.height); val rad = r.width * 0.05f
                drawCircle(Brush.radialGradient(listOf(Color(0xCCFFD58A), Color(0x00FFD58A)), ctr, rad), rad, ctr, blendMode = BlendMode.Plus)
            }
            a.parts.forEach { p ->
                val ctr = Offset(r.left + p[0] * r.width, r.top + p[1] * r.height); val rad = p[5] * r.width * 2f
                drawCircle(Brush.radialGradient(listOf(Color(1f, 0.84f, 0.55f, (p[4] * 0.16f).coerceIn(0f, 1f)), Color(1f, 0.84f, 0.55f, 0f)), ctr, rad), rad, ctr, blendMode = BlendMode.Plus)
            }
        }
    }
}

/** Breath saxophone: instrument, finish, mouthpiece, reed, 16 expression controls. Layout of the validated preview. */
@Composable
fun BoxScope.AeSaxUi(c: AeCtl, a: AeAnim) {
    val border = Color(0x2AF2B84A)
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF4A2C12), Color(0xFF1C1008), Color(0xFF070403)), radius = 800f)))
    Canvas(Modifier.fillMaxSize()) { a.frame.intValue; drawDust(a) }
    AeSaxHero(c, a, 340, 40, 320, 310)
    AeCardBox(22, 54, 300, 286, "", GOLD, border)
    listOf("INSTRUMENT" to 64, "FINISH" to 110, "MOUTHPIECE" to 156, "REED" to 202).forEach { (t, yy) -> AeT(t, 9, GOLD, Modifier.at(38, yy, 200, 12), AeFonts.plate, FontWeight.ExtraBold, 0.2f) }
    arrayOf("SOPRANO", "ALTO", "TENOR", "BARI").forEachIndexed { i, t -> AeChip(c, 0, i, 4, t, 36 + i * 68, 80, 64) }
    arrayOf("GOLD", "BLACK", "SILVER", "COPPER").forEachIndexed { i, t -> AeChip(c, 1, i, 4, t, 36 + i * 68, 126, 64) }
    arrayOf("JAZZ", "CLASSIC", "RUBBER").forEachIndexed { i, t -> AeChip(c, 2, i, 3, t, 36 + i * 90, 172, 86) }
    arrayOf("SOFT", "MEDIUM", "HARD").forEachIndexed { i, t -> AeChip(c, 3, i, 3, t, 36 + i * 90, 218, 86) }
    // The meter stops short of the Blend knob: its hit box starts at x=185, so 140 leaves a gap
    // while keeping the full 24 bars (the draw scales to whatever width it is given).
    Canvas(Modifier.at(38, 266, 140, 14)) { a.frame.intValue; drawSaxVu(a) }
    AeT("OUTPUT", 9, GOLD, Modifier.at(38, 286, 100, 12), AeFonts.plate, FontWeight.ExtraBold, 0.2f)
    AeGlassKnob(c, 20, "Level", 262, 250, 42)
    // Blend mixes the sampled pack under the synthesis (parameter 22). It sits beside Level
    // because it is a voice-level setting, not an articulation: how much of the sound comes
    // from the recording rather than the model.
    AeGlassKnob(c, 22, "Blend", 196, 250, 42)
    // Humanize sits with OUTPUT rather than in the expression grid: it is not an articulation
    // setting but the player model itself (per-note spread of pitch, attack, level and vibrato),
    // and it reads as the counterpart of Level — how loud, and how human.
    AeIntensitySlider(c, 21, 36, 310, 286, GOLD)
    AeCardBox(678, 54, 300, 286, "EXPRESSION · ARTICULATION · ROOM", GOLD, border, AeFonts.plate)
    listOf(4 to "Breath", 5 to "Growl", 6 to "Subtone", 7 to "Grit", 8 to "Attack", 9 to "Click", 10 to "Scoop", 11 to "Fall",
        12 to "V.Depth", 13 to "V.Rate", 14 to "V.Delay", 15 to "Legato", 16 to "Room", 17 to "Size", 18 to "Warm", 19 to "Bright")
        .forEachIndexed { i, (k, l) -> AeGlassKnob(c, k, l, 704 + (i % 4) * 72, 78 + (i / 4) * 66, 36) }
    AeKeyboard(c, 52, 360, 896, 90)
}
