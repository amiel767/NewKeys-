package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Disquette stylisée (viewBox 750×810, stroke 68).
 * Trois chemins en outline :
 *   • corps extérieur avec coin coupé en haut-droite
 *   • volet supérieur (shutter) ouvert vers le haut
 *   • étiquette inférieure (label) remontant du bas
 */
@Composable
fun IconSaveV2(size: Dp = 20.dp, color: Color = Color(0xFFC9D1E0)) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 810f
        val offX = (this.size.width - 750f * s) / 2f
        val offY = (this.size.height - 810f * s) / 2f
        val sw = 68f * s
        withTransform({ translate(offX, offY); scale(s, s, pivot = Offset.Zero) }) {
            val body = Path().apply {
                moveTo(152f, 42f); lineTo(475f, 42f); lineTo(688f, 255f); lineTo(688f, 668f)
                arcTo(Rect(506f, 577f, 688f, 759f), 0f, 90f, false)
                lineTo(152f, 759f)
                arcTo(Rect(61f, 577f, 243f, 759f), 90f, 90f, false)
                lineTo(61f, 133f)
                arcTo(Rect(61f, 42f, 243f, 224f), 180f, 90f, false)
                close()
            }
            drawPath(body, color, style = Stroke(sw, cap = StrokeCap.Butt, join = StrokeJoin.Round))
            val shutter = Path().apply {
                moveTo(195f, 42f); lineTo(195f, 199f)
                arcTo(Rect(195f, 178f, 237f, 220f), 180f, -90f, false)
                lineTo(399f, 220f)
                arcTo(Rect(378f, 178f, 420f, 220f), 90f, -90f, false)
                lineTo(420f, 42f)
            }
            drawPath(shutter, color, style = Stroke(sw, cap = StrokeCap.Butt, join = StrokeJoin.Round))
            val label = Path().apply {
                moveTo(195f, 759f); lineTo(195f, 466f)
                arcTo(Rect(195f, 445f, 237f, 487f), 180f, 90f, false)
                lineTo(534f, 445f)
                arcTo(Rect(513f, 445f, 555f, 487f), 270f, 90f, false)
                lineTo(555f, 759f)
            }
            drawPath(label, color, style = Stroke(sw, cap = StrokeCap.Butt, join = StrokeJoin.Round))
        }
    }
}

/**
 * Clavier piano stylisé vu de dessus (viewBox 520×500, fill plein).
 * Cadre en U inversé + 3 touches noires + 3 séparateurs blancs.
 */
@Composable
fun IconPianoV2(size: Dp = 22.dp, color: Color = Color(0xFFC9D1E0)) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 520f
        val offX = (this.size.width - 520f * s) / 2f
        val offY = (this.size.height - 500f * s) / 2f
        withTransform({ translate(offX, offY); scale(s, s, pivot = Offset.Zero) }) {
            val frame = Path().apply {
                moveTo(22f, 62f); quadraticBezierTo(22f, 40f, 44f, 40f)
                lineTo(80f, 40f); lineTo(72f, 64f); lineTo(46f, 64f); lineTo(46f, 466f)
                lineTo(474f, 466f); lineTo(474f, 64f); lineTo(448f, 64f); lineTo(440f, 40f)
                lineTo(476f, 40f); quadraticBezierTo(498f, 40f, 498f, 62f)
                lineTo(498f, 468f); quadraticBezierTo(498f, 490f, 476f, 490f)
                lineTo(44f, 490f); quadraticBezierTo(22f, 490f, 22f, 468f)
                close()
            }
            drawPath(frame, color)
            listOf(
                Triple(100f, 93f, 24f),
                Triple(217f, 93f, 24f),
                Triple(333f, 94f, 24f)
            ).forEach { (x, w, r) ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 40f),
                    size = Size(w, 280f),
                    cornerRadius = CornerRadius(r)
                )
            }
            listOf(135f, 252f, 369f).forEach { x ->
                drawRect(color, topLeft = Offset(x, 343f), size = Size(22f, 125f))
            }
        }
    }
}

/**
 * Prise MIDI DIN 5 broches avec barre diagonale "Panic".
 * viewBox 288×288. Le paramètre withBar permet de l'utiliser sans
 * la barre si besoin (ex: icône "MIDI connecté" sans état panic).
 */
@Composable
fun IconDinV2(
    size: Dp = 30.dp,
    color: Color = Color.White,
    withBar: Boolean = true
) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 288f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            drawCircle(color, 90f, Offset(144f, 144f), style = Stroke(18f))
            listOf(
                Offset(91f, 141f),
                Offset(107f, 106f),
                Offset(144f, 92f),
                Offset(181f, 106f),
                Offset(197f, 141f)
            ).forEach { drawCircle(color, 14.5f, it) }
            if (withBar) {
                drawLine(
                    color,
                    Offset(30f, 30f),
                    Offset(258f, 258f),
                    strokeWidth = 22f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
