package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * FICHIER TEMPORAIRE - A SUPPRIMER
 *
 * MixerTopBarV2 (brief 5) appelle deux composants livres plus tard :
 *   - IconDinV2          -> fichier 5/7 MixerIconsV2.kt       (brief 7)
 *   - SustainPedalButton -> fichier 6/7 SustainPedalButton.kt (brief 8)
 *
 * Ces stubs permettent a 5 de compiler et d'etre integre des maintenant.
 * Des que 7 et 8 arrivent, SUPPRIMER CE FICHIER : les vraies implementations
 * declarent les memes symboles dans le meme package (conflit sinon).
 *
 * Signatures deduites des appels de MixerTopBarV2.kt lignes 104-108.
 */

@Composable
fun IconDinV2(size: Dp, color: Color, withBar: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension * 0.36f
        drawCircle(color.copy(alpha = 0.20f), radius = r, center = Offset(cx, cy))
        drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.4.dp.toPx()))
        val pr = r * 0.20f
        val d = r * 0.52f
        listOf(
            Offset(cx, cy - d),
            Offset(cx - d * 0.95f, cy + d * 0.7f),
            Offset(cx + d * 0.95f, cy + d * 0.7f),
            Offset(cx - d * 0.5f, cy - d * 0.4f),
            Offset(cx + d * 0.5f, cy - d * 0.4f)
        ).forEach { drawCircle(color, radius = pr, center = it) }
        if (withBar) {
            drawRect(
                color,
                topLeft = Offset(cx - r * 0.45f, cy + r * 0.92f),
                size = Size(r * 0.9f, 1.6.dp.toPx())
            )
        }
    }
}

@Composable
fun SustainPedalButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    panelTop: Color,
    panelBottom: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) Brush.verticalGradient(listOf(Color(0xFF3FBF8F), Color(0xFF2A8F6A)))
                else Brush.verticalGradient(listOf(panelTop, panelBottom))
            )
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "PED",
            color = if (isActive) Color.White else Color(0xFF8F9AB0),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
