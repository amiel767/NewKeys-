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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.example.model.ScenePreset
import com.example.ui.theme.*

/**
 * Scene Expanded Card:
 * Instead of a full-screen blocking pop-up, the small scene square button expands smoothly
 * in-place into an elegant deck showing saved scenes, recording current scene, etc.
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
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(200)) + expandVertically(tween(250), expandFrom = Alignment.Top),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .shadow(28.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF262638), Color(0xFF1B1B26), Color(0xFF14141E))
                    )
                )
                .border(1.5.dp, NeonCyan, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("scene_expanded_panel")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🎬", fontSize = 11.sp)
                        }
                        Text(
                            text = "Scènes Live Enregistrées",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 11.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Saved Scenes List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(scenes) { scene ->
                        val isSelected = activeSceneId == scene.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0x2622D3EE) else Color(0x0EFFFFFF)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else Color(0x14FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectScene(scene.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .shadow(4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(scene.color)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scene.name,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = scene.timestamp,
                                    fontSize = 8.5.sp,
                                    color = TextDim2
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonCyan)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF002B36)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Save Current Snapshot Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(NeonCyanLight, NeonCyanDark)))
                        .clickable {
                            val newName = "Scène #${scenes.size + 1} (Live Setup)"
                            onSaveCurrentScene(newName)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Enregistrer la configuration actuelle",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF002B36)
                    )
                }
            }
        }
    }
}
