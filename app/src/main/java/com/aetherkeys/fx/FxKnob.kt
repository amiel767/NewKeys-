package com.aetherkeys.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Un potard : l'arc de valeur, le corps en SVG, le libellé et la valeur.
 *
 * LE GLISSEMENT EST VERTICAL, et l'échelle est RELATIVE : 180 unités de course
 * pour la plage entière. Une échelle absolue (1 unité = 1 unité) rendrait les
 * petites plages — un ratio de 0 à 2 — impossibles à régler finement.
 *
 * Un double-tap remet la valeur par défaut. C'est le geste des instruments Æther
 * et le même que dans la maquette.
 */
@Composable
fun FxKnob(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    decimals: Int,
    accent: Color,
    size: Int = 46,
    onChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    val range = (max - min).takeIf { abs(it) > 1e-6f } ?: 1f
    val norm = ((value - min) / range).coerceIn(0f, 1f)
    var drag by remember { mutableFloatStateOf(0f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp)
                .pointerInput(label, min, max) {
                    detectDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { drag = 0f },
                    ) { _, delta ->
                        drag -= delta.y
                        val v = (value + drag / 180f * range).coerceIn(min, max)
                        onChange(v)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // L'arc de valeur, sous le corps.
            Canvas(Modifier.size(size.dp)) {
                val stroke = this.size.minDimension * .085f
                val inset = stroke / 2 + this.size.minDimension * .06f
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val start = 135f
                val sweep = 270f
                drawArc(
                    color = Color(0x22FFFFFF),
                    startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = start, sweepAngle = sweep * norm, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Box(Modifier.size((size * .62f).dp).fxSvg(AeFxSvg.knobBody()))
        }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), color = FxC.mute, fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp, maxLines = 1)
        Text(
            formatValue(value, decimals, unit),
            color = FxC.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

/** La valeur, arrondie au nombre de décimales du paramètre. */
private fun formatValue(v: Float, decimals: Int, unit: String): String {
    val text = when (decimals) {
        0 -> v.roundToInt().toString()
        1 -> ((v * 10f).roundToInt() / 10f).toString()
        else -> ((v * 100f).roundToInt() / 100f).toString()
    }
    return if (unit.isBlank()) text else "$text$unit"
}
