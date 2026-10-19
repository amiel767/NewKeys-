package com.soundstage.mixer.ui.components.drumpad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.DrumKitData
import com.soundstage.mixer.model.DrumPadItem
import com.soundstage.mixer.model.StorageItem

// ─────────────────────────────────────────────────────────────────────────
// DpManager — fenêtre 4 onglets
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpManager(
    open: Boolean,
    tab: String,
    onTabChange: (String) -> Unit,
    openTarget: Int?,
    pads: List<DrumPadItem>,
    drumSamples: List<StorageItem>,
    loops: List<StorageItem>,
    kits: List<DrumKitData>,
    activeKitName: String,
    scenes: List<Any>,
    onLoadKit: (String) -> Unit,
    onDeleteKit: (String) -> Unit,
    onSaveCurrentAsKit: (String) -> Unit,
    onAssign: (padIndex: Int, sample: StorageItem) -> Unit,
    onAssignLoop: (padIndex: Int, sample: StorageItem) -> Unit,
    onImportFiles: (tab: String) -> Unit = {},
    onLoadScene: (Any) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!open) return

    var search by remember { mutableStateOf("") }
    var showSaveKitField by remember { mutableStateOf(false) }
    var newKitName by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(setOf<String>()) }
    // Target pad for the assignment grid. Seeded from the pad the window was
    // opened from, then adjustable from the bar itself.
    var assignTarget by remember(openTarget) { mutableStateOf(openTarget) }

    // Both sample and loop tabs can assign onto a DrumPad; the target pad comes
    // from the assignment bar, so the tab only decides which file list is shown.
    val assigning = (tab == "DrumPad" || tab == "Loops") && assignTarget != null
    val filteredSamples = drumSamples.filter { search.isEmpty() || it.name.contains(search, true) }
    val filteredLoops = loops.filter { search.isEmpty() || it.name.contains(search, true) }
    val filteredKits = kits.filter { search.isEmpty() || it.name.contains(search, true) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val windowW = min(maxWidth * 0.92f, 660.dp)
        val windowH = min(maxHeight * 0.88f, 372.dp)
        val sidebarW = (windowW * 0.23f).coerceIn(100.dp, 154.dp)

        // Backdrop
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x9E06090E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )

        // Window
        Column(
            Modifier
                .size(width = windowW, height = windowH)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230))))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume */ }
                .padding(12.dp)
        ) {
            // ─── Header ───
            Row(
                Modifier.fillMaxWidth().height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xB30F141D))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFFE9EDF5),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (search.isEmpty()) Text(
                                "Search ${tab.lowercase()}",
                                color = Color(0xFF8690A4),
                                fontSize = 13.sp
                            )
                            inner()
                        }
                    )
                }
                if (tab == "DrumPad" || tab == "Loops") {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF9DBDEB), Color(0xFF7FA3DD))))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onImportFiles(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color(0xFF0F1726), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color(0xFFE9EDF5), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Body ───
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // Sidebar
                Column(
                    Modifier
                        .width(sidebarW)
                        .fillMaxHeight()
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "BROWSER",
                        color = Color(0xFF7B859A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp
                    )
                    listOf("Kits", "DrumPad", "Loops", "Scenes").forEach { key ->
                        val active = tab == key
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) DpColors.ACCENT.copy(alpha = 0.14f)
                                    else Color(0xFF0F141D).copy(alpha = 0.6f)
                                )
                                .border(
                                    1.dp,
                                    if (active) DpColors.ACCENT.copy(alpha = 0.55f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    onTabChange(key)
                                    selection = emptySet()
                                    showSaveKitField = false
                                }
                                .padding(start = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                key,
                                color = if (active) DpColors.ACCENT else Color(0xFF9AA5BA),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Content
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (tab) {
                                "Kits" -> KitsContent(
                                    kits = filteredKits,
                                    activeKitName = activeKitName,
                                    showSaveField = showSaveKitField,
                                    newKitName = newKitName,
                                    onNewKitName = { newKitName = it },
                                    onStartSave = { showSaveKitField = true; newKitName = "" },
                                    onCancelSave = { showSaveKitField = false; newKitName = "" },
                                    onConfirmSave = {
                                        if (newKitName.isNotBlank()) {
                                            onSaveCurrentAsKit(newKitName.trim())
                                            showSaveKitField = false
                                            newKitName = ""
                                        }
                                    },
                                    onLoad = onLoadKit,
                                    onDelete = onDeleteKit,
                                )
                                "DrumPad" -> StorageListContent(
                                    items = filteredSamples,
                                    assigning = assigning,
                                    selection = selection,
                                    pads = pads,
                                    onItemClick = { sample ->
                                        if (assigning && assignTarget != null) {
                                            onAssign(assignTarget!!, sample)
                                        } else {
                                            selection = if (selection.contains(sample.name))
                                                selection - sample.name
                                            else selection + sample.name
                                        }
                                    },
                                    emptyText = "No samples"
                                )
                                "Loops" -> StorageListContent(
                                    items = filteredLoops,
                                    assigning = assigning,
                                    selection = selection,
                                    pads = pads,
                                    onItemClick = { loop ->
                                        if (assigning && assignTarget != null) {
                                            onAssignLoop(assignTarget!!, loop)
                                        } else {
                                            selection = if (selection.contains(loop.name))
                                                selection - loop.name
                                            else selection + loop.name
                                        }
                                    },
                                    emptyText = "No loops"
                                )
                                "Scenes" -> ScenesContent(scenes = scenes, onLoad = onLoadScene)
                            }
                        }
                        if (assigning && assignTarget != null) {
                            Spacer(Modifier.height(8.dp))
                            AssignBar(
                                pads = pads,
                                target = assignTarget!!,
                                onSelectTarget = { assignTarget = it },
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Kits
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun KitsContent(
    kits: List<DrumKitData>,
    activeKitName: String,
    showSaveField: Boolean,
    newKitName: String,
    onNewKitName: (String) -> Unit,
    onStartSave: () -> Unit,
    onCancelSave: () -> Unit,
    onConfirmSave: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(kits, key = { it.name }) { kit ->
                val active = kit.name == activeKitName
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(listOf(
                                Color.White.copy(alpha = if (active) 0.12f else 0.065f),
                                Color.White.copy(alpha = if (active) 0.07f else 0.03f)
                            ))
                        )
                        .border(
                            1.dp,
                            if (active) Color.White.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLoad(kit.name) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            kit.name,
                            color = Color(0xFFE9EDF5),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (i in 0 until 8) {
                                val hasSample = kit.padSamples.getOrNull(i) != null
                                Box(
                                    Modifier
                                        .width(14.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (hasSample) DpPalette.at(i)
                                            else Color.White.copy(alpha = 0.10f)
                                        )
                                )
                            }
                        }
                    }
                    if (active) {
                        Text(
                            "ACTIVE",
                            color = Color(0xFFC7CFDD),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDelete(kit.name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color(0xFF6F7A90), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (showSaveField) {
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F141D))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = newKitName,
                        onValueChange = onNewKitName,
                        singleLine = true,
                        textStyle = TextStyle(color = Color(0xFFE9EDF5), fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (newKitName.isEmpty()) Text(
                                "Kit name…",
                                color = Color(0xFF6F7A90),
                                fontSize = 13.sp
                            )
                            inner()
                        }
                    )
                }
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DpColors.ACCENT.copy(alpha = 0.20f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onConfirmSave() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Save", color = DpColors.ACCENT, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCancelSave() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color(0xFFC7CFDD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onStartSave() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+ Save current pads as a kit",
                    color = Color(0xFF9AA5BA),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Storage list (samples + loops)
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun StorageListContent(
    items: List<StorageItem>,
    assigning: Boolean,
    selection: Set<String>,
    pads: List<DrumPadItem>,
    onItemClick: (StorageItem) -> Unit,
    emptyText: String,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = Color(0xFF6F7A90), fontSize = 13.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items, key = { it.name }) { s ->
            val on = selection.contains(s.name)
            val usedBy = pads.firstOrNull { it.sampleFileName == s.name }?.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(listOf(
                            Color.White.copy(alpha = if (on) 0.16f else 0.065f),
                            Color.White.copy(alpha = if (on) 0.08f else 0.03f)
                        ))
                    )
                    .border(
                        1.dp,
                        if (on) DpColors.ACCENT.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onItemClick(s) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!assigning) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) DpColors.ACCENT else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (on) Color.Transparent else Color.White.copy(alpha = 0.22f),
                                RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Text(
                            "✓",
                            color = Color(0xFF0F1726),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    s.name,
                    color = Color(0xFFE9EDF5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (usedBy != null && !assigning) {
                    Text(
                        "P$usedBy",
                        color = Color(0xFFB5BFD3),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                if (s.formattedSize.isNotEmpty()) {
                    Text(
                        s.formattedSize,
                        color = Color(0xFF7B859A),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Scenes
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun ScenesContent(
    scenes: List<Any>,
    onLoad: (Any) -> Unit,
) {
    if (scenes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Scenes available in next update",
                color = Color(0xFF6F7A90),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(scenes.size) { idx ->
            val scene = scenes[idx]
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(listOf(
                            Color.White.copy(alpha = 0.065f),
                            Color.White.copy(alpha = 0.03f)
                        ))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLoad(scene) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DpColors.ACCENT.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♫", color = DpColors.ACCENT, fontSize = 16.sp)
                }
                Text(
                    "Scene ${idx + 1}",
                    color = Color(0xFFE9EDF5),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "LOAD",
                    color = Color(0xFFC7CFDD),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Assign bar (one cell per DrumPad; 8 or 16 depending on the active grid)
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun AssignBar(
    pads: List<DrumPadItem>,
    target: Int,
    onSelectTarget: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follow the real pad list so a 16-pad grid shows 16 assignable cells.
    val count = pads.size.coerceAtLeast(8)
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3447), Color(0xFF1E2736))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (count > 8) 3.dp else 5.dp)
    ) {
        for (i in 0 until count) {
            val pad = pads.getOrNull(i)
            // Identity comes from the pad itself; the index is only a fallback.
            val padId = pad?.id ?: (i + 1)
            val isTarget = padId == target
            val color = DpPalette.at(i)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(if (count > 8) 8.dp else 11.dp))
                    .background(
                        Brush.linearGradient(listOf(
                            color.copy(alpha = if (isTarget) 0.53f else 0.19f),
                            Color(0xFF1F2739)
                        ))
                    )
                    .border(
                        if (isTarget) 1.5.dp else 0.dp,
                        if (isTarget) color else Color.Transparent,
                        RoundedCornerShape(if (count > 8) 8.dp else 11.dp)
                    )
                    .padding(horizontal = if (count > 8) 3.dp else 5.dp, vertical = 4.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelectTarget(padId) }
            ) {
                Column {
                    Text(
                        "D$padId",
                        color = if (isTarget) Color.White else Color(0xFF8E99AE),
                        fontSize = if (count > 8) 8.5.sp else 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text(
                        pad?.label ?: "+",
                        color = if (pad != null) Color(0xFFE9EDF5) else Color(0xFF566075),
                        fontSize = if (count > 8) 8.5.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
