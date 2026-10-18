package com.soundstage.mixer.ui.components.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SubSceneSnapshot
import com.soundstage.mixer.model.TrackChannel
import kotlin.math.roundToInt

@Composable
fun MixerBottomBarV2(
    masterTrack: TrackChannel,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterFxClick: () -> Unit, // Fix: ajouté
    bpm: Int, onBpmChange: (Int) -> Unit,
    isSnapshotArmMode: Boolean, onToggleSnapshotArm: () -> Unit,
    activeSnapshotSlot: String?,
    snapshots: Map<String, SubSceneSnapshot>,
    configuredSnapshotSlots: List<String>,
    onSnapshotSlotClick: (String) -> Unit,
    onAddSnapshot: (String) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    snapshotCustomNames: Map<String, String>,
    onRenameSnapshotSlot: (String, String) -> Unit,
    isKeyboardActive: Boolean, onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var manageSlotKey by remember { mutableStateOf<String?>(null) }
    val pT = Color(0xFF283144); val pB = Color(0xFF1D2433)
    val tM = Color(0xFFE9EDF5); val ac = Color(0xFF8FB2E8)

    Row(modifier.height(42.dp), // Fix: 42dp au lieu de 48
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        
        ModuleBox(80.dp) { // Fix: 80dp au lieu de 94
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF3CC48A)))
                    Text("CPU 15%", color = tM, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) // Fix: 9sp
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4BA8F0)))
                    Text("RAM 325", color = tM, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        
        ModuleBox(80.dp) { // Fix: 80dp au lieu de 92
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("−", color = tM, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBpmChange(bpm - 1) }.padding(4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$bpm", color = tM, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                    Text("BPM", color = Color(0xFF7B859A), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 7.sp)
                }
                Text("+", color = tM, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBpmChange(bpm + 1) }.padding(4.dp))
            }
        }

        val isFlex = configuredSnapshotSlots.size > 3
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            configuredSnapshotSlots.forEachIndexed { idx, key ->
                val displayName = snapshotCustomNames[key] ?: if (key == "slot_default") "Default" else "Snapshot $idx"
                val isActive = activeSnapshotSlot == key || (activeSnapshotSlot == null && key == "slot_default")
                val isDeletable = key != "slot_default"
                SnapshotCaseV2(key, displayName, isActive, isDeletable, isFlex,
                    onClick = { onSnapshotSlotClick(key) },
                    onLongPress = { manageSlotKey = key },
                    onDelete = { onDeleteSnapshot(key) })
            }
            if (configuredSnapshotSlots.size < 5) {
                Box(Modifier.width(32.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAddSnapshot("Snapshot ${configuredSnapshotSlots.size}") },
                    contentAlignment = Alignment.Center) {
                    Text("+", color = Color(0xFF8E99AD), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Box(Modifier.size(38.dp, 42.dp).clip(RoundedCornerShape(10.dp))
                .background(if (isSnapshotArmMode) Brush.verticalGradient(listOf(Color(0x47FF2A55), Color(0x24FF2A55))) else Brush.verticalGradient(listOf(pT, pB)))
                .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleSnapshotArm() },
            contentAlignment = Alignment.Center) {
            IconSaveV2(size = 18.dp, color = if (isSnapshotArmMode) Color(0xFFFF2A55) else Color(0xFFE9EDF5))
        }

        // Fix: Master Box modifié avec bouton FX
        ModuleBox(140.dp) {
            MasterSliderCompact(masterTrack.volume, onMasterVolumeChange, masterTrack.peakMeterL, masterTrack.peakMeterR, onMasterFxClick)
        }

        Box(Modifier.size(42.dp, 42.dp).clip(RoundedCornerShape(10.dp))
                .background(if (isKeyboardActive) Brush.verticalGradient(listOf(Color(0x479BC0F8), Color(0x249BC0F8))) else Brush.verticalGradient(listOf(pT, pB)))
                .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleKeyboard() },
            contentAlignment = Alignment.Center) {
            IconPianoV2(size = 20.dp, color = if (isKeyboardActive) ac else Color(0xFFC9D1E0))
        }
    }

    manageSlotKey?.let { key ->
        ManageSnapshotDialogV2(
            slotKey = key,
            currentName = snapshotCustomNames[key] ?: if (key == "slot_default") "Default" else "Snapshot",
            onDismiss = { manageSlotKey = null },
            onSave = { newName, _ -> onRenameSnapshotSlot(key, newName); manageSlotKey = null })
    }
}

