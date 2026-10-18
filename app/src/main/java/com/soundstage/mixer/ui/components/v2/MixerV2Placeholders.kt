package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/*
 * FICHIER TEMPORAIRE - A SUPPRIMER
 *
 * Stubs des composants V2 livres dans les briefs suivants. Ils permettent aux
 * briefs 3 (MixerTopBarV2) et 4 (MixerBottomBarV2) de compiler des maintenant.
 *
 *   - IconDinV2             -> fichier 5/7 MixerIconsV2.kt            (brief 7)
 *   - IconSaveV2            -> fichier 5/7 MixerIconsV2.kt            (brief 7)
 *   - IconPianoV2           -> fichier 5/7 MixerIconsV2.kt            (brief 7)
 *   - SustainPedalButton    -> fichier 6/7 SustainPedalButton.kt      (brief 8)
 *   - ManageSnapshotDialogV2-> fichier 7/7 ManageSnapshotDialogV2.kt  (brief 9)
 *
 * DES QUE CES FICHIERS ARRIVENT, SUPPRIMER CE FICHIER : les vraies
 * implementations declarent les memes symboles dans le meme package
 * (conflit de declaration sinon).
 *
 * Signatures deduites des sites d'appel reels (MixerTopBarV2.kt l.104-108,
 * MixerBottomBarV2.kt : IconSaveV2(size, color), IconPianoV2(size, color),
 * ManageSnapshotDialogV2(slotKey, currentName, onDismiss, onSave)).
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
fun IconSaveV2(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.12f),
            style = Stroke(width = sw)
        )
        drawRect(color, topLeft = Offset(w * 0.30f, h * 0.12f), size = Size(w * 0.34f, h * 0.26f))
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.26f, h * 0.56f),
            size = Size(w * 0.48f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = sw)
        )
    }
}

@Composable
fun IconPianoV2(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.84f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.08f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.84f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(width = 1.3.dp.toPx())
        )
        listOf(0.28f, 0.5f, 0.72f).forEach { fx ->
            drawRect(
                color,
                topLeft = Offset(w * fx - w * 0.045f, h * 0.24f),
                size = Size(w * 0.09f, h * 0.30f)
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

@Composable
fun ManageSnapshotDialogV2(
    slotKey: String,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433))))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Gérer le snapshot", color = Color(0xFFE9EDF5), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("Clé : $slotKey", color = Color(0xFF7B859A), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Annuler", color = Color(0xFFC9D1E0), fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                )
                Text(
                    "Enregistrer", color = Color(0xFF8FB2E8), fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { onSave(name, slotKey) }.padding(8.dp)
                )
            }
        }
    }
}
