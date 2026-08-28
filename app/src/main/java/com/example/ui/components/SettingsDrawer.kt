package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    isLowLatency: Boolean,
    onToggleLowLatency: () -> Unit,
    isVelocityTouch: Boolean,
    onToggleVelocityTouch: () -> Unit,
    isMetroInRec: Boolean,
    onToggleMetroInRec: () -> Unit,
    appFolder: String,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x59000000))
            .clickable { onClose() }
            .testTag("settings_drawer_scrim")
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .shadow(30.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1C1C26), Color(0xFF14141B))
                        )
                    )
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .clickable(enabled = false) {}
                    .padding(16.dp)
                    .testTag("settings_drawer")
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Réglages",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Settings Rows
                SettingsToggleRow(
                    title = "Thème couleur",
                    subtitle = "Néon violet & Cyan",
                    isOn = true,
                    onToggle = {}
                )

                SettingsToggleRow(
                    title = "Latence audio ultra-faible",
                    subtitle = "Buffer OpenSL / AAudio réduit",
                    isOn = isLowLatency,
                    onToggle = onToggleLowLatency
                )

                SettingsToggleRow(
                    title = "Vélocité clavier",
                    subtitle = "Sensible au toucher",
                    isOn = isVelocityTouch,
                    onToggle = onToggleVelocityTouch
                )

                SettingsToggleRow(
                    title = "Métronome",
                    subtitle = "Cliquable pendant Rec",
                    isOn = isMetroInRec,
                    onToggle = onToggleMetroInRec
                )

                Spacer(modifier = Modifier.height(8.dp))

                // App Folder Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                ) {
                    Text(text = "Dossier des Soundfonts", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(text = appFolder, fontSize = 10.sp, color = NeonCyan)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Audio Engine Status Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1422D3EE))
                        .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "Moteur Audio Android Live", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text(text = "48 kHz · 64 frames buffer · 0.0% CPU", fontSize = 9.sp, color = TextDim)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isOn: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = subtitle, fontSize = 10.sp, color = TextDim2)
        }

        Box(
            modifier = Modifier
                .width(38.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isOn) Brush.horizontalGradient(listOf(NeonCyanLight, NeonCyan)) else Brush.linearGradient(listOf(Color(0x1AFFFFFF), Color(0x1AFFFFFF)))
                )
                .padding(2.dp),
            contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
