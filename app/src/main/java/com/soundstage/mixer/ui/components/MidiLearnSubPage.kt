package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.*
import com.soundstage.mixer.ui.theme.TextDim
import com.soundstage.mixer.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.abs

private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun MidiLearnSubPage(
    mappings: List<MidiMappingItem>,
    learningActionType: MidiActionType?,
    lastMidiSummary: String,
    connectedMidiCount: Int,
    onStartLearn: (MidiActionType) -> Unit,
    onCancelLearn: () -> Unit,
    onUnmapAction: (MidiActionType) -> Unit,
    onToggleMappingEnabled: (MidiActionType) -> Unit,
    onUpdateManualMapping: (MidiActionType, MidiControlType, Int, Int) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    onExpandedChange: (Boolean) -> Unit = {},
    midiDebounceEnabled: Boolean = false,
    midiDebounceMs: Long = 60L,
    onToggleMidiDebounce: () -> Unit = {},
    onUpdateMidiDebounceMs: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Navigation State: null = Level 1 (Catalogue), non-null = Level 2 (Category Actions)
    var selectedCategory by remember { mutableStateOf<MidiActionCategory?>(null) }

    LaunchedEffect(selectedCategory) {
        onExpandedChange(selectedCategory != null)
    }
    // Manual Edit State: non-null = Level 3 (Manual Wheel Edit Page)
    var manualEditAction by remember { mutableStateOf<MidiMappingItem?>(null) }

    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("midi_learn_page")
    ) {
        when {
            // LEVEL 3: MANUAL EDIT WHEEL PAGE
            manualEditAction != null -> {
                val currentAction = manualEditAction!!
                ManualMidiEditPage(
                    item = currentAction,
                    onBack = { manualEditAction = null },
                    onApply = { type, channel, ctrlNum ->
                        onUpdateManualMapping(currentAction.actionType, type, channel, ctrlNum)
                        manualEditAction = null
                    },
                    onUnmap = {
                        onUnmapAction(currentAction.actionType)
                        manualEditAction = null
                    }
                )
            }

            // LEVEL 2: CATEGORY ACTIONS LIST
            selectedCategory != null -> {
                val category = selectedCategory!!
                val categoryMappings = remember(mappings, category) {
                    mappings.filter { it.actionType.category == category }
                }

                CategoryActionsListPage(
                    category = category,
                    mappings = categoryMappings,
                    learningActionType = learningActionType,
                    lastMidiSummary = lastMidiSummary,
                    onStartLearn = onStartLearn,
                    onCancelLearn = onCancelLearn,
                    onUnmapAction = onUnmapAction,
                    onToggleMappingEnabled = onToggleMappingEnabled,
                    onEditManual = { item -> manualEditAction = item },
                    onBack = { selectedCategory = null }
                )
            }

            // LEVEL 1: CATALOGUE OF SECTIONS (AOSP CARD RELIEF STYLE)
            else -> {
                MidiSectionsCataloguePage(
                    mappings = mappings,
                    connectedMidiCount = connectedMidiCount,
                    lastMidiSummary = lastMidiSummary,
                    midiDebounceEnabled = midiDebounceEnabled,
                    midiDebounceMs = midiDebounceMs,
                    onToggleMidiDebounce = onToggleMidiDebounce,
                    onUpdateMidiDebounceMs = onUpdateMidiDebounceMs,
                    onSelectCategory = { category -> selectedCategory = category },
                    onResetDefaults = { showResetConfirm = true },
                    onClearAll = { showClearConfirm = true },
                    onBack = onBack
                )
            }
        }
    }

    // CONFIRMATION DIALOGS
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Mappings") },
            text = { Text("Restore factory default MIDI assignments?") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDefaults()
                        showResetConfirm = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Mappings") },
            text = { Text("All assigned MIDI controls will be removed. Continue?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ======================================================================================
// LEVEL 1: CATALOGUE OF SECTIONS (Matches Screenshot 2 AOSP Card Style)
// ======================================================================================
@Composable
private fun MidiSectionsCataloguePage(
    mappings: List<MidiMappingItem>,
    connectedMidiCount: Int,
    lastMidiSummary: String,
    midiDebounceEnabled: Boolean,
    midiDebounceMs: Long,
    onToggleMidiDebounce: () -> Unit,
    onUpdateMidiDebounceMs: (Long) -> Unit,
    onSelectCategory: (MidiActionCategory) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onBack() }
                        .testTag("midi_catalogue_back_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "MIDI Learn & Controllers",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (connectedMidiCount > 0) "$connectedMidiCount active device(s) connected" else "No USB MIDI device detected",
                        fontSize = 11.sp,
                        color = if (connectedMidiCount > 0) Color(0xFF10B981) else TextDim
                    )
                }
            }
        }

        // LIVE MONITOR (If active message received)
        if (lastMidiSummary.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2433))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "Last MIDI Event: $lastMidiSummary",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // SECTIONS LIST
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 1: SECTIONS
            item {
                MidiReliefCard(title = "MIDI ACTION CATEGORIES") {
                    Column {
                        val categories = listOf(
                            MidiCategoryMeta(
                                category = MidiActionCategory.SNAPSHOTS,
                                icon = Icons.Default.CollectionsBookmark,
                                iconTint = Color(0xFF38BDF8),
                                iconBg = Color(0x1838BDF8)
                            ),
                            MidiCategoryMeta(
                                category = MidiActionCategory.PATCHES,
                                icon = Icons.Default.LibraryMusic,
                                iconTint = Color(0xFFA855F7),
                                iconBg = Color(0x18A855F7)
                            ),
                            MidiCategoryMeta(
                                category = MidiActionCategory.TRACKS,
                                icon = Icons.Default.Tune,
                                iconTint = Color(0xFF10B981),
                                iconBg = Color(0x1810B981)
                            ),
                            MidiCategoryMeta(
                                category = MidiActionCategory.DRUMPAD,
                                icon = Icons.Default.Apps,
                                iconTint = Color(0xFFF97316),
                                iconBg = Color(0x18F97316)
                            ),
                            MidiCategoryMeta(
                                category = MidiActionCategory.GLOBAL,
                                icon = Icons.Default.VolumeUp,
                                iconTint = Color(0xFFEAB308),
                                iconBg = Color(0x18EAB308)
                            )
                        )

                        categories.forEachIndexed { index, meta ->
                            val assignedCount = mappings.count { it.actionType.category == meta.category && it.isAssigned }
                            val totalCount = mappings.count { it.actionType.category == meta.category }

                            MidiSettingRow(
                                icon = meta.icon,
                                iconTint = meta.iconTint,
                                iconBg = meta.iconBg,
                                title = meta.category.displayName,
                                subtitle = "${meta.category.subtitle} ($assignedCount/$totalCount mapped)",
                                onClick = { onSelectCategory(meta.category) }
                            )

                            if (index < categories.size - 1) {
                                MidiRowDivider()
                            }
                        }
                    }
                }
            }

            // CARD 2: SMART HARDWARE FILTER (ANTI-JITTER / DEBOUNCE)
            item {
                MidiReliefCard(title = "SMART HARDWARE FILTER (ANTI-JITTER)") {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleMidiDebounce() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x1810B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Debounce",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Anti-Jitter / Debounce Filter",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Eliminates parasitic burst signals from worn buttons & encoders",
                                        fontSize = 11.sp,
                                        color = TextDim
                                    )
                                }
                            }
                            Switch(
                                checked = midiDebounceEnabled,
                                onCheckedChange = { onToggleMidiDebounce() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }

                        if (midiDebounceEnabled) {
                            MidiRowDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Filter Sensitivity Window",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Text(
                                        text = "${midiDebounceMs} ms",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = midiDebounceMs.toFloat().coerceIn(5f, 120f),
                                    onValueChange = { onUpdateMidiDebounceMs(it.toLong()) },
                                    valueRange = 5f..120f,
                                    steps = 22,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF10B981),
                                        activeTrackColor = Color(0xFF10B981),
                                        inactiveTrackColor = Color(0xFF334155)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // CARD 3: MANAGEMENT & ACTIONS
            item {
                MidiReliefCard(title = "MANAGEMENT & PRESETS") {
                    Column {
                        MidiSettingRow(
                            icon = Icons.Default.Refresh,
                            iconTint = Color(0xFFF59E0B),
                            iconBg = Color(0x18F59E0B),
                            title = "Reset to Defaults",
                            subtitle = "Restore standard CC#7 volume & snapshot mappings",
                            onClick = onResetDefaults
                        )

                        MidiRowDivider()

                        MidiSettingRow(
                            icon = Icons.Default.DeleteSweep,
                            iconTint = Color(0xFFEF4444),
                            iconBg = Color(0x18EF4444),
                            title = "Clear All Mappings",
                            subtitle = "Unassign all controllers and reset to blank",
                            onClick = onClearAll
                        )
                    }
                }
            }
        }
    }
}

