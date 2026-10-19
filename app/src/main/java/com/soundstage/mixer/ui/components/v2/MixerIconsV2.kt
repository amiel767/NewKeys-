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
 * Floppy / save icon, traced from the JSX `Ico n="save"` path on a 24×24 viewBox:
 * body `M5 4h11l3 3v13H5z`, shutter `M8 4v5h7V4`, label `M8 20v-6h8v6`.
 *
 * The default stroke is a touch heavier than the JSX's 2 (2.2/24) because the
 * button sits on the darkest part of the bottom bar and the 2.0 weight read as
 * almost invisible at 19 dp.
 */
@Composable
fun IconSaveV2(size: Dp = 20.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val sw = 2.2f * s
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            val body = Path().apply {
                moveTo(5f, 4f); lineTo(16f, 4f); lineTo(19f, 7f); lineTo(19f, 20f); lineTo(5f, 20f)
                close()
            }
            drawPath(body, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val shutter = Path().apply {
                moveTo(8f, 4f); lineTo(8f, 9f); lineTo(15f, 9f); lineTo(15f, 4f)
            }
            drawPath(shutter, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val label = Path().apply {
                moveTo(8f, 20f); lineTo(8f, 14f); lineTo(16f, 14f); lineTo(16f, 20f)
            }
            drawPath(label, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Icône Piano (inchangée) */
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
            listOf(Triple(100f, 93f, 24f), Triple(217f, 93f, 24f), Triple(333f, 94f, 24f))
                .forEach { (x, w, r) -> drawRoundRect(color, topLeft = Offset(x, 40f), size = Size(w, 280f), cornerRadius = CornerRadius(r)) }
            listOf(135f, 252f, 369f).forEach { x -> drawRect(color, topLeft = Offset(x, 343f), size = Size(22f, 125f)) }
        }
    }
}

/** Icône DIN / Panic (barre raccourcie) */
@Composable
fun IconDinV2(size: Dp = 30.dp, color: Color = Color.White, withBar: Boolean = true) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 288f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            drawCircle(color, 90f, Offset(144f, 144f), style = Stroke(18f))
            listOf(
                Offset(91f, 141f), Offset(107f, 106f), Offset(144f, 92f), Offset(181f, 106f), Offset(197f, 141f)
            ).forEach { drawCircle(color, 14.5f, it) }
            if (withBar) {
                // Barre réduite pour ne pas déborder du cercle (au lieu de 30 à 258)
                drawLine(color, Offset(60f, 60f), Offset(228f, 228f), strokeWidth = 22f, cap = StrokeCap.Round)
            }
        }
    }
}

/** NOUVEAU: Icône Patches (Dossier) */
@Composable
fun IconFolderV2(size: Dp = 20.dp, color: Color = Color(0xFFC9D1E0)) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.25f)
            lineTo(w * 0.4f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.35f)
            lineTo(w * 0.9f, h * 0.35f)
            lineTo(w * 0.9f, h * 0.85f)
            lineTo(w * 0.1f, h * 0.85f)
            close()
        }
        drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

