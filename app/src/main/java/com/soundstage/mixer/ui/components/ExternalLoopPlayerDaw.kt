package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.model.AudioStorageItem
import com.soundstage.mixer.model.LoopStepMode
import java.io.File
import java.util.Locale

// Professional Studio Slate-Blue Theme Colors (strictly matching the DrumPad/Mixer UI)
private val StudioSlateGradTop = Color(0xFF283245)
private val StudioSlateGradBottom = Color(0xFF1B2232)
private val StudioSlateBtnTop = Color(0xFF2E3B54)
private val StudioSlateBtnBottom = Color(0xFF1E273A)
private val StudioSlateBtnActiveTop = Color(0xFF2C4A78)
private val StudioSlateBtnActiveBottom = Color(0xFF1A3052)

private val StudioAccentBlue = Color(0xFF8BB5F8) // Pure Glacier Blue matching Tonic Pad slider
private val StudioAccentBlueDull = Color(0xFF536E9B)
private val StudioTextWhite = Color(0xFFF1F5F9)
private val StudioTextMuted = Color(0xFF94A3B8)
private val StudioScreenDarkBg = Color(0xFF0D121B)
private val StudioWaveformUnplayed = Color(0xFF3B4861)
private val StudioWaveformPlayed = Color(0xFF8BB5F8)

/**
 * Professional Studio-grade external loop player with physical 3D relief,
 * matching the authentic LiveKeys console aesthetic.
 */