private data class MidiCategoryMeta(
    val category: MidiActionCategory,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color
)

// ======================================================================================
// LEVEL 2: CATEGORY ACTIONS LIST
// ======================================================================================
@Composable
private fun CategoryActionsListPage(
    category: MidiActionCategory,
    mappings: List<MidiMappingItem>,
    learningActionType: MidiActionType?,
    lastMidiSummary: String,
    onStartLearn: (MidiActionType) -> Unit,
    onCancelLearn: () -> Unit,
    onUnmapAction: (MidiActionType) -> Unit,
    onToggleMappingEnabled: (MidiActionType) -> Unit,
    onEditManual: (MidiMappingItem) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = category.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = category.subtitle,
                        fontSize = 11.sp,
                        color = TextDim
                    )
                }
            }

            // Compact Listening Badge aligned at header position (Requested in Screenshot 1)
            if (learningActionType != null) {
                val infiniteTransition = rememberInfiniteTransition(label = "learning_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.90f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = pulseAlpha * 0.22f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Text(
                        text = "Listening: ${learningActionType.title}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { onCancelLearn() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // ACTIONS LIST CARD (TWO PARALLEL PANES TO PREVENT ENDLESS SCROLLING)
        val halfCount = (mappings.size + 1) / 2
        val col1Items = mappings.take(halfCount)
        val col2Items = mappings.drop(halfCount)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pane 1 (Left Column) - Fixed Immovable Header Bar + Scrollable List inside
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF222836), Color(0xFF191F2C))
                        )
                    )
                    .border(1.dp, Color(0x22475569), RoundedCornerShape(16.dp))
            ) {
                // FIXED HEADER BAR (NEVER SCROLLS)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1F2C))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "${category.displayName.uppercase()} ${if (col2Items.isNotEmpty()) "(PART 1)" else "ACTIONS"}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x33475569))
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(col1Items.size) { index ->
                        val item = col1Items[index]
                        ActionMappingRow(
                            item = item,
                            isLearning = (learningActionType == item.actionType),
                            onStartLearn = { onStartLearn(item.actionType) },
                            onUnmap = { onUnmapAction(item.actionType) },
                            onToggleEnabled = { onToggleMappingEnabled(item.actionType) },
                            onEditManual = { onEditManual(item) }
                        )

                        if (index < col1Items.size - 1) {
                            MidiRowDivider()
                        }
                    }
                }
            }

            // Pane 2 (Right Column) - Fixed Immovable Header Bar + Scrollable List inside
            if (col2Items.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF222836), Color(0xFF191F2C))
                            )
                        )
                        .border(1.dp, Color(0x22475569), RoundedCornerShape(16.dp))
                ) {
                    // FIXED HEADER BAR (NEVER SCROLLS)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1F2C))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "${category.displayName.uppercase()} (PART 2)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.8.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x33475569))
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(col2Items.size) { index ->
                            val item = col2Items[index]
                            ActionMappingRow(
                                item = item,
                                isLearning = (learningActionType == item.actionType),
                                onStartLearn = { onStartLearn(item.actionType) },
                                onUnmap = { onUnmapAction(item.actionType) },
                                onToggleEnabled = { onToggleMappingEnabled(item.actionType) },
                                onEditManual = { onEditManual(item) }
                            )

                            if (index < col2Items.size - 1) {
                                MidiRowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMappingRow(
    item: MidiMappingItem,
    isLearning: Boolean,
    onStartLearn: () -> Unit,
    onUnmap: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEditManual: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditManual() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Column: Action Details
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.actionType.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isEnabled) Color.White else TextDim
                )
                if (item.actionType.isContinuous) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x223B82F6))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Fader",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
            }

            Text(
                text = item.actionType.subtitle,
                fontSize = 10.sp,
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Mapped status tag
            if (item.isAssigned) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.isEnabled) Color(0x2610B981) else Color(0x18FFFFFF))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.formatDescription(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isEnabled) Color(0xFF34D399) else TextDim
                    )
                }
            } else {
                Text(
                    text = "Unassigned",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Right Actions: Learn Button & Chevron to Manual Edit
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLearning) Color(0xFFF59E0B)
                        else if (item.isAssigned) Color(0x223B82F6)
                        else Color(0xFF3B82F6)
                    )
                    .clickable { onStartLearn() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isLearning) "Listening..." else "Learn",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLearning) Color.Black else if (item.isAssigned) Color(0xFF93C5FD) else Color.White
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Manual Edit",
                tint = TextDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ======================================================================================