/** NOUVEAU: Icône Settings (Engrenage) */
@Composable
fun IconGearV2(size: Dp = 20.dp, color: Color = Color(0xFFC9D1E0)) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val center = Offset(w / 2f, h / 2f)
        val outer = w * 0.4f
        val inner = w * 0.25f
        
        drawCircle(color, inner, center, style = Stroke(stroke))
        
        val teeth = 8
        for (i in 0 until teeth) {
            val angle = (i * 360.0 / teeth) * (Math.PI / 180.0)
            val startX = center.x + ((inner + stroke) * Math.cos(angle)).toFloat()
            val startY = center.y + ((inner + stroke) * Math.sin(angle)).toFloat()
            val endX = center.x + (outer * Math.cos(angle)).toFloat()
            val endY = center.y + (outer * Math.sin(angle)).toFloat()
            drawLine(color, Offset(startX, startY), Offset(endX, endY), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * Layer icon: three stacked, offset plates seen in perspective, the top one lit.
 * Drawn as filled slabs with a darker seam between them so the stack reads as
 * depth rather than as three unrelated bars.
 */
@Composable
fun IconLayerV2(size: Dp = 20.dp, color: Color = Color(0xFFC9D1E0)) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Parallelogram slab: top edge shorter than the bottom one, giving the tilt.
        fun slab(cy: Float, alpha: Float) {
            val halfTop = w * 0.30f
            val halfBot = w * 0.40f
            val th = h * 0.20f
            val cx = w / 2f
            val p = Path().apply {
                moveTo(cx - halfTop, cy - th / 2f)
                lineTo(cx + halfTop, cy - th / 2f)
                lineTo(cx + halfBot, cy + th / 2f)
                lineTo(cx - halfBot, cy + th / 2f)
                close()
            }
            drawPath(p, color.copy(alpha = alpha))
            // Seam highlight along the top edge of the slab.
            drawLine(
                color = Color.White.copy(alpha = 0.22f * alpha),
                start = Offset(cx - halfTop + w * 0.04f, cy - th / 2f + h * 0.015f),
                end = Offset(cx + halfTop - w * 0.04f, cy - th / 2f + h * 0.015f),
                strokeWidth = h * 0.045f, cap = StrokeCap.Round
            )
        }
        slab(h * 0.72f, 0.42f)
        slab(h * 0.50f, 0.68f)
        slab(h * 0.28f, 1f)
    }
}

/**
 * Patches / Scenes icon: two stacked patch cards with slot lines, matching the
 * original mixer top bar drawing. A folder glyph never matched the JSX, which
 * describes a card with a foot, so this restores the pre-V2 look.
 */
@Composable
fun IconPatchesV2(size: Dp = 20.dp, color: Color = Color(0xFF9BC0F8)) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Back card, faded: the stack reads as depth.
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.18f, h * 0.02f),
            size = Size(w * 0.72f, h * 0.50f),
            cornerRadius = CornerRadius(2.5.dp.toPx())
        )
        // Front card.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.02f, h * 0.32f),
            size = Size(w * 0.84f, h * 0.66f),
            cornerRadius = CornerRadius(2.5.dp.toPx())
        )
        // Slot lines cut into the front card.
        val slot = Color(0xFF141926)
        drawLine(slot, Offset(w * 0.16f, h * 0.52f), Offset(w * 0.72f, h * 0.52f), 1.3.dp.toPx(), StrokeCap.Round)
        drawLine(slot, Offset(w * 0.16f, h * 0.70f), Offset(w * 0.72f, h * 0.70f), 1.3.dp.toPx(), StrokeCap.Round)
        drawLine(slot, Offset(w * 0.16f, h * 0.86f), Offset(w * 0.55f, h * 0.86f), 1.3.dp.toPx(), StrokeCap.Round)
    }
}

/**
 * USB MIDI icon: the USB trident (shaft, arrow head, branch, circle foot and the
 * square plug). Replaces the Material `Icons.Default.Usb` so it matches the
 * stroke weight of the other mixer icons.
 */
@Composable
fun IconUsbV2(size: Dp = 20.dp, color: Color = Color(0xFFC9D1E0)) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.7.dp.toPx()
        fun px(fx: Float) = w * fx
        fun py(fy: Float) = h * fy
        // Shaft.
        drawLine(color, Offset(px(0.5f), py(0.90f)), Offset(px(0.5f), py(0.22f)), sw, StrokeCap.Round)
        // Arrow head.
        val arrow = Path().apply {
            moveTo(px(0.5f), py(0.08f))
            lineTo(px(0.36f), py(0.28f))
            lineTo(px(0.64f), py(0.28f))
            close()
        }
        drawPath(arrow, color)
        // Foot.
        drawCircle(color, w * 0.09f, Offset(px(0.5f), py(0.90f)))
        // Left branch with its circle.
        drawPath(
            Path().apply {
                moveTo(px(0.5f), py(0.58f))
                lineTo(px(0.24f), py(0.44f))
            },
            color, style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawCircle(color, w * 0.07f, Offset(px(0.22f), py(0.42f)))
        // Right branch with its square plug.
        drawPath(
            Path().apply {
                moveTo(px(0.5f), py(0.50f))
                lineTo(px(0.76f), py(0.36f))
            },
            color, style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawRect(color, Offset(px(0.70f), py(0.24f)), Size(w * 0.14f, h * 0.14f))
    }
}
