package com.soundstage.mixer.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
    const val MIN_MIDI = 33  // A1 (33), Bb1 (34), B1 (35), then C2 (36) to C8 (108)
    const val MAX_MIDI = 108 // C8

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiToNoteName(midi: Int): String {
        val clamped = midi.coerceIn(0, 127)
        val semitone = (clamped % 12 + 12) % 12
        val octave = (clamped / 12) - 1
        return "${NOTE_NAMES[semitone]}$octave"
    }

    fun midiToKeyLeftEdgeFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        if (clampedMidi < 36) {
            return when (clampedMidi) {
                33 -> 0.0f
                34 -> 0.7f
                35 -> 1.0f
                else -> 0.0f
            }
        }
        val octaveIndex = (clampedMidi - 36) / 12
        val semitone = (clampedMidi - 36) % 12
        val octaveWhiteStart = 2.0f + octaveIndex * 7f
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
        if (clampedMidi < 36) {
            return when (clampedMidi) {
                33 -> 1.0f
                34 -> 1.3f
                35 -> 2.0f
                else -> 1.0f
            }
        }
        val octaveIndex = (clampedMidi - 36) / 12
        val semitone = (clampedMidi - 36) % 12
        val octaveWhiteStart = 2.0f + octaveIndex * 7f
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
     * A1 starts at 0.5.
     */
    fun midiToKeyCenterFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        if (clampedMidi < 36) {
            return when (clampedMidi) {
                33 -> 0.5f
                34 -> 1.0f
                35 -> 1.5f
                else -> 0.5f
            }
        }
        val octaveIndex = (clampedMidi - 36) / 12
        val semitone = (clampedMidi - 36) % 12
        val octaveWhiteStart = 2.0f + octaveIndex * 7f
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

    fun getTrackNeonColor(trackId: Int): Color {
        return when (trackId) {
            1 -> Color(0xFF22D3EE) // Neon Cyan
            2 -> Color(0xFF10B981) // Neon Emerald
            3 -> Color(0xFF8B5CF6) // Neon Purple
            4 -> Color(0xFFFFC247) // Neon Amber
            5 -> Color(0xFFD946EF) // Neon Magenta
            6 -> Color(0xFF38BDF8) // Neon Sky Blue
            7 -> Color(0xFF84CC16) // Neon Lime
            8 -> Color(0xFFF43F5E) // Neon Rose
            else -> Color(0xFF22D3EE)
        }
    }
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
    onDragSelectionChange: ((range: Pair<Float, Float>?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visibleTracks = remember(tracks) {
        tracks.filter { it.isEnabled }
    }

    if (visibleTracks.isEmpty()) return

    val totalWidthDp = whiteWidthDp * totalWhiteKeys
    val visibleCount = visibleTracks.size.coerceAtMost(6)
    val targetHeight = if (isExpanded) {
        (30.dp * visibleCount + 8.dp).coerceAtLeast(38.dp)
    } else {
        // Compact mode: ultra-fine, aesthetic Sunday Keys style lines (12-16dp total)
        (2.8.dp * visibleTracks.size.coerceAtMost(6) + 3.dp).coerceIn(10.dp, 16.dp)
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "mapperHeight"
    )

    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .width(totalWidthDp)
            .height(animatedHeight)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(Color(0xE60A0E16))
            .padding(horizontal = 0.dp, vertical = 1.dp)
            .testTag("keyboard_layer_mapper")
    ) {
        if (!isExpanded) {
            // ================= COMPACT MODE: Sunday Keys Ultra-Thin Layer Lines (Fine & Aesthetic) =================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onToggleExpanded() },
                verticalArrangement = Arrangement.spacedBy(1.5.dp, Alignment.CenterVertically)
            ) {
                visibleTracks.forEachIndexed { _, track ->
                    val trackColor = KeyPositionMapper.getTrackNeonColor(track.id)
                    val leftFrac = KeyPositionMapper.midiToKeyLeftEdgeFraction(track.splitNoteMin)
                    val rightFrac = KeyPositionMapper.midiToKeyRightEdgeFraction(track.splitNoteMax)

                    val startXDp = (leftFrac * whiteWidthDp.value).dp
                    val endXDp = (rightFrac * whiteWidthDp.value).dp
                    val barWidthDp = (endXDp - startXDp).coerceAtLeast(6.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = startXDp)
                                .width(barWidthDp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(trackColor.copy(alpha = 0.90f), trackColor)
                                    )
                                )
                                .border(0.4.dp, Color(0x44FFFFFF), RoundedCornerShape(1.5.dp))
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
                visibleTracks.forEachIndexed { _, track ->
                    val trackColor = KeyPositionMapper.getTrackNeonColor(track.id)
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
                        onRangeChanged = onRangeChanged,
                        onDragSelectionChange = onDragSelectionChange
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
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit,
    onDragSelectionChange: ((range: Pair<Float, Float>?) -> Unit)? = null
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
    val barWidthDp = (endXDp - startXDp).coerceAtLeast(18.dp)

    val dotSize = 7.dp
    val dotInset = 5.dp
    val leftDotX = startXDp + dotInset
    val rightDotX = (endXDp - dotInset - dotSize).coerceAtLeast(leftDotX + dotSize + 2.dp)
    val touchHitboxWidth = 36.dp

    var dragMinOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragMaxOffsetPx by remember { mutableFloatStateOf(0f) }
    var initialMinFrac by remember { mutableFloatStateOf(0f) }
    var initialMaxFrac by remember { mutableFloatStateOf(0f) }
    var isDraggingMin by remember { mutableStateOf(false) }
    var isDraggingMax by remember { mutableStateOf(false) }

    val isActivelyDragging = isDraggingMin || isDraggingMax

    // Smooth Updated State References to prevent gesture reset/glue during recomposition
    val currentMinNote by rememberUpdatedState(minNote)
    val currentMaxNote by rememberUpdatedState(maxNote)
    val currentLeftFrac by rememberUpdatedState(leftFrac)
    val currentRightFrac by rememberUpdatedState(rightFrac)
    val currentOnRangeChanged by rememberUpdatedState(onRangeChanged)
    val currentOnDragSelectionChange by rememberUpdatedState(onDragSelectionChange)
    val currentWhiteWidthPx by rememberUpdatedState(whiteWidthPx)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        // Main Colored Capsule Bar (Border-less, MaterialYou Expressive)
        val minNoteName = KeyPositionMapper.midiToNoteName(minNote)
        val maxNoteName = KeyPositionMapper.midiToNoteName(maxNote)

        Box(
            modifier = Modifier
                .offset(x = startXDp)
                .width(barWidthDp)
                .height(24.dp)
                .shadow(if (isActivelyDragging) 4.dp else 1.5.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(trackColor.copy(alpha = if (isActivelyDragging) 0.96f else 0.88f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onToggleExpanded() }
                    )
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (barWidthDp >= 64.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    // Low Note Indicator Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x40000000))
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = minNoteName,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Track / Patch Name centered in the middle
                    Text(
                        text = patchTitle,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // High Note Indicator Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x40000000))
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = maxNoteName,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else if (barWidthDp >= 38.dp) {
                Text(
                    text = "$minNoteName-$maxNoteName",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            } else {
                Text(
                    text = minNoteName,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // ================= LEFT DRAG HANDLE: Small White Dot inside line with ergonomic hitbox =================
        Box(
            modifier = Modifier
                .offset(x = (leftDotX + (dotSize / 2f)) - (touchHitboxWidth / 2f))
                .size(touchHitboxWidth, 26.dp)
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragMinOffsetPx = 0f
                            initialMinFrac = currentLeftFrac
                            isDraggingMin = true
                            currentOnDragSelectionChange?.invoke(currentLeftFrac to currentRightFrac)
                        },
                        onDragEnd = {
                            isDraggingMin = false
                            currentOnDragSelectionChange?.invoke(null)
                        },
                        onDragCancel = {
                            isDraggingMin = false
                            currentOnDragSelectionChange?.invoke(null)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragMinOffsetPx += dragAmount
                            val wPx = if (currentWhiteWidthPx > 0f) currentWhiteWidthPx else 10f
                            val currentFrac = initialMinFrac + (dragMinOffsetPx / wPx)
                            val newMidi = KeyPositionMapper.xFractionToNearestMidi(
                                xFraction = currentFrac,
                                minAllowedMidi = KeyPositionMapper.MIN_MIDI,
                                maxAllowedMidi = currentMaxNote
                            )
                            if (newMidi != currentMinNote) {
                                currentOnRangeChanged(track.id, newMidi, currentMaxNote)
                            }
                            val updatedLeft = KeyPositionMapper.midiToKeyLeftEdgeFraction(newMidi)
                            val updatedRight = KeyPositionMapper.midiToKeyRightEdgeFraction(currentMaxNote)
                            currentOnDragSelectionChange?.invoke(updatedLeft to updatedRight)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Pure small white dot cleanly nestled inside the line extremity
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // ================= RIGHT DRAG HANDLE: Small White Dot inside line with ergonomic hitbox =================
        Box(
            modifier = Modifier
                .offset(x = (rightDotX + (dotSize / 2f)) - (touchHitboxWidth / 2f))
                .size(touchHitboxWidth, 26.dp)
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragMaxOffsetPx = 0f
                            initialMaxFrac = currentRightFrac
                            isDraggingMax = true
                            currentOnDragSelectionChange?.invoke(currentLeftFrac to currentRightFrac)
                        },
                        onDragEnd = {
                            isDraggingMax = false
                            currentOnDragSelectionChange?.invoke(null)
                        },
                        onDragCancel = {
                            isDraggingMax = false
                            currentOnDragSelectionChange?.invoke(null)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragMaxOffsetPx += dragAmount
                            val wPx = if (currentWhiteWidthPx > 0f) currentWhiteWidthPx else 10f
                            val currentFrac = initialMaxFrac + (dragMaxOffsetPx / wPx)
                            val newMidi = KeyPositionMapper.xFractionToNearestMidi(
                                xFraction = currentFrac,
                                minAllowedMidi = currentMinNote,
                                maxAllowedMidi = KeyPositionMapper.MAX_MIDI
                            )
                            if (newMidi != currentMaxNote) {
                                currentOnRangeChanged(track.id, currentMinNote, newMidi)
                            }
                            val updatedLeft = KeyPositionMapper.midiToKeyLeftEdgeFraction(currentMinNote)
                            val updatedRight = KeyPositionMapper.midiToKeyRightEdgeFraction(newMidi)
                            currentOnDragSelectionChange?.invoke(updatedLeft to updatedRight)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Pure small white dot cleanly nestled inside the line extremity
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun ExpandedSundayKeysLayerPanel(
    tracks: List<TrackChannel>,
    onClose: () -> Unit,
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val visibleTracks = remember(tracks) { tracks.filter { it.isEnabled } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(142.dp)
            .shadow(20.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFA101826),
                        Color(0xFD0A0E18)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x6622D3EE), Color(0x2222D3EE))
                ),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Title + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                    )
                    Text(
                        text = "KEYBOARD LAYERS & SPLITS (SUNDAY KEYS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.6.sp
                    )
                }

                // Close Button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Layer Bars container
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val availableWidthDp = maxWidth
                val totalWhiteKeys = 45 // A1..C8
                val whiteWidthDp = (availableWidthDp / totalWhiteKeys.toFloat()).coerceAtLeast(14.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                ) {
                    KeyboardLayerMapper(
                        tracks = tracks,
                        whiteWidthDp = whiteWidthDp,
                        totalWhiteKeys = totalWhiteKeys,
                        isExpanded = true,
                        onToggleExpanded = onClose,
                        onRangeChanged = onRangeChanged
                    )
                }
            }
        }
    }
}
