package com.soundstage.mixer.instruments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * Relief for a knob whose look is already drawn (an SVG cap).
 *
 * The window already carried one hand-measured piece of realism — the mixer's
 * [com.soundstage.mixer.ui.components.FaderThumb], sampled pixel by pixel from a photo. This
 * extends the same approach to a knob: a specular on the upper left, a shadow under the lower
 * rim and a lit outer rim. Purely additive, so the cap underneath and its pointer stay visible
 * and the instrument keeps its identity while gaining a lit surface.
 */
internal fun DrawScope.drawKnobRelief() {
    val d = min(size.width, size.height)
    if (d <= 0f) return
    val r = d / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(
        Brush.radialGradient(
            0f to Color(0x59FFFFFF), 0.42f to Color(0x14FFFFFF), 1f to Color(0x00000000),
            center = Offset(cx - r * 0.36f, cy - r * 0.42f), radius = r * 1.15f,
        ),
        r, Offset(cx, cy),
    )
    drawCircle(
        Brush.radialGradient(
            0.55f to Color(0x00000000), 1f to Color(0x66000000),
            center = Offset(cx, cy), radius = r,
        ),
        r, Offset(cx, cy),
    )
    drawCircle(Color(0x1FFFFFFF), r * 0.985f, Offset(cx, cy), style = Stroke(d * 0.028f))
}
