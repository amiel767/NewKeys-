package com.soundstage.mixer.ui.components.v2

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/*
 * FICHIER TEMPORAIRE - A SUPPRIMER
 *
 * Stubs des composants V2 non encore livres. Ils permettent aux fichiers
 * 3/7 (MixerTopBarV2) et 4/7 (MixerBottomBarV2) de compiler.
 *
 *   - SustainPedalButton     -> fichier 6/7 SustainPedalButton.kt     (brief 6)
 *   - ManageSnapshotDialogV2 -> fichier 7/7 ManageSnapshotDialogV2.kt (brief 7)
 *
 * IconDinV2 / IconSaveV2 / IconPianoV2 ont ete RETIRES de ce fichier :
 * le fichier 5/7 MixerIconsV2.kt les fournit desormais (brief 5).
 *
 * DES QUE LES FICHIERS 6/7 ET 7/7 ARRIVENT, SUPPRIMER CE FICHIER : les vraies
 * implementations declarent les memes symboles dans le meme package
 * (conflit de declaration sinon).
 *
 * Signatures deduites des sites d'appel reels de MixerTopBarV2.kt (l.107)
 * et MixerBottomBarV2.kt.
 */

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
