package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.ScenePreset
import com.soundstage.mixer.ui.theme.*

/**
 * Modern Global Scene Side Panel:
 * - Fluid sliding side drawer from the right (identical to SettingsDrawer)
 * - Clean Diskette save icon in header for 1-click global scene save/overwrite
 * - Real Scene files list with sound wave icon, selected badge, rename & animated delete
 * - Dotted/Dashed "+ Nouvelle scène" bottom card
 */
@Composable
fun SceneDialog(
    isOpen: Boolean,
    scenes: List<ScenePreset>,
    activeSceneId: String,
    onSelectScene: (String) -> Unit,
    onSaveCurrentScene: (String) -> Unit = {},
    onCreateBlankScene: (String) -> Unit = {},
    onUpdateActiveScene: () -> Unit = {},
    onDeleteScene: (String) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isNewSceneDialogOpen by remember { mutableStateOf(false) }
    var newSceneNameInput by remember { mutableStateOf("") }
    var renamingSceneId by remember { mutableStateOf<String?>(null) }
    var renamingSceneNameInput by remember { mutableStateOf("") }
    var pendingDeleteSceneId by remember { mutableStateOf<String?>(null) }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.50f))
                .clickable { onClose() }
                .testTag("scene_side_drawer_scrim")
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .widthIn(min = 300.dp, max = 380.dp)
                    .fillMaxWidth(0.38f)
                    .shadow(24.dp, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(Color(0xFF0F131A))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .testTag("scene_side_drawer_content")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ================= 1. HEADER: "Scène" + Diskette Icon + Close (X) =================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Scène",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Floppy Disk (Save) Icon - exactly identical to Snapshots Floppy Disk
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF19202E))
                                    .border(1.dp, Color(0xFF28344A), RoundedCornerShape(8.dp))
                                    .clickable { onUpdateActiveScene() }
                                    .testTag("btn_save_scene_diskette"),
                                contentAlignment = Alignment.Center
                            ) {
                                FloppyDiskIcon(
                                    isArmed = false,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Close Button (X)
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // ================= 2. REAL SCENES LIST =================
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(scenes, key = { it.id }) { scene ->
                            val isSelected = scene.id == activeSceneId
                            val isPendingDelete = pendingDeleteSceneId == scene.id

                            val cardBg = if (isSelected) Color(0xFF16232D) else Color(0xFF161A24)
                            val cardBorder = if (isSelected) NeonCyan.copy(alpha = 0.8f) else Color(0xFF222838)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(if (isSelected) 1.5.dp else 1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { onSelectScene(scene.id) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("scene_item_${scene.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Sound wave icon + Scene title & description
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Sound bars icon container
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Color(0xFF1C383D) else Color(0xFF1F2433)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.size(16.dp, 16.dp)) {
                                                val w = size.width
                                                val h = size.height
                                                val barCol = if (isSelected) NeonCyan else Color(0xFF64748B)

                                                // 3 Sound equalizer bars
                                                drawLine(
                                                    color = barCol,
                                                    start = Offset(w * 0.20f, h * 0.40f),
                                                    end = Offset(w * 0.20f, h * 0.90f),
                                                    strokeWidth = 2.5f,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                                drawLine(
                                                    color = barCol,
                                                    start = Offset(w * 0.50f, h * 0.15f),
                                                    end = Offset(w * 0.50f, h * 0.90f),
                                                    strokeWidth = 2.5f,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                                drawLine(
                                                    color = barCol,
                                                    start = Offset(w * 0.80f, h * 0.50f),
                                                    end = Offset(w * 0.80f, h * 0.90f),
                                                    strokeWidth = 2.5f,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = scene.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (scene.timestamp.isNotEmpty()) scene.timestamp else "Scène active",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Right: Actions (Rename, Delete confirmation, Checkmark)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Rename button
                                        IconButton(
                                            onClick = {
                                                renamingSceneId = scene.id
                                                renamingSceneNameInput = scene.name
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Renommer",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        // Animated Delete Button
                                        AnimatedContent(
                                            targetState = isPendingDelete,
                                            label = "delete_anim_${scene.id}"
                                        ) { pending ->
                                            if (pending) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFDC2626))
                                                            .clickable {
                                                                onDeleteScene(scene.id)
                                                                pendingDeleteSceneId = null
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text("Suppr", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFF333D52))
                                                            .clickable { pendingDeleteSceneId = null }
                                                            .padding(horizontal = 4.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { pendingDeleteSceneId = scene.id },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Supprimer",
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Selected Checkmark Badge
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(NeonCyan),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = Color(0xFF03252E),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ================= 3. BOTTOM BUTTON: "+ Nouvelle scène" (Dashed border) =================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .drawBehind {
                                val stroke = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF333E56),
                                    style = stroke,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                                )
                            }
                            .clickable {
                                newSceneNameInput = "Scène ${scenes.size + 1}"
                                isNewSceneDialogOpen = true
                            }
                            .testTag("btn_new_scene_dashed"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Nouvelle scène",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }

    // New Scene Dialog
    if (isNewSceneDialogOpen) {
        AlertDialog(
            onDismissRequest = { isNewSceneDialogOpen = false },
            title = {
                Text(
                    text = "Créer une nouvelle scène",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Donnez un nom à votre nouvelle scène :",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newSceneNameInput,
                        onValueChange = { newSceneNameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0x55FFFFFF),
                            cursorColor = NeonCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSceneNameInput.isNotBlank()) {
                            onSaveCurrentScene(newSceneNameInput.trim())
                        }
                        isNewSceneDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Enregistrer", color = Color(0xFF032830), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isNewSceneDialogOpen = false }) {
                    Text("Annuler", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Rename Scene Dialog
    if (renamingSceneId != null) {
        AlertDialog(
            onDismissRequest = { renamingSceneId = null },
            title = {
                Text(
                    text = "Renommer la scène",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = renamingSceneNameInput,
                    onValueChange = { renamingSceneNameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0x55FFFFFF),
                        cursorColor = NeonCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = renamingSceneId
                        if (id != null && renamingSceneNameInput.isNotBlank()) {
                            onSaveCurrentScene(renamingSceneNameInput.trim())
                        }
                        renamingSceneId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Valider", color = Color(0xFF032830), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSceneId = null }) {
                    Text("Annuler", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
