package com.soundstage.mixer.ui.pages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
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
import com.soundstage.mixer.ui.components.AmbientStudioBackground

// Snapshot Color Palette
private val SnapshotAccent = Color(0xFF9BC0F8)
private val SnapshotAccentDark = Color(0xFF8FB6F2)
private val SnapshotAccentBorder = Color(0x669BC0F8)
private val CardBorder = Color(0xFF38455E)
private val CardBgBrush = Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433)))
private val SelectedCardBrush = Brush.verticalGradient(listOf(Color(0xFF3B4863), Color(0xFF2A344A)))

@Composable
fun ProjectsPage(
    projects: List<ScenePreset>,
    onOpenProject: (String) -> Unit,
    onCreateNewProject: (name: String, isBlank: Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    hasActiveProject: Boolean = false,
    themeHue: Float = 185f,
    themeBgSaturation: Float = 1.60f,
    themeBgLightness: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSetlistName by remember(projects) {
        mutableStateOf(projects.firstOrNull()?.name ?: "")
    }
    var isNewSetlistDialogOpen by remember { mutableStateOf(false) }
    var newSetlistNameInput by remember { mutableStateOf("") }
    var isBlankSetlistMode by remember { mutableStateOf(true) }
    var pendingDeleteSetlistName by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf("Recently used") }
    var isSortMenuOpen by remember { mutableStateOf(false) }

    // Filtered setlists based on search query
    val filteredProjects = remember(projects, searchQuery, sortOrder) {
        val queryFiltered = if (searchQuery.isBlank()) {
            projects
        } else {
            projects.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        when (sortOrder) {
            "Alphabetical" -> queryFiltered.sortedBy { it.name.lowercase() }
            "Date created" -> queryFiltered.sortedByDescending { it.timestamp }
            else -> queryFiltered
        }
    }

    // ================= CREATE NEW SETLIST DIALOG =================
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Setlist Name:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newSetlistNameInput,
                        onValueChange = { newSetlistNameInput = it },
                        singleLine = true,
                        placeholder = { Text("Ex: Weekend Worship, Live Sunday...", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SnapshotAccentBorder,
                            unfocusedBorderColor = Color(0x44FFFFFF),
                            cursorColor = SnapshotAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (hasActiveProject && projects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Setlist Mode:",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Compact side-by-side mode cards (fits landscape screen without truncation)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Option 1: Blank Setlist
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBlankSetlistMode) Color(0xFF1E293B) else Color(0xFF111622))
                                    .border(1.dp, if (isBlankSetlistMode) SnapshotAccentBorder else CardBorder, RoundedCornerShape(8.dp))
                                    .clickable { isBlankSetlistMode = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isBlankSetlistMode,
                                        onClick = { isBlankSetlistMode = true },
                                        colors = RadioButtonDefaults.colors(selectedColor = SnapshotAccent, unselectedColor = Color(0xFF64748B)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "New Blank",
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

                            // Option 2: Duplicate Current Setlist
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isBlankSetlistMode) Color(0xFF1E293B) else Color(0xFF111622))
                                    .border(1.dp, if (!isBlankSetlistMode) SnapshotAccentBorder else CardBorder, RoundedCornerShape(8.dp))
                                    .clickable { isBlankSetlistMode = false }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = !isBlankSetlistMode,
                                        onClick = { isBlankSetlistMode = false },
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
                    } else {
                        Text(
                            text = "Start a fresh setlist with clean tracks ready for performance.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newSetlistNameInput.trim().ifEmpty { "Setlist ${projects.size + 1}" }
                        onCreateNewProject(name, if (hasActiveProject && projects.isNotEmpty()) isBlankSetlistMode else true)
                        isNewSetlistDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccent)
                ) {
                    Text("Create & Open", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
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

    // ================= DELETE CONFIRMATION DIALOG =================
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
                Button(
                    onClick = {
                        onDeleteProject(setName)
                        pendingDeleteSetlistName = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("btn_confirm_delete_project")
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
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

    // ================= ROOT CONTAINER: OPEN STUDIO LAYOUT WITH FLOATING GLOW ORB =================
    AmbientStudioBackground(
        modifier = modifier
            .fillMaxSize()
            .testTag("projects_page")
    ) {
        // 2. Open Page Content (No enclosing box border)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Header + Divider
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. TOP HEADER BAR: Search Box + Title "Setlists" + "+ Create New Setlist" + Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Compact Search Bar
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(38.dp)
                            .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF283144), Color(0xFF1D2433))
                                )
                            )
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
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
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF8E99AF),
                                modifier = Modifier.size(16.dp)
                            )
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search setlists...",
                                    color = Color(0xFF6B7280),
                                    fontSize = 12.sp
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Center: Title "Setlists"
                    Text(
                        text = "Setlists",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Right: "+ Create New Setlist" Button + Settings Button immediately to its right
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .shadow(2.5.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2))
                                    )
                                )
                                .border(1.dp, Color(0x33000000), RoundedCornerShape(10.dp))
                                .drawWithContent {
                                    drawContent()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.25f),
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
                                .padding(horizontal = 14.dp)
                                .testTag("btn_create_setlist_header"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create",
                                    tint = Color(0xFF101726),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Create New Setlist",
                                    color = Color(0xFF101726),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Settings button placed next to Create New Setlist
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF283144), Color(0xFF1D2433))
                                    )
                                )
                                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
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
                                .clickable { onOpenSettings() }
                                .testTag("btn_setlist_settings_header"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Horizontal separator line below header
                HorizontalDivider(
                    color = CardBorder.copy(alpha = 0.75f),
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // MIDDLE SECTION: Recently used Carousel + All Setlists List (Extends down to Cancel/Load buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 2. SECTION: "Recently used Setlists" (Ultra Compact Horizontal Cards Carousel)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Recently used Setlists",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (projects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xB3283144))
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No setlists yet.\nTap Create to start.",
                                    color = Color(0xFF6B7280),
                                    fontSize = 10.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            projects.take(6).forEach { project ->
                                val isSelected = project.name == selectedSetlistName
                                RecentlyUsedSetlistCard(
                                    project = project,
                                    isSelected = isSelected,
                                    onSelect = { selectedSetlistName = project.name },
                                    onDelete = { pendingDeleteSetlistName = project.name }
                                )
                            }
                        }
                    }
                }

                // 3. SECTION: "All Setlists" (Full width table list view extending down to Cancel/Load buttons)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Sub-header with Sort options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "All Setlists",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Sort by",
                                color = Color(0xFF64748B),
                                fontSize = 10.5.sp
                            )

                            Box {
                                Row(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xD91B212F))
                                        .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                                        .clickable { isSortMenuOpen = true }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = sortOrder,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Sort Options",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = isSortMenuOpen,
                                    onDismissRequest = { isSortMenuOpen = false },
                                    modifier = Modifier.background(Color(0xFF1B212F))
                                ) {
                                    listOf("Recently used", "Alphabetical", "Date created").forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order, color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                sortOrder = order
                                                isSortMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { isSortMenuOpen = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Scrollable List of Setlists (Translucent backdrop to let glowing orb shine clearly through)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x80111520))
                            .border(1.dp, CardBorder.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (filteredProjects.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No setlists matching « $searchQuery »" else "No setlists found.",
                                        color = Color(0xFF6B7280),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(filteredProjects, key = { it.id }) { project ->
                                val isSelected = project.name == selectedSetlistName
                                SetlistTableRow(
                                    project = project,
                                    isSelected = isSelected,
                                    onSelect = { selectedSetlistName = project.name },
                                    onDoubleClick = { onOpenProject(project.name) },
                                    onDelete = { pendingDeleteSetlistName = project.name }
                                )
                            }
                        }
                    }
                }
            }

            // BOTTOM SECTION: Actions (No bottom divider line per user request)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel Button
                    Box(
                        modifier = Modifier
                            .height(38.dp)
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
                                if (projects.isNotEmpty()) {
                                    onOpenProject(selectedSetlistName.ifEmpty { projects.first().name })
                                }
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Load Setlist Button (in Glowing Snapshot Soft Blue with dark contrast text)
                    val isEnabled = selectedSetlistName.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .shadow(if (isEnabled) 3.dp else 0.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isEnabled) Brush.verticalGradient(listOf(Color(0xFF9BC0F8), Color(0xFF8FB6F2)))
                                else Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433)))
                            )
                            .border(
                                1.dp,
                                if (isEnabled) Color(0x33000000) else CardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .drawWithContent {
                                drawContent()
                                if (isEnabled) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.25f),
                                        start = Offset(4.dp.toPx(), 1f),
                                        end = Offset(size.width - 4.dp.toPx(), 1f),
                                        strokeWidth = 1.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            .clickable(enabled = isEnabled) {
                                onOpenProject(selectedSetlistName)
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Load Setlist",
                            color = if (isEnabled) Color(0xFF101726) else Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyUsedSetlistCard(
    project: ScenePreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(190.dp)
            .height(115.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color(0x77000000))
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SelectedCardBrush else CardBgBrush)
            .border(
                1.5.dp,
                if (isSelected) SnapshotAccentBorder else CardBorder,
                RoundedCornerShape(12.dp)
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = if (isSelected) 0.18f else 0.10f),
                    start = Offset(6.dp.toPx(), 1f),
                    end = Offset(size.width - 6.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable { onSelect() }
            .padding(12.dp)
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
                Text(
                    text = project.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { isMenuOpen = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1B212F))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 12.sp) },
                            onClick = {
                                isMenuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val patchesCount = project.patches.size
                val patchLabel = when (patchesCount) {
                    0 -> "0 Patches"
                    1 -> "1 Patch"
                    else -> "$patchesCount Patches"
                }
                Text(
                    text = patchLabel,
                    color = if (isSelected) SnapshotAccent else Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (project.timestamp.isNotEmpty()) "Last used: ${project.timestamp}" else "Recently created",
                    color = Color(0xFF64748B),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SetlistTableRow(
    project: ScenePreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDoubleClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val patchesCount = project.patches.size
    val patchLabel = when (patchesCount) {
        0 -> "0 Patches"
        1 -> "1 Patch"
        else -> "$patchesCount Patches"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (isSelected) Color(0x339BC0F8) else Color.Transparent)
            .clickable { onSelect() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Name & Indicator dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1.5f)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) SnapshotAccent else Color(0xFF475569))
            )
            Text(
                text = project.name,
                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Patches Count
        Text(
            text = patchLabel,
            color = if (isSelected) SnapshotAccent else Color(0xFF94A3B8),
            fontSize = 11.sp,
            modifier = Modifier.weight(0.8f)
        )

        // Date Created / Last used
        Text(
            text = if (project.timestamp.isNotEmpty()) "Last used: ${project.timestamp}" else "Active Setlist",
            color = Color(0xFF64748B),
            fontSize = 10.5.sp,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 3-dots Menu Button
        Box {
            IconButton(
                onClick = { isMenuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                modifier = Modifier.background(Color(0xFF1B212F))
            ) {
                DropdownMenuItem(
                    text = { Text("Open", color = Color.White, fontSize = 12.sp) },
                    onClick = {
                        isMenuOpen = false
                        onDoubleClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 12.sp) },
                    onClick = {
                        isMenuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}