@Composable
fun ExternalLoopPlayerDaw(
    fileName: String,
    filePath: String?,
    durationMs: Long,
    positionMs: Long,
    progress: Float,
    isPlaying: Boolean,
    isLooping: Boolean,
    pitchSemitones: Int,
    stepMode: String,
    waveform: FloatArray,
    volume: Float,
    currentExplorerPath: String,
    explorerItems: List<AudioStorageItem>,
    isExplorerOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onRetrigger: () -> Unit,
    onSeekProgress: (Float) -> Unit,
    onToggleLoop: () -> Unit,
    onChangePitch: (Int) -> Unit,
    onResetPitch: () -> Unit,
    onSelectStepMode: (String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleExplorer: () -> Unit,
    onNavigateExplorerFolder: (String) -> Unit,
    onSelectAudioFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPitchDialog by remember { mutableStateOf(false) }
    var showStepMenu by remember { mutableStateOf(false) }

    // Monolithic Sculpted Chassis with Physical Studio Relief
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF242C3E), Color(0xFF161C28))
                )
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .drawWithContent {
                drawContent()
                // Top specular highlight line
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(8.dp.toPx(), 1f),
                    end = Offset(size.width - 8.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .padding(7.dp)
            .testTag("external_loop_player_container")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ================= TOP HARDWARE HEADER BAR =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Name & Time Screen Badge (Recessed Hardware Display)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(1.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black)
                        .clip(RoundedCornerShape(7.dp))
                        .background(StudioScreenDarkBg)
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_aosp_audio_file),
                                contentDescription = "Audio File",
                                tint = if (isPlaying) StudioAccentBlue else StudioTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (fileName.isNotBlank()) fileName else "NO AUDIO LOADED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioTextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Time counter
                        Text(
                            text = formatTimeCounter(positionMs, durationMs),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = StudioTextMuted
                        )
                    }
                }

                // Relief Button: Browse Files / Storage Explorer
                StudioReliefButton(
                    iconRes = R.drawable.ic_aosp_folder,
                    label = if (isExplorerOpen) "PLAYER" else "FILES",
                    isActive = isExplorerOpen,
                    onClick = onToggleExplorer,
                    modifier = Modifier.width(74.dp)
                )

                // Relief Button: Pitch Shift Key
                StudioReliefButton(
                    iconRes = R.drawable.ic_daw_pitch,
                    label = if (pitchSemitones == 0) "KEY 0" else "${if (pitchSemitones > 0) "+" else ""}$pitchSemitones st",
                    isActive = pitchSemitones != 0,
                    onClick = { showPitchDialog = !showPitchDialog },
                    modifier = Modifier.width(72.dp)
                )

                // Relief Button: Step Quantize
                StudioReliefButton(
                    iconRes = R.drawable.ic_daw_step,
                    label = stepMode,
                    isActive = stepMode != "DEFAULT",
                    onClick = { showStepMenu = !showStepMenu },
                    modifier = Modifier.width(78.dp)
                )

                // Relief Button: Loop Toggle
                StudioReliefButton(
                    iconRes = R.drawable.ic_daw_loop,
                    label = if (isLooping) "LOOP ON" else "LOOP OFF",
                    isActive = isLooping,
                    onClick = onToggleLoop,
                    modifier = Modifier.width(78.dp)
                )
            }

            // ================= MAIN DECK BODY =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isExplorerOpen) {
                    // AOSP Internal Storage Browser View (Slate Relief Style)
                    StudioStorageBrowser(
                        currentPath = currentExplorerPath,
                        items = explorerItems,
                        onNavigate = onNavigateExplorerFolder,
                        onFileSelected = onSelectAudioFile,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // DAW Transport Controls & Waveform View
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Transport Controls Row (Hardware Buttons)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Retrigger Button
                            StudioReliefButton(
                                iconRes = R.drawable.ic_daw_retrigger,
                                label = "RETRIGGER",
                                onClick = onRetrigger,
                                modifier = Modifier.weight(1f)
                            )

                            // Play / Pause Button
                            StudioReliefButton(
                                iconRes = if (isPlaying) R.drawable.ic_daw_pause else R.drawable.ic_daw_play,
                                label = if (isPlaying) "PAUSE" else "PLAY",
                                isPrimary = true,
                                isActive = isPlaying,
                                onClick = onTogglePlayPause,
                                modifier = Modifier.weight(1.2f)
                            )

                            // Stop Button
                            StudioReliefButton(
                                iconRes = R.drawable.ic_daw_stop,
                                label = "STOP",
                                onClick = onStop,
                                modifier = Modifier.weight(1f)
                            )

                            // Loop Volume Slider in Relief Well
                            Box(
                                modifier = Modifier
                                    .weight(1.4f)
                                    .fillMaxHeight()
                                    .shadow(1.5.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(StudioSlateBtnTop, StudioSlateBtnBottom)
                                        )
                                    )
                                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "VOL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioTextMuted
                                    )
                                    Slider(
                                        value = volume,
                                        onValueChange = onVolumeChange,
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = StudioTextWhite,
                                            activeTrackColor = StudioAccentBlue,
                                            inactiveTrackColor = Color(0xFF141924)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Recessed Glass Waveform Display (Studio Hardware Screen)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioScreenDarkBg)
                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(8.dp))
                        ) {
                            DawWaveformCanvas(
                                waveform = waveform,
                                progress = progress,
                                onSeekProgress = onSeekProgress,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // ================= PITCH SHIFTER RELIEF POPUP =================
                if (showPitchDialog) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 74.dp)
                            .width(210.dp)
                            .shadow(6.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF28344A), Color(0xFF1A2232))
                                )
                            )
                            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LOOP PITCH SHIFT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioTextMuted
                                )
                                Text(
                                    text = "${if (pitchSemitones > 0) "+" else ""}$pitchSemitones ST",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StudioAccentBlue
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StudioMiniReliefButton(
                                    label = "-1 ST",
                                    onClick = { onChangePitch(pitchSemitones - 1) },
                                    modifier = Modifier.weight(1f)
                                )

                                StudioMiniReliefButton(
                                    label = "RESET",
                                    onClick = onResetPitch,
                                    modifier = Modifier.weight(1.2f)
                                )

                                StudioMiniReliefButton(
                                    label = "+1 ST",
                                    onClick = { onChangePitch(pitchSemitones + 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "Dedicated to loop playback. SoundFonts remain unaffected.",
                                fontSize = 8.5.sp,
                                color = StudioTextMuted
                            )
                        }
                    }
                }

                // ================= STEP QUANTIZATION RELIEF POPUP =================
                if (showStepMenu) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .width(170.dp)
                            .shadow(6.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF28344A), Color(0xFF1A2232))
                                )
                            )
                            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "LOOP STEP QUANTIZE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioTextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            LoopStepMode.values().forEach { mode ->
                                val isSelected = stepMode == mode.label
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) StudioSlateBtnActiveTop else Color.Transparent)
                                        .clickable {
                                            onSelectStepMode(mode.label)
                                            showStepMenu = false
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) StudioAccentBlue else StudioTextWhite
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

/**
 * Interactive Real-Time DAW Waveform Viewport with Vertical Playhead.
 */
