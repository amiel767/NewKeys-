package com.example.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScenePreset
import com.example.ui.theme.*

/**
 * Scene Compact Dialog (reduced by 30% in size) styled following Google AOSP Material / Settings Design:
 * - Quick "Enregistrer" action button placed directly next to the close '✕' button.
 * - Ultra-compact AOSP preference rows with title, date and radio selector.
 * - Saves scenes to app scene storage.
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
            .widthIn(min = 250.dp, max = 310.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF181D26))
            .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(16.dp))
            .clickable(enabled = false) {}
            .padding(12.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 13.sp,
                            color = NeonCyan
                        )
                    }
                    Column {
                        Text(
                            text = "Scènes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E2E6)
                        )
                        Text(
                            text = "Mixer presets",
                            fontSize = 9.5.sp,
                            color = Color(0xFF90909A)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Button "Enregistrer" next to X button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan)
                            .clickable {
                                isNamingOpen = !isNamingOpen
                                if (isNamingOpen) {
                                    sceneNameInput = "Scène ${scenes.size + 1}"
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💾 Enregistrer",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF002B33)
                        )
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0x1FFFFFFF))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 11.sp, color = Color(0xFFC4C6D0))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scene Naming Section
            AnimatedVisibility(visible = isNamingOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF222834))
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ENREGISTRER LA SCÈNE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = sceneNameInput,
                            onValueChange = { sceneNameInput = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 11.5.sp,
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
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x1FFFFFFF))
                                .clickable { isNamingOpen = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Annuler", fontSize = 10.sp, color = TextDim)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan)
                                .clickable {
                                    val finalName = sceneNameInput.trim().ifEmpty { "Scène ${scenes.size + 1}" }
                                    onSaveCurrentScene(finalName)
                                    isNamingOpen = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Valider", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF002B33))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // AOSP Section Header
            Text(
                text = "SCÈNES DISPONIBLES",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // AOSP Compact Preference Group Card Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF202632))
                    .padding(vertical = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                ) {
                    items(scenes) { scene ->
                        val isSelected = activeSceneId == scene.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectScene(scene.id) }
                                .background(if (isSelected) Color(0x1800E5FF) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Scene Color Dot Indicator
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(scene.color)
                            )

                            // Title and Summary Text
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scene.name,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NeonCyanLight else Color(0xFFE2E2E6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = scene.timestamp,
                                    fontSize = 9.sp,
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
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