// LEVEL 3: MANUAL EDIT WHEEL PAGE (No popup - Full sub-page with horizontal carousel)
// ======================================================================================
@Composable
private fun ManualMidiEditPage(
    item: MidiMappingItem,
    onBack: () -> Unit,
    onApply: (MidiControlType, Int, Int) -> Unit,
    onUnmap: () -> Unit
) {
    var selectedType by remember { mutableStateOf(item.midiType) }
    // Channel: -1 = Omni, 0..15 = Ch 1..16
    var selectedChannel by remember { mutableStateOf(item.channel) }
    // Control Number: 0..127
    var selectedNumber by remember { mutableStateOf(if (item.controlNumber >= 0) item.controlNumber else 64) }

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Manual Assignment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = item.actionType.title,
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.isAssigned) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x18EF4444))
                        .clickable { onUnmap() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Unassign",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SECTION 1: EVENT TYPE SELECTOR
            item {
                MidiReliefCard(title = "MIDI EVENT TYPE") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MidiControlType.values().forEach { type ->
                            val isSel = (type == selectedType)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color(0xFF3B82F6) else Color(0x18FFFFFF))
                                    .clickable { selectedType = type }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (type) {
                                        MidiControlType.CC -> "CC"
                                        MidiControlType.NOTE -> "Note"
                                        MidiControlType.PROGRAM_CHANGE -> "Prog Change"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: MIDI CHANNEL HORIZONTAL WHEEL CAROUSEL (Omni + 1..16)
            item {
                MidiReliefCard(title = "MIDI CHANNEL (OMNI / 1 TO 16)") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        val channelsList = remember {
                            listOf(-1) + (0..15).toList()
                        }
                        val channelListState = rememberLazyListState(
                            initialFirstVisibleItemIndex = (selectedChannel + 1).coerceIn(0, 16)
                        )
                        val coroutineScope = rememberCoroutineScope()

                        Text(
                            text = if (selectedChannel < 0) "OMNI (Receives on all channels)" else "Channel ${selectedChannel + 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                        // Carousel Row with centered selection & semi-transparent side peek
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LazyRow(
                                state = channelListState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(channelsList) { ch ->
                                    val isSelected = (ch == selectedChannel)
                                    val label = if (ch < 0) "Omni" else "${ch + 1}"

                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 48.dp else 40.dp)
                                            .scale(if (isSelected) 1.0f else 0.85f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) Color(0xFF3B82F6)
                                                else Color(0x18FFFFFF)
                                            )
                                            .clickable {
                                                selectedChannel = ch
                                                coroutineScope.launch {
                                                    val targetIdx = channelsList.indexOf(ch)
                                                    channelListState.animateScrollToItem(
                                                        (targetIdx - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = if (isSelected) 14.sp else 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color(0x66FFFFFF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: CONTROL NUMBER HORIZONTAL WHEEL (0..127)
            item {
                val numberLabel = when (selectedType) {
                    MidiControlType.CC -> "CC NUMBER (0 - 127)"
                    MidiControlType.NOTE -> "NOTE NUMBER (0 - 127)"
                    MidiControlType.PROGRAM_CHANGE -> "PROGRAM NUMBER (0 - 127)"
                }

                MidiReliefCard(title = numberLabel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Display Value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // -10 Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .clickable { selectedNumber = (selectedNumber - 10).coerceIn(0, 127) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-10", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }

                            // -1 Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .clickable { selectedNumber = (selectedNumber - 1).coerceIn(0, 127) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }

                            // Center Value Box
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x333B82F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "#$selectedNumber",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (selectedType == MidiControlType.NOTE) {
                                        val octave = (selectedNumber / 12) - 1
                                        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                                        val noteName = noteNames[selectedNumber % 12] + octave
                                        Text(
                                            text = noteName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF60A5FA)
                                        )
                                    }
                                }
                            }

                            // +1 Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .clickable { selectedNumber = (selectedNumber + 1).coerceIn(0, 127) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "+1", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }

                            // +10 Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .clickable { selectedNumber = (selectedNumber + 10).coerceIn(0, 127) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+10", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fast Scrub Slider
                        Slider(
                            value = selectedNumber.toFloat(),
                            onValueChange = { selectedNumber = it.toInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3B82F6),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                }
            }

            // APPLY BUTTON
            item {
                Button(
                    onClick = {
                        onApply(selectedType, selectedChannel, selectedNumber)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text(
                        text = "Apply Mapping",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ======================================================================================
// REUSABLE AOSP RELIEF CARD & ROW COMPONENTS (Exact Screenshot 2 styling)
// ======================================================================================
@Composable
private fun MidiReliefCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF222836), Color(0xFF191F2C))
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.10f),
                        start = Offset(6.dp.toPx(), 1f),
                        end = Offset(size.width - 6.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun MidiSettingRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color = Color(0x18FFFFFF),
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Squircle Icon Container
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = TextDim,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MidiRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 14.dp)
            .background(Color.White.copy(alpha = 0.04f))
    )
}