@Composable
private fun RowScope.SnapshotCaseV2(
    key: String, displayName: String, isActive: Boolean,
    isDeletable: Boolean, isFlex: Boolean,
    onClick: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    val animatedY by animateFloatAsState(dragY, animationSpec = if (dragY > 0f) tween(0) else spring(0.6f, 500f), label = "snapY")
    val isSwiping = dragY > 30f
    val splitParts = remember(displayName) {
        val m = Regex("^Snapshot\\s+(.+)$", RegexOption.IGNORE_CASE).find(displayName)
        if (m != null) Pair("SNAPSHOT", m.groupValues[1]) else null
    }
    Box(modifier = Modifier
        .then(if (isFlex) Modifier.weight(1f) else Modifier.width(90.dp)) // Fix: 90dp
        .fillMaxHeight()
        .offset { IntOffset(0, animatedY.roundToInt()) }
        .clip(RoundedCornerShape(10.dp)) // Fix: 10dp
        .background(if (isActive) Brush.verticalGradient(listOf(Color(0xFF8FB2E8), Color(0xFF7FA3DD))) else Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433))))
        .border(1.dp, if (isActive) Color(0x998FB2E8) else Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
        .pointerInput(key) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, delta ->
                    change.consume()
                    if (delta > 0 && isDeletable) dragY = (dragY + delta).coerceAtMost(60f)
                },
                onDragEnd = { if (dragY > 30f && isDeletable) onDelete(); dragY = 0f },
                onDragCancel = { dragY = 0f })
        }
        .pointerInput(key) {
            detectTapGestures(onTap = { if (!isSwiping) onClick() }, onLongPress = { onLongPress() })
        },
        contentAlignment = Alignment.Center) {
        if (splitParts != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SNAPSHOT", color = Color(0x99FFFFFF), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, lineHeight = 8.sp)
                Text(splitParts.second, color = if (isActive) Color(0xFF0F1726) else Color(0xFFDDE3EE), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(displayName, color = if (isActive) Color(0xFF0F1726) else Color(0xFFDDE3EE), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MasterSliderCompact(
    value: Float, onValueChange: (Float) -> Unit, peakL: Float, peakR: Float, onFxClick: () -> Unit
) {
    var localPeak by remember { mutableFloatStateOf(0f) }
    // Fix: rememberUpdatedState pour que le drag lise la bonne valeur
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(peakL, peakR) {
        val target = value * maxOf(peakL, peakR)
        if (target > localPeak) localPeak = target
    }
    
    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("MASTER", color = Color(0xFF7B859A), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("${(value * 100).roundToInt()}%",
                    color = if (value > 0.75f) Color(0xFFD8A24A) else Color(0xFFF1F4FA),
                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
            }
            Box(Modifier.fillMaxWidth().height(18.dp).pointerInput(Unit) {
                detectHorizontalDragGestures { change, delta ->
                    change.consume()
                    onValueChange((currentValue + delta / size.width.toFloat()).coerceIn(0f, 1f)) // Fix: currentValue
                }
            }) {
                Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).clip(RoundedCornerShape(2.dp)).background(Color(0xFF0E131C)))
                Box(Modifier.fillMaxWidth(value).height(4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(Color(0x8C8FB2E8), Color(0xFF8FB2E8)))))
                if (localPeak > 0.01f) {
                    Box(Modifier.fillMaxHeight().width(2.dp).offset(x = (localPeak * 100).dp).background(Color(0xFFA9C6F0), RoundedCornerShape(1.dp)))
                }
                Box(Modifier.offset { IntOffset((value * 100).roundToInt(), 0) }
                    .size(14.dp, 16.dp).align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF6E7799), Color(0xFF4B5373), Color(0xFF3F4766))))
                    .border(1.dp, Color(0x29FFFFFF), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth(0.6f).height(1.5.dp).background(Color(0xFF8FB2E8), RoundedCornerShape(1.dp)))
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        // Fix: Ajout du bouton FX
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF283144))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFxClick() },
            contentAlignment = Alignment.Center) {
            Text("FX", color = Color(0xFF8FB2E8), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ModuleBox(width: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(10.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433))))
        .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(10.dp))) { content() }
}
