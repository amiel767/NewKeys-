package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScenePreset
import com.example.ui.theme.*

/**
 * Scene Expanded Dialog styled following Google AOSP Material You / Settings Design:
 * - Clean Preference Groups with rounded container styling
 * - AOSP section headers, preference rows with title, summary and radio selectors
 * - Interactive scene recording with manual custom name input
 */
@Composable
fun SceneDialog(
    isOpen: Boolean,
    scenes: List<ScenePreset>,
    activeSceneId: String,
    onSelectScene: (String) -> Unit,
    onSaveCurrentScene: (String) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isNamingOpen by remember { mutableStateOf(false) }
    var sceneNameInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .widthIn(min = 360.dp, max = 460.dp)
            .shadow(32.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E2024))
            .border(1.2.dp, Color(0x3300E5FF), RoundedCornerShape(24.dp))
            .clickable(enabled = false) {}
            .padding(18.dp)
            .testTag("scene_aosp_settings_dialog")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Google AOSP Settings Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 18.sp,
                            color = NeonCyan
                        )
                    }
                    Column {
                        Text(
                            text = "Scènes de mixage",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E2E6)
                        )
                        Text(
                            text = "Sélectionnez ou enregistrez une scène",
                            fontSize = 11.5.sp,
                            color = Color(0xFF90909A)
                        )
                    }
                }

                // Close button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 13.sp, color = Color(0xFFC4C6D0))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AOSP Section Header
            Text(
                text = "SCÈNES DISPONIBLES",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            // AOSP Preference Group Card Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF282A30))
                    .padding(vertical = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(scenes) { scene ->
                        val isSelected = activeSceneId == scene.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectScene(scene.id) }
                                .background(if (isSelected) Color(0x1800E5FF) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Scene Color Dot Indicator
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(scene.color)
                            )

                            // Title and Summary Text
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scene.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NeonCyanLight else Color(0xFFE2E2E6)
                                )
                                Text(
                                    text = "Enregistré le ${scene.timestamp}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E9199)
                                )
                            }

                            // AOSP Radio Button
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectScene(scene.id) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = NeonCyan,
                                    unselectedColor = Color(0xFF6E7179)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scene Recording Section (Manual Name Input)
            if (isNamingOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF282A30))
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "NOMMER LA SCÈNE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 0.6.sp
                    )

                    // Text Input Field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1B1C20))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = sceneNameInput,
                            onValueChange = { sceneNameInput = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(NeonCyan),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val finalName = sceneNameInput.trim().ifEmpty { "Scène ${scenes.size + 1}" }
                                    onSaveCurrentScene(finalName)
                                    isNamingOpen = false
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (sceneNameInput.isEmpty()) {
                            Text(
                                text = "Entrez le nom de la scène...",
                                color = Color(0x66FFFFFF),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Action Buttons (Annuler & Enregistrer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { isNamingOpen = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Annuler",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC4C6D0)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(NeonCyan)
                                .clickable {
                                    val finalName = sceneNameInput.trim().ifEmpty { "Scène ${scenes.size + 1}" }
                                    onSaveCurrentScene(finalName)
                                    isNamingOpen = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Enregistrer",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00363F)
                            )
                        }
                    }
                }
            } else {
                // AOSP Tonal Action Button to start recording
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(NeonCyan)
                        .clickable {
                            sceneNameInput = "Scène ${scenes.size + 1}"
                            isNamingOpen = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💾 Enregistrer la scène",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00363F)
                    )
                }
            }
        }
    }
}
