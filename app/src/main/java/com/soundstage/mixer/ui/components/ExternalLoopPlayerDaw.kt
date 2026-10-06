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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.audio.AudioKeyBpmDetector
import com.soundstage.mixer.model.AudioStorageItem
import java.io.File

// Studio Relief Theme Colors (Matching TonicPad & TopBar Studio Palette)
private val StudioChassisGradTop = Color(0xFF253146)
private val StudioChassisGradBottom = Color(0xFF192130)

private val StudioCardGradTop = Color(0xFF222C3E)
private val StudioCardGradBottom = Color(0xFF18202E)

private val StudioBtnGradTop = Color(0xFF2D3B54)
private val StudioBtnGradBottom = Color(0xFF202B3F)

private val StudioAccentBlue = Color(0xFF8BB5F8) // Glacier Blue
private val StudioAccentAmber = Color(0xFFFFB020) // Studio Amber
private val StudioTextWhite = Color(0xFFF1F5F9)
private val StudioTextMuted = Color(0xFF94A3B8)
private val StudioWaveformUnplayed = Color(0xFF3B4861)
private val StudioWaveformPlayed = Color(0xFF8BB5F8)

/**
 * Pure geometric centering text style removing all Android font padding and asymmetrical ascenders/descenders.
 */
private fun studioCenteredTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color
): TextStyle = TextStyle(
    fontSize = fontSize,
    lineHeight = fontSize,
    fontWeight = fontWeight,
    color = color,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

/**
 * Safe Float coerceIn helper preventing NaN crashes on Canvas calculations
 */
private fun Float.safeCoerceIn(minimumValue: Float, maximumValue: Float): Float {
    if (this.isNaN() || this.isInfinite()) return if (minimumValue.isNaN()) 0f else minimumValue
    val min = if (minimumValue.isNaN()) 0f else minimumValue
    val max = if (maximumValue.isNaN()) 1f else maxOf(min, maximumValue)
    return this.coerceIn(min, max)
}

/**
 * Chromatic scale notes for dynamic pitch transposition
 */
private val ChromaticNotes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun computeTransposedKeyResult(
    baseKey: String,
    semitones: Int,
    isMinorOverride: Boolean?
): String {
    if (baseKey.isBlank() || baseKey == "—") return "—"

    val isBaseMinor = baseKey.endsWith("m", ignoreCase = true) || baseKey.endsWith("min", ignoreCase = true)
    val rootStr = baseKey.replace("m", "", ignoreCase = true)
        .replace("min", "", ignoreCase = true)
        .replace("maj", "", ignoreCase = true)
        .trim().uppercase()

    val isMinor = isMinorOverride ?: isBaseMinor

    val baseIndex = ChromaticNotes.indexOf(rootStr)
    if (baseIndex == -1) {
        return if (isMinor) "${rootStr}m" else rootStr
    }

    var transposedIndex = (baseIndex + semitones) % 12
    if (transposedIndex < 0) transposedIndex += 12

    val root = ChromaticNotes[transposedIndex]
    return if (isMinor) "${root}m" else root
}

/**
 * Professional DAW-grade Loop Player Interface.
 * Pure geometric centering for all cards, pills and text containers.
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
    var isLocked by remember { mutableStateOf(false) }

    // Precise Audio Analysis for the currently loaded audio file
    val activeDetectionResult = remember(fileName, filePath) {
        if (fileName.isNotBlank() && fileName != "No Loop Selected") {
            AudioKeyBpmDetector.detectKeyAndBpm(fileName, filePath?.let { File(it) })
        } else null
    }

    val baseKey = activeDetectionResult?.key?.ifBlank { "—" } ?: "—"
    val baseTimeSignature = activeDetectionResult?.timeSignature?.ifBlank { "4/4" } ?: "4/4"

    var keyModeIsMinor by remember(baseKey) {
        mutableStateOf(baseKey.endsWith("m", ignoreCase = true) || baseKey.endsWith("min", ignoreCase = true))
    }

    // Sculpted Chassis with Studio Metallic Gradient & Soft Relief
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(StudioChassisGradTop, StudioChassisGradBottom)
                )
            )
            .padding(6.dp)
            .testTag("external_loop_player_container")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ================= 1. FILE MANAGER & EXPLORER =================
            StudioFileManagerPanel(
                currentPath = currentExplorerPath,
                items = explorerItems,
                activeFileName = fileName,
                activeFilePath = filePath,
                isPlaying = isPlaying,
                progress = progress,
                isLocked = isLocked,
                onToggleLock = { isLocked = !isLocked },
                onNavigate = onNavigateExplorerFolder,
                onFileSelected = onSelectAudioFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // ================= 2. WAVEFORM PROGRESS DISPLAY (BORDERLESS SMOOTH ENVELOPE) =================
            StudioWaveformPanel(
                fileName = fileName,
                positionMs = positionMs,
                durationMs = durationMs,
                progress = progress,
                isPlaying = isPlaying,
                waveform = waveform,
                onSeekProgress = onSeekProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )

            // ================= 3. BOTTOM HARDWARE DECK CONTROLS =================
            StudioHardwareControlBar(
                isPlaying = isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                pitchSemitones = pitchSemitones,
                onChangePitch = onChangePitch,
                baseKey = baseKey,
                baseTimeSignature = baseTimeSignature,
                keyModeIsMinor = keyModeIsMinor,
                onToggleKeyMode = { keyModeIsMinor = !keyModeIsMinor },
                stepMode = stepMode,
                onSelectStepMode = onSelectStepMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            )
        }
    }
}

/**
 * Top Maximized Storage File Manager with 3D Relief Cards and Pure Geometric Centering
 */
@Composable
private fun StudioFileManagerPanel(
    currentPath: String,
    items: List<AudioStorageItem>,
    activeFileName: String,
    activeFilePath: String?,
    isPlaying: Boolean,
    progress: Float,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onNavigate: (String) -> Unit,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(StudioCardGradTop, StudioCardGradBottom)
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x0AFFFFFF))),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Header Row: Breadcrumbs at left, Sort & Lock Icon at right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2B384E))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Breadcrumbs Path Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val parentPath = File(currentPath).parent
                    if (parentPath != null && parentPath.isNotBlank() && currentPath != "/storage/emulated/0") {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(StudioBtnGradTop)
                                .clickable { onNavigate(parentPath) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Parent Folder",
                                tint = StudioTextWhite,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    val cleanPath = currentPath
                        .replace(Regex("""^.*?/files/Æther Keys"""), "Internal Storage > Æther Keys")
                        .replace(Regex("""^.*?/Æther Keys"""), "Internal Storage > Æther Keys")
                        .replace("/storage/emulated/0/SoundStage/Loops", "Internal Storage > Æther Keys > Loops")
                        .replace("/storage/emulated/0", "Internal Storage")
                        .replace("/", " > ")

                    Text(
                        text = cleanPath,
                        style = studioCenteredTextStyle(10.sp, FontWeight.Medium, StudioTextWhite),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right Header Controls: Sort & Lock Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Sort Pill (Pure Geometric Center)
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E283A))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sort : Nearest BPM",
                            style = studioCenteredTextStyle(8.5.sp, FontWeight.Medium, StudioTextMuted)
                        )
                    }

                    // Lock Icon Button (Clean cadenas icon)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isLocked) StudioBtnGradTop else Color(0xFF1E283A))
                            .border(
                                0.8.dp,
                                if (isLocked) StudioAccentBlue else Color(0x22FFFFFF),
                                CircleShape
                            )
                            .clickable { onToggleLock() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = if (isLocked) StudioAccentBlue else StudioTextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // List of Directories and Audio Files
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO AUDIO LOOPS FOUND IN THIS DIRECTORY",
                        style = studioCenteredTextStyle(10.sp, FontWeight.Normal, StudioTextMuted)
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
                        if (item.isDirectory) {
                            // Directory Item Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .shadow(1.dp, RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF263248))
                                    .clickable { onNavigate(item.path) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = StudioAccentBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = item.name,
                                        style = studioCenteredTextStyle(11.sp, FontWeight.Medium, StudioTextWhite),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF1B2433))
                                        .padding(horizontal = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Folder",
                                        style = studioCenteredTextStyle(8.5.sp, FontWeight.Normal, StudioTextMuted)
                                    )
                                }
                            }
                        } else {
                            // Audio File Item Card with Precise Detection & Centered Pills
                            val isSelected = activeFilePath == item.path || activeFileName == item.name

                            val detectedInfo = remember(item.path, item.name) {
                                AudioKeyBpmDetector.detectKeyAndBpm(item.name, File(item.path))
                            }

                            val detectedBpm = detectedInfo.bpm
                            val detectedKey = detectedInfo.key.ifBlank { "—" }
                            val detectedSig = detectedInfo.timeSignature

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .shadow(if (isSelected) 2.dp else 1.dp, RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF334568), Color(0xFF263550))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF243044), Color(0xFF1E2838))
                                            )
                                        }
                                    )
                                    .clickable { onFileSelected(File(item.path)) }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected && isPlaying) {
                                    val safeProg = progress.safeCoerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth(safeProg)
                                            .height(2.dp)
                                            .background(StudioAccentAmber)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = "Audio File",
                                            tint = if (isSelected) StudioAccentAmber else StudioAccentBlue,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = item.name,
                                            style = studioCenteredTextStyle(
                                                11.sp,
                                                if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                StudioTextWhite
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 3 Info Pills: BPM, Key, Time Signature (Pure Geometric Center)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Pill 1: BPM
                                        Box(
                                            modifier = Modifier
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(Color(0xFF1A2332))
                                                .padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$detectedBpm BPM",
                                                style = studioCenteredTextStyle(8.5.sp, FontWeight.Normal, StudioTextMuted)
                                            )
                                        }

                                        // Pill 2: Key
                                        Box(
                                            modifier = Modifier
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(Color(0xFF22324A))
                                                .padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = detectedKey,
                                                style = studioCenteredTextStyle(8.5.sp, FontWeight.SemiBold, StudioAccentBlue)
                                            )
                                        }

                                        // Pill 3: Time Signature
                                        Box(
                                            modifier = Modifier
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(Color(0xFF1A2332))
                                                .padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = detectedSig,
                                                style = studioCenteredTextStyle(8.5.sp, FontWeight.Normal, StudioTextMuted)
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
    }
}

