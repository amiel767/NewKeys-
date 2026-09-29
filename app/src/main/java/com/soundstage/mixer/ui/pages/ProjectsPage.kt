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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.ScenePreset

private val ProjectsBg = Color(0xFF0D111A)
private val CardBg = Color(0xFF151924)
private val CardBorder = Color(0xFF202534)
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
    var isNewProjectDialogOpen by remember { mutableStateOf(false) }
    var newProjectNameInput by remember { mutableStateOf("") }
    var isBlankProjectMode by remember { mutableStateOf(true) }
    var pendingDeleteProjectName by remember { mutableStateOf<String?>(null) }

    // Dialog for creating a new project
    if (isNewProjectDialogOpen) {
        AlertDialog(
            onDismissRequest = { isNewProjectDialogOpen = false },
            title = {
                Text(
                    text = if (hasActiveProject && projects.isNotEmpty()) "Nouveau ou Dupliquer Projet" else "Nouveau Projet",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nom du projet :",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newProjectNameInput,
                        onValueChange = { newProjectNameInput = it },
                        singleLine = true,
                        placeholder = { Text("Ex: Live Worship, Dimmed Lights...", color = Color(0xFF64748B)) },
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
                            text = "Mode de création :",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Option 1: Blank Project
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isBlankProjectMode = true }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isBlankProjectMode,
                                onClick = { isBlankProjectMode = true },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Nouveau Projet Vierge",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Démarre avec une console propre",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Option 2: Duplicate Current Project
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isBlankProjectMode = false }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !isBlankProjectMode,
                                onClick = { isBlankProjectMode = false },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Dupliquer le Projet en Cours",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Clone toutes les pistes, soundfonts et réglages actuels",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Démarre un projet vierge avec une console propre prête à l'emploi.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProjectNameInput.trim().ifEmpty { "Project ${projects.size + 1}" }
                        onCreateNewProject(name, if (hasActiveProject && projects.isNotEmpty()) isBlankProjectMode else true)
                        isNewProjectDialogOpen = false
                    },
                    modifier = Modifier.testTag("btn_confirm_create_project")
                ) {
                    Text("Créer & Ouvrir", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isNewProjectDialogOpen = false }) {
                    Text("Cancel", color = Color(0xFF8E95A5))
                }
            },
            containerColor = Color(0xFF161B29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog for deleting a project
    if (pendingDeleteProjectName != null) {
        val projName = pendingDeleteProjectName!!
        AlertDialog(
            onDismissRequest = { pendingDeleteProjectName = null },
            title = {
                Text(
                    text = "Delete Project?",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete « $projName »?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProject(projName)
                        pendingDeleteProjectName = null
                    },
                    modifier = Modifier.testTag("btn_confirm_delete_project")
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProjectName = null }) {
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
            .background(ProjectsBg)
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF191F2F))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
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
                        text = "Projects",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${projects.size} projet(s) enregistré(s)",
                        fontSize = 11.sp,
                        color = Color(0xFF8C96A8)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // New Project button
                Button(
                    onClick = {
                        newProjectNameInput = "Projet ${projects.size + 1}"
                        isNewProjectDialogOpen = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E2638),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A344C)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_top_new_project")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New", tint = AccentPink, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Project", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Settings icon
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF151924))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Paramètres",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ================= CONTENT =================
        if (projects.isEmpty()) {
            // Empty state: Elegant prompt to create a new project
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            newProjectNameInput = "Projet 1"
                            isNewProjectDialogOpen = true
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
                                .background(Color(0xFF1E2536))
                                .border(1.dp, Color(0xFF2A334A), CircleShape),
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
                            text = "New Project",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Créer votre première session de mixage live",
                            fontSize = 12.sp,
                            color = Color(0xFF8C96A8)
                        )
                    }
                }
            }
        } else {
            // Projects Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // First card: New Project
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF121622))
                            .border(1.dp, Color(0xFF252D40), RoundedCornerShape(14.dp))
                            .clickable {
                                newProjectNameInput = "Projet ${projects.size + 1}"
                                isNewProjectDialogOpen = true
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
                                text = "New Project",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Existing Project cards
                items(projects, key = { it.id }) { project ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBg)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
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
                                        text = project.timestamp.ifEmpty { "Modifié récemment" },
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF7A869C)
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = { pendingDeleteProjectName = project.name },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer",
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
                                        text = "Session Live",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8C96A8)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
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
                                        text = "Ouvrir",
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