@Composable
private fun DawWaveformCanvas(
    waveform: FloatArray,
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekProgress(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekProgress(frac)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // Studio Grid Lines
            val gridLines = 8
            for (g in 1 until gridLines) {
                val gx = (width / gridLines) * g
                drawLine(
                    color = Color(0x14FFFFFF),
                    start = Offset(gx, 0f),
                    end = Offset(gx, height),
                    strokeWidth = 1f
                )
            }
            drawLine(
                color = Color(0x1EFFFFFF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )

            // Render Real Waveform Bars
            if (waveform.isNotEmpty()) {
                val barCount = waveform.size
                val barWidth = width / barCount.toFloat()

                for (i in 0 until barCount) {
                    val peak = waveform[i]
                    val barHeight = (peak * (height * 0.88f)).coerceAtLeast(3f)
                    val x = i * barWidth
                    val isPast = (x / width) <= progress

                    val barColor = if (isPast) StudioWaveformPlayed else StudioWaveformUnplayed

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x + 0.5f, centerY - (barHeight / 2f)),
                        size = Size(maxOf(1f, barWidth - 1f), barHeight)
                    )
                }
            } else {
                drawLine(
                    color = Color(0x2AFFFFFF),
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 2f
                )
            }

            // Vertical Playhead Line
            val playheadX = (progress * width).coerceIn(0f, width)
            drawLine(
                color = Color.White,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 2.5f
            )

            // Playhead Cursor Indicator
            drawCircle(
                color = StudioAccentBlue,
                radius = 3.5f,
                center = Offset(playheadX, 4f)
            )
        }
    }
}

/**
 * Storage Browser with Studio Relief Styling.
 */
@Composable
private fun StudioStorageBrowser(
    currentPath: String,
    items: List<AudioStorageItem>,
    onNavigate: (String) -> Unit,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(8.dp))
            .background(StudioScreenDarkBg)
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Breadcrumb Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF131924), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val parentPath = File(currentPath).parent
            if (parentPath != null && parentPath.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(StudioSlateBtnTop)
                        .clickable { onNavigate(parentPath) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_aosp_parent_dir),
                        contentDescription = "Parent Directory",
                        tint = StudioTextWhite,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(
                text = currentPath.replace("/storage/emulated/0", "INTERNAL STORAGE"),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = StudioTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // File and Folder List
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO AUDIO FILES FOUND IN THIS DIRECTORY",
                    fontSize = 10.sp,
                    color = StudioTextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF131A26))
                            .clickable {
                                if (item.isDirectory) {
                                    onNavigate(item.path)
                                } else {
                                    onFileSelected(File(item.path))
                                }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (item.isDirectory) R.drawable.ic_aosp_folder else R.drawable.ic_aosp_audio_file
                            ),
                            contentDescription = if (item.isDirectory) "Directory" else "Audio File",
                            tint = if (item.isDirectory) StudioAccentBlue else StudioTextMuted,
                            modifier = Modifier.size(14.dp)
                        )

                        Text(
                            text = item.name,
                            fontSize = 11.sp,
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            color = StudioTextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (!item.isDirectory && item.sizeBytes > 0) {
                            Text(
                                text = formatFileSize(item.sizeBytes),
                                fontSize = 9.sp,
                                color = StudioTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hardware Button with 3D Bevel, Specular Highlight and Physical Relief.
 */
@Composable
private fun StudioReliefButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPrimary: Boolean = false
) {
    val bgGradient = if (isActive || isPrimary) {
        listOf(StudioSlateBtnActiveTop, StudioSlateBtnActiveBottom)
    } else {
        listOf(StudioSlateBtnTop, StudioSlateBtnBottom)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(bgGradient))
            .border(
                1.dp,
                if (isActive) StudioAccentBlue.copy(alpha = 0.6f) else Color(0x28FFFFFF),
                RoundedCornerShape(8.dp)
            )
            .drawWithContent {
                drawContent()
                // Top bevel specular line
                drawLine(
                    color = Color.White.copy(alpha = if (isActive) 0.35f else 0.18f),
                    start = Offset(4.dp.toPx(), 1f),
                    end = Offset(size.width - 4.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (isActive) StudioAccentBlue else StudioTextWhite,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) StudioAccentBlue else StudioTextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudioMiniReliefButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .shadow(1.5.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(StudioSlateBtnTop, StudioSlateBtnBottom)))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = StudioTextWhite
        )
    }
}

private fun formatTimeCounter(posMs: Long, durMs: Long): String {
    val curSec = (posMs / 1000).coerceAtLeast(0)
    val totSec = (durMs / 1000).coerceAtLeast(0)

    val curMin = curSec / 60
    val curRemSec = curSec % 60

    val totMin = totSec / 60
    val totRemSec = totSec % 60

    return String.format(Locale.US, "%02d:%02d / %02d:%02d", curMin, curRemSec, totMin, totRemSec)
}

private fun formatFileSize(bytes: Long): String {
    return if (bytes < 1024 * 1024) {
        "${bytes / 1024} KB"
    } else {
        String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
}