/**
 * Middle Waveform Panel with 3D Relief & Borderless Smooth Envelope Waveform
 */
@Composable
private fun StudioWaveformPanel(
    fileName: String,
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    isPlaying: Boolean,
    waveform: FloatArray,
    onSeekProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(StudioCardGradTop, StudioCardGradBottom)
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x0AFFFFFF))),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: File Name + Bar/Beat Counter at left, Next Track Pill at right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (fileName.isNotBlank()) fileName else "No Loop Selected",
                        style = studioCenteredTextStyle(11.sp, FontWeight.Bold, StudioTextWhite),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val bar = if (durationMs > 0) ((positionMs / (durationMs / 8f.coerceAtLeast(1f))) + 1).toInt().coerceIn(1, 8) else 1
                    val beat = if (durationMs > 0) (((positionMs % (durationMs / 8f.coerceAtLeast(1f))) / (durationMs / 32f.coerceAtLeast(1f))) + 1).toInt().coerceIn(1, 4) else 1

                    Text(
                        text = "Bar $bar · Beat $beat",
                        style = studioCenteredTextStyle(9.5.sp, FontWeight.Medium, StudioTextMuted)
                    )
                }

                // Next Track Pill (Pure Geometric Center)
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222E42))
                        .border(1.dp, StudioAccentAmber, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "Playing · End of Bar" else "Loop Ready",
                        style = studioCenteredTextStyle(8.5.sp, FontWeight.Medium, StudioTextWhite)
                    )
                }
            }

            // Real Waveform Canvas (Borderless Smooth Envelope)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                DawWaveformCanvas(
                    waveform = waveform,
                    progress = progress,
                    onSeekProgress = onSeekProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Timeline Ruler with Vertical Ticks and Numbers (1 2 3 4 5 6 7 8)
            DawTimelineRuler(
                barCount = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * Bottom Hardware Control Bar strictly conforming to Screenshots 1, 2, and 3 with Pure Geometric Centering
 */
@Composable
private fun StudioHardwareControlBar(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    pitchSemitones: Int,
    onChangePitch: (Int) -> Unit,
    baseKey: String,
    baseTimeSignature: String,
    keyModeIsMinor: Boolean,
    onToggleKeyMode: () -> Unit,
    stepMode: String,
    onSelectStepMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Play / Pause Button (Left)
        Box(
            modifier = Modifier
                .width(54.dp)
                .fillMaxHeight()
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF385684), Color(0xFF243A5C))
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onTogglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = StudioTextWhite,
                modifier = Modifier.size(24.dp)
            )
        }

        // 2. Key / Pitch Control Box (Conforming to Screenshot 3)
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(StudioCardGradTop, StudioCardGradBottom)
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x0AFFFFFF))),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            val displayedKey = remember(baseKey, pitchSemitones, keyModeIsMinor) {
                computeTransposedKeyResult(baseKey, pitchSemitones, keyModeIsMinor)
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Large Minus Pitch Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B384E))
                        .shadow(1.dp, CircleShape)
                        .clickable { onChangePitch((pitchSemitones - 1).coerceAtLeast(-12)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "- Pitch", tint = StudioTextWhite, modifier = Modifier.size(16.dp))
                }

                // Large Active Key Display (Pure Geometric Center)
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayedKey,
                        style = studioCenteredTextStyle(22.sp, FontWeight.Bold, StudioTextWhite)
                    )
                }

                // MAJ/MIN + Subtitle Column
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    // MAJ / MIN Mode Selector Pills (Pure Geometric Center)
                    Row(
                        modifier = Modifier
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF141C28))
                            .padding(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (!keyModeIsMinor) StudioAccentBlue else Color.Transparent)
                                .clickable { if (keyModeIsMinor) onToggleKeyMode() }
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MAJ",
                                style = studioCenteredTextStyle(
                                    8.sp,
                                    FontWeight.Bold,
                                    if (!keyModeIsMinor) Color.Black else StudioTextMuted
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (keyModeIsMinor) StudioAccentBlue else Color.Transparent)
                                .clickable { if (!keyModeIsMinor) onToggleKeyMode() }
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MIN",
                                style = studioCenteredTextStyle(
                                    8.sp,
                                    FontWeight.Bold,
                                    if (keyModeIsMinor) Color.Black else StudioTextMuted
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    Text(
                        text = "orig. $baseKey · ${if (pitchSemitones >= 0) "+" else ""}$pitchSemitones st",
                        style = studioCenteredTextStyle(8.sp, FontWeight.Normal, StudioTextMuted)
                    )
                }

                // Large Plus Pitch Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B384E))
                        .shadow(1.dp, CircleShape)
                        .clickable { onChangePitch((pitchSemitones + 1).coerceAtMost(12)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+ Pitch", tint = StudioTextWhite, modifier = Modifier.size(16.dp))
                }
            }
        }

        // 3. Time Signature Box (NO "SIGNATURE" title, big 4/4 centered in middle)
        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(StudioCardGradTop, StudioCardGradBottom)
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x0AFFFFFF))),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = baseTimeSignature,
                style = studioCenteredTextStyle(20.sp, FontWeight.Bold, StudioTextWhite)
            )
        }

        // 4. Step Control Box (Matching Key Deck: - button, Large Center Display + STEP title below, + button)
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(StudioCardGradTop, StudioCardGradBottom)
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x0AFFFFFF))),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            val stepValues = remember { listOf("Auto", "1", "2", "3", "4", "6", "8", "12", "16", "32") }
            val currentIndex = stepValues.indexOfFirst { it.equals(stepMode, ignoreCase = true) }.let { if (it == -1) 0 else it }
            val currentDisplayStep = stepValues.getOrElse(currentIndex) { "Auto" }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Large Minus Step Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B384E))
                        .shadow(1.dp, CircleShape)
                        .clickable {
                            val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                            onSelectStepMode(stepValues[prevIndex])
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "- Step", tint = StudioTextWhite, modifier = Modifier.size(16.dp))
                }

                // Center Column: Large Step Value + "STEP" Title below
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentDisplayStep,
                        style = studioCenteredTextStyle(22.sp, FontWeight.Bold, StudioTextWhite)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "STEP",
                        style = studioCenteredTextStyle(8.sp, FontWeight.Bold, StudioTextMuted)
                    )
                }

                // Large Plus Step Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B384E))
                        .shadow(1.dp, CircleShape)
                        .clickable {
                            val nextIndex = (currentIndex + 1).coerceAtMost(stepValues.size - 1)
                            onSelectStepMode(stepValues[nextIndex])
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+ Step", tint = StudioTextWhite, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Continuous Smooth Waveform Canvas (Borderless Envelope Path)
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
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        val frac = (offset.x / w).safeCoerceIn(0f, 1f)
                        onSeekProgress(frac)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        val frac = (change.position.x / w).safeCoerceIn(0f, 1f)
                        onSeekProgress(frac)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val centerY = height / 2f
            val safeProgress = progress.safeCoerceIn(0f, 1f)
            val playheadX = (safeProgress * width).safeCoerceIn(0f, width)

            // Center guideline
            drawLine(
                color = Color(0x18FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )

            // Render Continuous Borderless Envelope Waveform
            if (waveform.isNotEmpty()) {
                val pointCount = waveform.size
                val stepX = width / (pointCount - 1).coerceAtLeast(1).toFloat()
                val maxAmp = (height * 0.42f)

                val envelopePath = Path().apply {
                    moveTo(0f, centerY)

                    // Top envelope curve (left to right)
                    for (i in 0 until pointCount) {
                        val x = i * stepX
                        val peak = waveform[i].coerceIn(0.04f, 1f)
                        val yTop = centerY - (peak * maxAmp)
                        lineTo(x, yTop)
                    }

                    // Bottom envelope curve (right to left)
                    for (i in (pointCount - 1) downTo 0) {
                        val x = i * stepX
                        val peak = waveform[i].coerceIn(0.04f, 1f)
                        val yBottom = centerY + (peak * maxAmp)
                        lineTo(x, yBottom)
                    }

                    close()
                }

                // 1. Unplayed background waveform (Full width - Borderless)
                drawPath(
                    path = envelopePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            StudioWaveformUnplayed.copy(alpha = 0.85f),
                            StudioWaveformUnplayed.copy(alpha = 0.35f),
                            StudioWaveformUnplayed.copy(alpha = 0.85f)
                        )
                    )
                )

                // 2. Played active waveform (Clipped to playhead position - Borderless)
                if (playheadX > 0f) {
                    clipRect(left = 0f, top = 0f, right = playheadX, bottom = height) {
                        drawPath(
                            path = envelopePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    StudioWaveformPlayed,
                                    StudioWaveformPlayed.copy(alpha = 0.5f),
                                    StudioWaveformPlayed
                                )
                            )
                        )
                    }
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
            drawLine(
                color = Color.White,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 2.5f
            )

            // Playhead Cursor Indicators
            drawCircle(
                color = StudioAccentAmber,
                radius = 4f,
                center = Offset(playheadX, 4f)
            )
            drawCircle(
                color = StudioAccentAmber,
                radius = 4f,
                center = Offset(playheadX, height - 4f)
            )
        }
    }
}

