package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.PatchItem
import com.soundstage.mixer.ui.theme.*

// Snapshot Harmonized Palette
private val SnapshotAccent = Color(0xFF9BC0F8)
private val SnapshotAccentDark = Color(0xFF8FB6F2)
private val CardBorder = Color(0xFF38455E)

/**
 * Modern Global Scene Side Panel for Patches:
 * - Dedicated exclusively to internal Patches belonging to the open Setlist
 * - Fluid sliding side drawer from the right
 * - Clean Diskette save icon in header for 1-click setlist/patch save with visual pulse
 * - Home icon to return to Projects / Setlists Page
 * - Displays Patches (Level 2), with option to create, rename, delete or select patches
 */
@Composable
fun SceneDialog(
    isOpen: Boolean,
    patches: List<PatchItem>,
    activePatchId: String?,
    onSelectPatch: (String) -> Unit,
    onCreateNewPatch: (name: String, isBlank: Boolean) -> Unit = { _, _ -> },
    onDeletePatch: (String) -> Unit = {},
    onRenamePatch: (id: String, newName: String) -> Unit = { _, _ -> },
    onUpdateActiveScene: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isNewSceneDialogOpen by remember { mutableStateOf(false) }
    var newSceneNameInput by remember { mutableStateOf("") }
    var isBlankProjectMode by remember { mutableStateOf(true) }
    var isSavePulsing by remember { mutableStateOf(false) }
    var renamingSceneId by remember { mutableStateOf<String?>(null) }
    var renamingSceneNameInput by remember { mutableStateOf("") }
    var pendingDeleteSceneId by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    if (isOpen) {
        val scrimAlpha by animateFloatAsState(
            targetValue = 0.55f,
            animationSpec = tween(durationMillis = 260),
            label = "scene_scrim_fade"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable { onClose() }
                .testTag("scene_side_drawer_scrim")
        )
    }

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
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 300.dp, max = 380.dp)
                    .fillMaxWidth(0.38f)
                    .shadow(24.dp, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(Color(0xFF0F131A))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .testTag("scene_side_drawer_content")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ================= 1. HEADER: "Patches" + Home + Diskette Icon + Close (X) =================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Patches",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Home Button (Return to Setlists Page)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF283144), Color(0xFF1D2433))
                                        )
                                    )
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                    .drawWithContent {
                                        drawContent()
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.12f),
                                            start = Offset(4.dp.toPx(), 1f),
                                            end = Offset(size.width - 4.dp.toPx(), 1f),
                                            strokeWidth = 1.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                    .clickable {
                                        onClose()
                                        onNavigateHome()
                                    }
                                    .testTag("btn_scene_home"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Setlists",
                                    tint = SnapshotAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Floppy Disk (Save) Icon with shine feedback
                            val diskBorderColor = if (isSavePulsing) SnapshotAccent else CardBorder
                            val diskBgColor = if (isSavePulsing) Color(0x339BC0F8) else Color(0xFF19202E)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(diskBgColor)
                                    .border(if (isSavePulsing) 1.5.dp else 1.dp, diskBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        isSavePulsing = true
                                        onUpdateActiveScene()
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(650)
                                            isSavePulsing = false
                                        }
                                    }
                                    .testTag("btn_save_scene_diskette"),
                                contentAlignment = Alignment.Center
                            ) {
                                FloppyDiskIcon(
                                    isArmed = isSavePulsing,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // ================= 2. PATCHES LIST =================
                    if (patches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "0 Patch",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "No patch in this Setlist.\nClick below to create a patch.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(patches, key = { it.id }) { patch ->
                                val isSelected = patch.id == activePatchId
                                val isPendingDelete = pendingDeleteSceneId == patch.id

                                val cardBg = if (isSelected) Color(0xFF222C3D) else Color(0xFF141924)
                                val cardBorder = if (isSelected) Color(0x889BC0F8) else Color(0xFF28344A)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .border(if (isSelected) 1.5.dp else 1.dp, cardBorder, RoundedCornerShape(14.dp))
                                        .clickable { onSelectPatch(patch.id) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .testTag("patch_item_${patch.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left: Sound wave icon + Patch name & snapshot count
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSelected) Color(0xFF2B384E) else Color(0xFF19202E))
                                                    .border(1.dp, if (isSelected) Color(0x889BC0F8) else Color(0xFF28344A), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Canvas(modifier = Modifier.size(16.dp, 16.dp)) {
                                                    val w = size.width
                                                    val h = size.height
                                                    val barCol = if (isSelected) SnapshotAccent else Color(0xFF64748B)

                                                    drawLine(
                                                        color = barCol,
                                                        start = Offset(w * 0.20f, h * 0.40f),
                                                        end = Offset(w * 0.20f, h * 0.90f),
                                                        strokeWidth = 2.5f,
                                                        cap = StrokeCap.Round
                                                    )
                                                    drawLine(
                                                        color = barCol,
                                                        start = Offset(w * 0.50f, h * 0.15f),
                                                        end = Offset(w * 0.50f, h * 0.90f),
                                                        strokeWidth = 2.5f,
                                                        cap = StrokeCap.Round
                                                    )
                                                    drawLine(
                                                        color = barCol,
                                                        start = Offset(w * 0.80f, h * 0.50f),
                                                        end = Offset(w * 0.80f, h * 0.90f),
                                                        strokeWidth = 2.5f,
                                                        cap = StrokeCap.Round
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = patch.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val snapCount = patch.configuredSnapshotSlots.size
                                                val snapText = when (snapCount) {
                                                    0 -> "0 Snapshot"
                                                    1 -> "1 Snapshot"
                                                    else -> "$snapCount Snapshots"
                                                }
                                                Text(
                                                    text = snapText,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Right: Actions (Rename, Delete, Selected Checkmark)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    renamingSceneId = patch.id
                                                    renamingSceneNameInput = patch.name
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            AnimatedContent(
                                                targetState = isPendingDelete,
                                                label = "delete_anim_${patch.id}"
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
                                                                    onDeletePatch(patch.id)
                                                                    pendingDeleteSceneId = null
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                                        ) {
                                                            Text("Delete", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                                        onClick = { pendingDeleteSceneId = patch.id },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete",
                                                            tint = Color(0xFF64748B),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .shadow(2.dp, CircleShape, ambientColor = Color.Black, spotColor = Color(0x66000000))
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(SnapshotAccent, SnapshotAccentDark)
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Active",
                                                        tint = Color(0xFF101726),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ================= 3. BOTTOM BUTTON: "+ New Patch" (Dashed border) =================
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
                                newSceneNameInput = "Patch ${patches.size + 1}"
                                isNewSceneDialogOpen = true
                            }
                            .testTag("btn_new_patch_dashed"),
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
                                text = "New Patch",
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

    // New Patch Dialog
    if (isNewSceneDialogOpen) {
        AlertDialog(
            onDismissRequest = { isNewSceneDialogOpen = false },
            title = {
                Text(
                    text = "New Patch",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter patch name:",
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
                            focusedBorderColor = SnapshotAccent,
                            unfocusedBorderColor = Color(0x55FFFFFF),
                            cursorColor = SnapshotAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Patch Mode:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: Blank Patch
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isBlankProjectMode) Color(0xFF1E293B) else Color(0xFF111622))
                                .border(1.dp, if (isBlankProjectMode) SnapshotAccent else Color(0xFF263248), RoundedCornerShape(8.dp))
                                .clickable { isBlankProjectMode = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isBlankProjectMode,
                                    onClick = { isBlankProjectMode = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = SnapshotAccent, unselectedColor = Color(0xFF64748B)),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Blank Patch",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Default tracks",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Option 2: Duplicate Current Patch
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isBlankProjectMode) Color(0xFF1E293B) else Color(0xFF111622))
                                .border(1.dp, if (!isBlankProjectMode) SnapshotAccent else Color(0xFF263248), RoundedCornerShape(8.dp))
                                .clickable { isBlankProjectMode = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = !isBlankProjectMode,
                                    onClick = { isBlankProjectMode = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = SnapshotAccent, unselectedColor = Color(0xFF64748B)),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Duplicate Current",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Clone tracks & SF2",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newSceneNameInput.trim().ifEmpty { "Patch ${patches.size + 1}" }
                        onCreateNewPatch(name, isBlankProjectMode)
                        isNewSceneDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccent)
                ) {
                    Text("Create Patch", color = Color(0xFF101726), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isNewSceneDialogOpen = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Rename Patch Dialog
    if (renamingSceneId != null) {
        AlertDialog(
            onDismissRequest = { renamingSceneId = null },
            title = {
                Text(
                    text = "Rename Patch",
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
                        focusedBorderColor = SnapshotAccent,
                        unfocusedBorderColor = Color(0x55FFFFFF),
                        cursorColor = SnapshotAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = renamingSceneId
                        if (id != null && renamingSceneNameInput.isNotBlank()) {
                            onRenamePatch(id, renamingSceneNameInput.trim())
                        }
                        renamingSceneId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccent)
                ) {
                    Text("Save", color = Color(0xFF101726), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSceneId = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
