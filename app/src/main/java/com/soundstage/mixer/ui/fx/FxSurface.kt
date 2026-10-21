package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The raised treatment shared by the rack's cards and the header chips.
 *
 * Three things make the lift read: a drop shadow just below the bottom edge, a 1px
 * sheen along the top edge, and a 1px darker seam along the bottom one.
 *
 * The shadow is painted here rather than with `Modifier.shadow` for two reasons: the
 * platform shadow is not rendered by the Roborazzi capture, and it is drawn over the
 * card, so a translucent fill would let it show through the card itself. Painting it
 * with `drawBehind` and starting the gradient exactly at the bottom edge keeps it
 * strictly underneath, whatever the fill's alpha. Two passes give it a penumbra: a
 * wide faint one, then a tighter darker one.
 */
fun Modifier.raised(
    corner: Dp,
    elevation: Dp = 6.dp,
    fill: Brush? = null,
): Modifier = this
    .drawBehind {
        val r = corner.toPx()
        val e = elevation.toPx()
        val w = size.width
        val h = size.height

        val soft = Path().apply {
            addRoundRect(RoundRect(0f, h * 0.70f, w, h + e * 1.5f, CornerRadius(r * 1.15f)))
        }
        drawPath(soft, Brush.verticalGradient(
            colors = listOf(Color(0x00000000), Color(0x4D000000)),
            startY = h, endY = h + e * 1.5f))

        val tight = Path().apply {
            addRoundRect(RoundRect(0f, h * 0.86f, w, h + e * 0.8f, CornerRadius(r)))
        }
        drawPath(tight, Brush.verticalGradient(
            colors = listOf(Color(0x00000000), Color(0x99000000)),
            startY = h, endY = h + e * 0.8f))
    }
    .clip(RoundedCornerShape(corner))
    .then(if (fill != null) Modifier.background(fill) else Modifier)
    .drawWithContent {
        drawContent()
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color(0x00FFFFFF), Color(0x26FFFFFF), Color(0x00FFFFFF))),
            start = Offset(0f, 0.5f), end = Offset(size.width, 0.5f), strokeWidth = 1f)
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color(0x00000000), Color(0x45000000), Color(0x00000000))),
            start = Offset(0f, size.height - 0.5f), end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1f)
    }
