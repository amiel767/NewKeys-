package com.soundstage.mixer.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.theme.*

/**
 * Geometric Helper for Mapping MIDI Notes to Precise Key Centers & Snap Positions
 */
object KeyPositionMapper {
    const val MIN_MIDI = 24  // C1
    const val MAX_MIDI = 108 // C7

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiToNoteName(midi: Int): String {
        val clamped = midi.coerceIn(0, 127)
        val semitone = (clamped % 12 + 12) % 12
        val octave = (clamped / 12) - 1
        return "${NOTE_NAMES[semitone]}$octave"
    }

    fun midiToKeyLeftEdgeFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        val octaveIndex = (clampedMidi - MIN_MIDI) / 12
        val semitone = (clampedMidi - MIN_MIDI) % 12

        val octaveWhiteStart = octaveIndex * 7f
        val whiteOffset = when (semitone) {
            0 -> 0.0f  // C
            1 -> 0.7f  // C#
            2 -> 1.0f  // D
            3 -> 1.7f  // D#
            4 -> 2.0f  // E
            5 -> 3.0f  // F
            6 -> 3.7f  // F#
            7 -> 4.0f  // G
            8 -> 4.7f  // G#
            9 -> 5.0f  // A
            10 -> 5.7f // A#
            11 -> 6.0f // B
            else -> 0.0f
        }
        return octaveWhiteStart + whiteOffset
    }

    fun midiToKeyRightEdgeFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        val octaveIndex = (clampedMidi - MIN_MIDI) / 12
        val semitone = (clampedMidi - MIN_MIDI) % 12

        val octaveWhiteStart = octaveIndex * 7f
        val whiteOffset = when (semitone) {
            0 -> 1.0f  // C
            1 -> 1.3f  // C#
            2 -> 2.0f  // D
            3 -> 2.3f  // D#
            4 -> 3.0f  // E
            5 -> 4.0f  // F
            6 -> 4.3f  // F#
            7 -> 5.0f  // G
            8 -> 5.3f  // G#
            9 -> 6.0f  // A
            10 -> 6.3f // A#
            11 -> 7.0f // B
            else -> 1.0f
        }
        return octaveWhiteStart + whiteOffset
    }

    /**
     * Maps a MIDI note to its exact center horizontal position (in white key width units).
     * C1 (24) starts at index 0.
     */
    fun midiToKeyCenterFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        val octaveIndex = (clampedMidi - MIN_MIDI) / 12
        val semitone = (clampedMidi - MIN_MIDI) % 12

        val octaveWhiteStart = octaveIndex * 7f
        val whiteOffset = when (semitone) {
            0 -> 0.5f  // C
            1 -> 1.0f  // C#
            2 -> 1.5f  // D
            3 -> 2.0f  // D#
            4 -> 2.5f  // E
            5 -> 3.5f  // F
            6 -> 4.0f  // F#
            7 -> 4.5f  // G
            8 -> 5.0f  // G#
            9 -> 5.5f  // A
            10 -> 6.0f // A#
            11 -> 6.5f // B
            else -> 0.5f
        }
        return octaveWhiteStart + whiteOffset
    }

    /**
     * Converts an X position in white key width units to the nearest MIDI note.
     */
    fun xFractionToNearestMidi(xFraction: Float, minAllowedMidi: Int = MIN_MIDI, maxAllowedMidi: Int = MAX_MIDI): Int {
        var closestMidi = minAllowedMidi
        var minDistance = Float.MAX_VALUE

        for (midi in minAllowedMidi..maxAllowedMidi) {
            val center = midiToKeyCenterFraction(midi)
            val dist = kotlin.math.abs(center - xFraction)
            if (dist < minDistance) {
                minDistance = dist
                closestMidi = midi
            }
        }
        return closestMidi
    }

    val TRACK_PALETTE = listOf(
        Color(0xFFF97316), // T1: Vibrant Amber Orange
        Color(0xFF22C55E), // T2: Emerald Green
        Color(0xFF3B82F6), // T3: Royal Blue
        Color(0xFFA855F7), // T4: Neon Purple
        Color(0xFFEC4899), // T5: Vibrant Magenta Pink
        Color(0xFF06B6D4), // T6: Electric Cyan
        Color(0xFFEAB308), // T7: Gold Yellow
        Color(0xFFEF4444)  // T8: Crimson Red
    )
}