/**
 * High-precision timeline ruler displaying DAW-style measure vertical tick marks and numbers
 */
@Composable
private fun DawTimelineRuler(
    barCount: Int = 8,
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 24f
            color = android.graphics.Color.argb(165, 148, 163, 184)
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f) return@Canvas

        val totalBeats = barCount * 4
        val beatWidth = w / totalBeats.toFloat()

        // Horizontal base line
        drawLine(
            color = Color(0x30FFFFFF),
            start = Offset(0f, 0f),
            end = Offset(w, 0f),
            strokeWidth = 1f
        )

        for (bar in 0 until barCount) {
            val barX = bar * 4 * beatWidth

            // Major vertical tick (bar start)
            drawLine(
                color = Color(0x90FFFFFF),
                start = Offset(barX, 0f),
                end = Offset(barX, 5.dp.toPx()),
                strokeWidth = 1.2f
            )

            // Minor vertical ticks for beats 2, 3, 4
            for (beat in 1..3) {
                val beatX = barX + (beat * beatWidth)
                if (beatX < w) {
                    drawLine(
                        color = Color(0x40FFFFFF),
                        start = Offset(beatX, 0f),
                        end = Offset(beatX, 2.8.dp.toPx()),
                        strokeWidth = 0.8f
                    )
                }
            }

            // Draw measure number below the major tick
            val numX = barX + (1.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                (bar + 1).toString(),
                numX,
                h - 1f,
                textPaint
            )
        }
    }
}

