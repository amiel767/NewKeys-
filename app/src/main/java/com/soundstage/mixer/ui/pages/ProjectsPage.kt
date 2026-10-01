package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.ScenePreset

private val SetlistsBg = Color(0xFF0D111A)
private val CardBgBrush = Brush.verticalGradient(listOf(Color(0xFF222836), Color(0xFF1B202C)))
private val AccentPink = Color(0xFFE05285)
private val AccentBlue = Color(0xFF3B82F6)

@Composable
fun ProjectsPage(
    projects: List<ScenePreset>,
    onOpenProject: (String) -> Unit,
    onCreateNewProject: (name: String, isBlank: Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    hasActiveProject: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isNewSetlistDialogOpen by remember { mutableStateOf(false) }
    var newSetlistNameInput by remember { mutableStateOf("") }
    var isBlankSetlistMode by remember { mutableStateOf(true) }
    var pendingDeleteSetlistName by remember { mutableStateOf<String?>(null) }

    // Dialog for creating a new setlist
    if (isNewSetlistDialogOpen) {
        AlertDialog(
            onDismissRequest = { isNewSetlistDialogOpen = false },
            title = {
                Text(
                    text = if (hasActiveProject && projects.isNotEmpty()) "New or Duplicate Setlist" else "New Setlist",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Setlist Name:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newSetlistNameInput,
                        onValueChange = { newSetlistNameInput = it },
                        singleLine = true,
                        placeholder = { Text("Ex: Live Worship, Sunday Service...", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Color(0x44FFFFFF),
                            cursorColor = AccentBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_project_name")
                    )

                    if (hasActiveProject && projects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Creation Mode:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Option 1: Blank Setlist
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isBlankSetlistMode = true }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isBlankSetlistMode,
                                onClick = { isBlankSetlistMode = true },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "New Blank Setlist",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Starts with a fresh clean console",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Option 2: Duplicate Current Setlist
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isBlankSetlistMode = false }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !isBlankSetlistMode,
                                onClick = { isBlankSetlistMode = false },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Duplicate Current Setlist",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Clones all current tracks, soundfonts and settings",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Start a fresh setlist with a clean console ready for live play.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newSetlistNameInput.trim().ifEmpty { "Setlist ${projects.size + 1}" }
                        onCreateNewProject(name, if (hasActiveProject && projects.isNotEmpty()) isBlankSetlistMode else true)
                        isNewSetlistDialogOpen = false
                    },
                    modifier = Modifier.testTag("btn_confirm_create_project")
                ) {
                    Text("Create & Open", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isNewSetlistDialogOpen = false }) {
                    Text("Cancel", color = Color(0xFF8E95A5))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog for deleting a setlist
    if (pendingDeleteSetlistName != null) {
        val setName = pendingDeleteSetlistName!!
        AlertDialog(
            onDismissRequest = { pendingDeleteSetlistName = null },
            title = {
                Text(
                    text = "Delete Setlist?",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete « $setName »?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProject(setName)
                        pendingDeleteSetlistName = null
                    },
                    modifier = Modifier.testTag("btn_confirm_delete_project")
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSetlistName = null }) {
                    Text("Cancel", color = Color(0xFF8E95A5))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SetlistsBg)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("projects_page")
    ) {
        // ================= HEADER =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBgBrush)
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(4.dp.toPx(), 1f),
                                end = Offset(size.width - 4.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "SoundStage",
                        tint = AccentPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Setlists",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${projects.size} saved setlist(s)",
                        fontSize = 11.sp,
                        color = Color(0xFF8C96A8)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // New Setlist button
                Box(
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBgBrush)
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
                            newSetlistNameInput = "Setlist ${projects.size + 1}"
                            isNewSetlistDialogOpen = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("btn_top_new_project"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New", tint = AccentPink, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Setlist", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Settings icon
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBgBrush)
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
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ================= CONTENT =================
        if (projects.isEmpty()) {
            // Empty state: Elegant prompt to create a new setlist
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBgBrush)
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(8.dp.toPx(), 1f),
                                end = Offset(size.width - 8.dp.toPx(), 1f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        .clickable {
                            newSetlistNameInput = "Setlist 1"
                            isNewSetlistDialogOpen = true
                        }
                        .padding(32.dp)
                        .testTag("card_empty_new_project"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2536)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = AccentPink,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "New Setlist",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Create your first live performance setlist",
                            fontSize = 12.sp,
                            color = Color(0xFF8C96A8)
                        )
                    }
                }
            }
        } else {
            // Setlists Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // First card: New Setlist
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF202738), Color(0xFF161C2A))))
                            .drawWithContent {
                                drawContent()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(6.dp.toPx(), 1f),
                                    end = Offset(size.width - 6.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            .clickable {
                                newSetlistNameInput = "Setlist ${projects.size + 1}"
                                isNewSetlistDialogOpen = true
                            }
                            .testTag("grid_item_new_project"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B2232)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New",
                                    tint = AccentPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "New Setlist",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Existing Setlist cards
                items(projects, key = { it.id }) { project ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBgBrush)
                            .drawWithContent {
                                drawContent()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(6.dp.toPx(), 1f),
                                    end = Offset(size.width - 6.dp.toPx(), 1f),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            .clickable { onOpenProject(project.name) }
                            .padding(14.dp)
                            .testTag("project_card_${project.name}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = project.timestamp.ifEmpty { "Recently modified" },
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF7A869C)
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = { pendingDeleteSetlistName = project.name },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFF6B7280),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Bottom footer of card: Open badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(AccentBlue)
                                    )
                                    Text(
                                        text = "Live Session",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8C96A8)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .shadow(1.dp, RoundedCornerShape(6.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E2536))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Open",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Open",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