/**
 * Visual Keyboard Layer Mapper:
 * - Compact state: Thin colored bars overlaid directly above the keys.
 * - Expanded state: Smooth vertical expansion with drag handles (Low/High Notes), patch names, and live key snapping.
 */
@Composable
fun KeyboardLayerMapper(
    tracks: List<TrackChannel>,
    whiteWidthDp: Dp,
    totalWhiteKeys: Int = 50,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleTracks = remember(tracks) {
        tracks.filter { it.isEnabled }
    }

    if (visibleTracks.isEmpty()) return

    val totalWidthDp = whiteWidthDp * totalWhiteKeys
    val visibleCount = visibleTracks.size.coerceAtMost(4)
    val targetHeight = if (isExpanded) {
        (32.dp * visibleCount + 8.dp).coerceAtLeast(40.dp)
    } else {
        (3.dp * visibleTracks.size.coerceAtMost(6) + 6.dp).coerceIn(10.dp, 22.dp)
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "mapperHeight"
    )

    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .width(totalWidthDp)
            .height(animatedHeight)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(Color(0xFF10141D))
            .border(0.8.dp, Color(0x2222D3EE), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .testTag("keyboard_layer_mapper")
    ) {
        if (!isExpanded) {
            // ================= COMPACT MODE: Thin Layered Range Lines =================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onToggleExpanded() },
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                visibleTracks.forEachIndexed { index, track ->
                    val trackColor = KeyPositionMapper.TRACK_PALETTE.getOrElse(index % KeyPositionMapper.TRACK_PALETTE.size) { NeonCyan }
                    val leftFrac = KeyPositionMapper.midiToKeyLeftEdgeFraction(track.splitNoteMin)
                    val rightFrac = KeyPositionMapper.midiToKeyRightEdgeFraction(track.splitNoteMax)

                    val startXDp = (leftFrac * whiteWidthDp.value).dp
                    val endXDp = (rightFrac * whiteWidthDp.value).dp
                    val barWidthDp = (endXDp - startXDp).coerceAtLeast(6.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = startXDp)
                                .width(barWidthDp)
                                .height(2.8.dp)
                                .clip(RoundedCornerShape(1.4.dp))
                                .background(trackColor)
                        )
                    }
                }
            }
        } else {
            // ================= EXPANDED MODE: Rich Range Bars with Drag Handles =================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visibleTracks.forEachIndexed { index, track ->
                    val trackColor = KeyPositionMapper.TRACK_PALETTE.getOrElse(index % KeyPositionMapper.TRACK_PALETTE.size) { NeonCyan }
                    val patchTitle = when {
                        track.patchName.isNotBlank() -> track.patchName
                        track.soundfontName.isNotBlank() -> track.soundfontName
                        else -> track.name
                    }

                    TrackRangeBarRow(
                        track = track,
                        trackColor = trackColor,
                        patchTitle = patchTitle,
                        whiteWidthDp = whiteWidthDp,
                        onToggleExpanded = onToggleExpanded,
                        onRangeChanged = onRangeChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRangeBarRow(
    track: TrackChannel,
    trackColor: Color,
    patchTitle: String,
    whiteWidthDp: Dp,
    onToggleExpanded: () -> Unit,
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit
) {
    val density = LocalDensity.current
    val whiteWidthPx = with(density) { whiteWidthDp.toPx() }

    // Left and Right X coordinate calculation from MIDI notes using exact key boundaries
    val minNote = track.splitNoteMin
    val maxNote = track.splitNoteMax

    val leftFrac = KeyPositionMapper.midiToKeyLeftEdgeFraction(minNote)
    val rightFrac = KeyPositionMapper.midiToKeyRightEdgeFraction(maxNote)

    val startXDp = (leftFrac * whiteWidthDp.value).dp
    val endXDp = (rightFrac * whiteWidthDp.value).dp
    val barWidthDp = (endXDp - startXDp).coerceAtLeast(60.dp)

    var dragMinOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragMaxOffsetPx by remember { mutableFloatStateOf(0f) }
    var initialMinFrac by remember { mutableFloatStateOf(0f) }
    var initialMaxFrac by remember { mutableFloatStateOf(0f) }
    var isDraggingMin by remember { mutableStateOf(false) }
    var isDraggingMax by remember { mutableStateOf(false) }

    val isActivelyDragging = isDraggingMin || isDraggingMax

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        // ================= TRANSPARENT BLUE SELECTION LAYER (Across both extremities) =================
        Box(
            modifier = Modifier
                .offset(x = startXDp)
                .width(barWidthDp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x4000E5FF),
                            if (isActivelyDragging) Color(0x6500E5FF) else Color(0x2800E5FF),
                            Color(0x4000E5FF)
                        )
                    )
                )
                .border(
                    width = if (isActivelyDragging) 1.5.dp else 1.dp,
                    color = if (isActivelyDragging) Color(0xFF00E5FF) else Color(0x6600E5FF),
                    shape = RoundedCornerShape(13.dp)
                )
        )

        // Main Colored Capsule Bar
        Box(
            modifier = Modifier
                .offset(x = startXDp)
                .width(barWidthDp)
                .height(26.dp)
                .shadow(if (isActivelyDragging) 6.dp else 3.dp, RoundedCornerShape(13.dp))
                .clip(RoundedCornerShape(13.dp))
                .background(trackColor.copy(alpha = if (isActivelyDragging) 0.92f else 0.82f))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(13.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onToggleExpanded() }
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Patch / SoundFont Name centered
            Text(
                text = patchTitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x35000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // ================= LEFT DRAG HANDLE (Low Note) =================
        val minNoteName = KeyPositionMapper.midiToNoteName(minNote)
        Row(
            modifier = Modifier
                .offset(x = (startXDp - 10.dp).coerceAtLeast(0.dp))
                .height(28.dp)
                .pointerInput(track.id, whiteWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragMinOffsetPx = 0f
                            initialMinFrac = leftFrac
                            isDraggingMin = true
                        },
                        onDragEnd = { isDraggingMin = false },
                        onDragCancel = { isDraggingMin = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragMinOffsetPx += dragAmount
                            val currentFrac = initialMinFrac + (dragMinOffsetPx / whiteWidthPx)
                            val newMidi = KeyPositionMapper.xFractionToNearestMidi(
                                xFraction = currentFrac,
                                minAllowedMidi = KeyPositionMapper.MIN_MIDI,
                                maxAllowedMidi = maxNote
                            )
                            if (newMidi != minNote) {
                                onRangeChanged(track.id, newMidi, maxNote)
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Circular Knob
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shadow(if (isDraggingMin) 5.dp else 3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isDraggingMin) Color(0xFF00E5FF) else Color.White)
                    .border(2.dp, Color(0xFF141923), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141923))
                )
            }

            // Note Badge Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDraggingMin) Color(0xFF0F394A) else Color(0xE60D1117))
                    .border(0.8.dp, if (isDraggingMin) Color(0xFF00E5FF) else Color(0x44FFFFFF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = minNoteName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDraggingMin) Color(0xFF00E5FF) else Color.White
                )
            }
        }

        // ================= RIGHT DRAG HANDLE (High Note) =================
        val maxNoteName = KeyPositionMapper.midiToNoteName(maxNote)
        Row(
            modifier = Modifier
                .offset(x = (endXDp - 32.dp).coerceAtLeast(startXDp + 30.dp))
                .height(28.dp)
                .pointerInput(track.id, whiteWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragMaxOffsetPx = 0f
                            initialMaxFrac = rightFrac
                            isDraggingMax = true
                        },
                        onDragEnd = { isDraggingMax = false },
                        onDragCancel = { isDraggingMax = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragMaxOffsetPx += dragAmount
                            val currentFrac = initialMaxFrac + (dragMaxOffsetPx / whiteWidthPx)
                            val newMidi = KeyPositionMapper.xFractionToNearestMidi(
                                xFraction = currentFrac,
                                minAllowedMidi = minNote,
                                maxAllowedMidi = KeyPositionMapper.MAX_MIDI
                            )
                            if (newMidi != maxNote) {
                                onRangeChanged(track.id, minNote, newMidi)
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Note Badge Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDraggingMax) Color(0xFF0F394A) else Color(0xE60D1117))
                    .border(0.8.dp, if (isDraggingMax) Color(0xFF00E5FF) else Color(0x44FFFFFF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = maxNoteName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDraggingMax) Color(0xFF00E5FF) else Color.White
                )
            }

            // Circular Knob
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shadow(if (isDraggingMax) 5.dp else 3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isDraggingMax) Color(0xFF00E5FF) else Color.White)
                    .border(2.dp, Color(0xFF141923), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141923))
                )
            }
        }
    }
}
