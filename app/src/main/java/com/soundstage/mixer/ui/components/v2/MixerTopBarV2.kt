package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.components.DetectedChord

@Composable
fun MixerTopBarV2(
    transpose: Int, octave: Int,
    onTransposeChange: (Int) -> Unit, onOctaveChange: (Int) -> Unit,
    detectedChord: DetectedChord?,
    isSustainActive: Boolean, onToggleSustain: () -> Unit,
    onOpenNotes: () -> Unit, onOpenDrumPad: () -> Unit,
    onPanic: () -> Unit, onOpenScenes: () -> Unit, onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pT = Color(0xFF2B3348); val pB = Color(0xFF1D2433)
    val tM = Color(0xFFE9EDF5); val tG = Color(0xFF7B859A); val ac = Color(0xFF8FB2E8)

    Row(modifier.height(36.dp), // Fix: réduit de 40 à 36
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        
        // Fix: Stepper réduit
        StepperV2("TRANS", if (transpose > 0) "+$transpose" else "$transpose",
            { onTransposeChange(-1) }, { onTransposeChange(1) }, pT, pB, tM, tG)
        StepperV2("OCT", if (octave > 0) "+$octave" else "$octave",
            { onOctaveChange(-1) }, { onOctaveChange(1) }, pT, pB, tM, tG)

        Row(Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(pT, pB)))
                .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            
            Box(Modifier.weight(0.3f), contentAlignment = Alignment.CenterStart) {
                if (detectedChord != null) {
                    val name = detectedChord.primaryName
                    // Fix: Max 14sp au lieu de 23sp
                    val size = when { name.length > 9 -> 11.sp; name.length > 6 -> 12.sp; else -> 14.sp }
                    Text(name, color = Color.White, fontSize = size,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                } else {
                    Text("– – –", color = Color(0xFF4F596E), fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(Color(0x14FFFFFF)))
            
            Column(Modifier.weight(0.7f).padding(start = 8.dp),
                verticalArrangement = Arrangement.Center) {
                if (detectedChord != null) {
                    Box(Modifier.clip(RoundedCornerShape(4.dp))
                            .background(ac.copy(alpha = 0.16f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(detectedChord.notesList.joinToString(" - "),
                            color = Color(0xFFB9CDF2), fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val alts = listOfNotNull(
                        detectedChord.equivalentName.takeIf { it.isNotBlank() && it != detectedChord.primaryName },
                        detectedChord.alternateNames.takeIf { it.isNotBlank() })
                    if (alts.isNotEmpty()) {
                        Spacer(Modifier.height(1.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            alts.take(2).forEach { alt ->
                                Box(Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x14FFFFFF))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text(alt, color = tM, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                } else {
                    Text("Waiting for notes…", color = Color(0xFF566075),
                        fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)) // Fix: taille 36 au lieu de 40
                .background(Brush.verticalGradient(listOf(Color(0xFFD2587C), Color(0xFFB03F63))))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPanic() },
            contentAlignment = Alignment.Center) {
            IconDinV2(size = 26.dp, color = Color.White, withBar = true)
        }

        SustainPedalButton(isActive = isSustainActive, onToggle = onToggleSustain, panelTop = pT, panelBottom = pB)

        TopIconButton(onOpenNotes, pT, pB) { Icon(painterResource(R.drawable.ic_quill), "Notes", tint = Color.White, modifier = Modifier.size(16.dp)) }
        
        TopIconButton(onOpenDrumPad, pT, pB) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(
                    listOf(Color(0xFFE0588C), Color(0xFFE0588C), Color(0xFFA55BE0)),
                    listOf(Color(0xFFE0588C), Color(0xFFA55BE0), Color(0xFFA55BE0)),
                    listOf(Color(0xFFA55BE0), Color(0xFF4F7FE6), Color(0xFF4F7FE6))
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { c -> Box(Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(c)) }
                    }
                }
            }
        }
        
        // Fix: Utilisation de IconFolderV2 et IconGearV2
        TopIconButton(onOpenScenes, pT, pB) { IconFolderV2(size = 18.dp, color = ac) }
        TopIconButton(onOpenSettings, pT, pB) { IconGearV2(size = 18.dp, color = Color(0xFFC9D1E0)) }
    }
}

@Composable
private fun StepperV2(
    label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit,
    pT: Color, pB: Color, tM: Color, tG: Color
) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)) // Fix: 36dp hauteur
            .background(Brush.verticalGradient(listOf(pT, pB)))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        // JSX RoundStep: cercle 30x30, relief = liseré clair en haut + ombre portée basse
        Box(Modifier.size(30.dp).shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onMinus() },
            contentAlignment = Alignment.Center) {
            Text("−", color = Color(0xFFE3E8F2), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.padding(horizontal = 2.dp).defaultMinSize(minWidth = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = tG, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 8.sp)
            Text(value, color = tM, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, lineHeight = 12.sp)
        }
        Box(Modifier.size(30.dp).shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPlus() },
            contentAlignment = Alignment.Center) {
            Text("+", color = Color(0xFFE3E8F2), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun TopIconButton(
    onClick: () -> Unit, pT: Color, pB: Color, content: @Composable () -> Unit
) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)) // Fix: 36dp
            .background(Brush.verticalGradient(listOf(pT, pB)))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center) { content() }
}
