package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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

    fun getTrackNeonColor(trackId: Int, saturationMultiplier: Float = 1.0f): Color {
        // Sunday Keys Palette: Strictly matching mixer track channel colors
        val base = when ((trackId - 1) % 8) {
            0 -> Color(0xFFE58929) // Track 1: Amber
            1 -> Color(0xFF36B37E) // Track 2: Emerald Green
            2 -> Color(0xFF3B82F6) // Track 3: Cobalt Sky Blue
            3 -> Color(0xFF9333EA) // Track 4: Violet / Purple
            4 -> Color(0xFFEC4899) // Track 5: Rose
            5 -> Color(0xFF14B8A6) // Track 6: Teal
            6 -> Color(0xFFF59E0B) // Track 7: Gold
            7 -> Color(0xFF6366F1) // Track 8: Indigo
            else -> Color(0xFF3B82F6)
        }
        if (saturationMultiplier == 1.0f) return base
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (base.red * 255).toInt(),
                (base.green * 255).toInt(),
                (base.blue * 255).toInt()
            ),
            hsv
        )
        hsv[1] = (hsv[1] * saturationMultiplier).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun getTrackNeonGradient(trackId: Int, saturationMultiplier: Float = 1.0f): Pair<Color, Color> {
        val c1 = getTrackNeonColor(trackId, saturationMultiplier)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (c1.red * 255).toInt(),
                (c1.green * 255).toInt(),
                (c1.blue * 255).toInt()
            ),
            hsv
        )
        hsv[2] = (hsv[2] * 0.75f).coerceIn(0f, 1f)
        val c2 = Color(android.graphics.Color.HSVToColor(hsv))
        return c1 to c2
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
    totalWhiteKeys: Int = 45,
    trackSaturation: Float = 1.0f,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit,
    onDragSelectionChange: ((range: Triple<Float, Float, Color>?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visibleTracks = remember(tracks) {
        tracks.filter { it.isEnabled }
    }

    if (visibleTracks.isEmpty()) return

    val totalWidthDp = whiteWidthDp * totalWhiteKeys

    // Fluid, ultra-smooth 60/120fps expansion progress without spring oscillation lag
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        ),
        label = "layer_expand_progress"
    )

    val safeExpand = expandProgress.coerceIn(0f, 1f)
    val rowSpacingDp = lerp(0.8.dp, 3.dp, safeExpand)

    val currentOnToggleExpanded by rememberUpdatedState(onToggleExpanded)
    val verticalScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .width(totalWidthDp)
            .wrapContentHeight()
            .clickable(
                enabled = !isExpanded,
                onClick = { currentOnToggleExpanded() }
            )
            .testTag("keyboard_layer_mapper")
    ) {
        // Visible vertical octave dividers (Sunday Keys DAW aesthetic)
        Canvas(modifier = Modifier.matchParentSize()) {
            val whitePx = whiteWidthDp.toPx()
            for (oct in 0..6) {
                val keyIndex = 2 + oct * 7
                val x = keyIndex * whitePx
                drawLine(
                    color = Color(0x6EFFFFFF),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.0f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 2.dp, end = 2.dp, top = lerp(1.dp, 2.dp, safeExpand), bottom = lerp(2.dp, 6.dp, safeExpand)),
            verticalArrangement = Arrangement.spacedBy(
                rowSpacingDp,
                Alignment.Top
            )
        ) {
            visibleTracks.forEach { track ->
                key(track.id) {
                    val trackColor = KeyPositionMapper.getTrackNeonColor(track.id, trackSaturation)
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
                        expandProgress = safeExpand,
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
    expandProgress: Float,
    onToggleExpanded: () -> Unit,
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit,
    onDragSelectionChange: ((range: Triple<Float, Float, Color>?) -> Unit)? = null
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

    // Dynamic animated height and corner radius (2.1dp at rest - reduced by 40%, 22dp when deployed)
    val rowHeightDp = lerp(2.1.dp, 22.dp, expandProgress)
    val cornerRadiusStartDp = lerp(1.5.dp, 5.dp, expandProgress)
    val cornerRadiusEndDp = lerp(3.5.dp, 5.dp, expandProgress)
    val minBarWidth = lerp(8.dp, 22.dp, expandProgress)
    val rawBarWidthDp = (endXDp - startXDp).coerceAtLeast(minBarWidth)

    var dragMinOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragMaxOffsetPx by remember { mutableFloatStateOf(0f) }
    var initialMinFrac by remember { mutableFloatStateOf(0f) }
    var initialMaxFrac by remember { mutableFloatStateOf(0f) }
    var isDraggingMin by remember { mutableStateOf(false) }
    var isDraggingMax by remember { mutableStateOf(false) }

    val isActivelyDragging = isDraggingMin || isDraggingMax

    // Bouncy aesthetic spring animation on position and width snap
    val animatedStartX by animateDpAsState(
        targetValue = startXDp,
        animationSpec = if (isActivelyDragging) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "layer_start_x"
    )
    val animatedWidth by animateDpAsState(
        targetValue = rawBarWidthDp,
        animationSpec = if (isActivelyDragging) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "layer_width"
    )

    val dotSize = 10.dp
    val dotInset = 4.dp
    val leftDotX = animatedStartX + dotInset
    val rightDotX = (animatedStartX + animatedWidth - dotInset - dotSize).coerceAtLeast(leftDotX + dotSize + 2.dp)
    val touchHitboxWidth = 38.dp

    // Smooth Updated State References to prevent gesture reset/glue during recomposition
    val currentMinNote by rememberUpdatedState(minNote)
    val currentMaxNote by rememberUpdatedState(maxNote)
    val currentLeftFrac by rememberUpdatedState(leftFrac)
    val currentRightFrac by rememberUpdatedState(rightFrac)
    val currentOnRangeChanged by rememberUpdatedState(onRangeChanged)
    val currentOnDragSelectionChange by rememberUpdatedState(onDragSelectionChange)
    val currentWhiteWidthPx by rememberUpdatedState(whiteWidthPx)

    val currentExpandProgress by rememberUpdatedState(expandProgress)
    val currentOnToggleExpanded by rememberUpdatedState(onToggleExpanded)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeightDp)
    ) {
        // Main Colored Capsule Bar (Completely Border-less, Pure Clear Solid Body)
        val minNoteName = KeyPositionMapper.midiToNoteName(minNote)
        val maxNoteName = KeyPositionMapper.midiToNoteName(maxNote)

        val safeExpand = if (expandProgress.isNaN()) 0f else expandProgress.coerceIn(0f, 1f)

        val capsuleShape = RoundedCornerShape(
            topStart = cornerRadiusStartDp,
            bottomStart = cornerRadiusStartDp,
            topEnd = cornerRadiusEndDp,
            bottomEnd = cornerRadiusEndDp
        )

        Box(
            modifier = Modifier
                .offset(x = animatedStartX)
                .width(animatedWidth.coerceAtLeast(4.dp))
                .height(rowHeightDp.coerceAtLeast(2.dp))
                .clip(capsuleShape)
                .background(
                    color = trackColor,
                    shape = capsuleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { currentOnToggleExpanded() },
                        onTap = {
                            if (currentExpandProgress < 0.5f) {
                                currentOnToggleExpanded()
                            }
                        }
                    )
                }
                .padding(horizontal = lerp(0.dp, 14.dp, safeExpand)),
            contentAlignment = Alignment.Center
        ) {
            if (safeExpand > 0.35f) {
                val textAlpha = ((safeExpand - 0.35f) / 0.65f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier.graphicsLayer { alpha = textAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    if (animatedWidth >= 64.dp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            // Low Note Indicator Badge (Sunday Keys dark pill)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xD90F172A))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = minNoteName,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Track / Patch Name centered in the middle
                            Text(
                                text = patchTitle,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 2.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // High Note Indicator Badge (Sunday Keys dark pill)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xD90F172A))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = maxNoteName,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    } else if (animatedWidth >= 38.dp) {
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
            }
        }

        // Handles (Left and Right Drag Handles): fade and scale in with expandProgress
        if (expandProgress > 0.45f) {
            val handleAlpha = ((expandProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)

            // ================= LEFT DRAG HANDLE =================
            Box(
                modifier = Modifier
                    .offset(x = (leftDotX + (dotSize / 2f)) - (touchHitboxWidth / 2f))
                    .size(touchHitboxWidth, 28.dp)
                    .graphicsLayer {
                        alpha = handleAlpha
                        scaleX = expandProgress
                        scaleY = expandProgress
                    }
                    .pointerInput(track.id) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragMinOffsetPx = 0f
                                initialMinFrac = currentLeftFrac
                                isDraggingMin = true
                                currentOnDragSelectionChange?.invoke(Triple(currentLeftFrac, currentRightFrac, trackColor))
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
                                currentOnDragSelectionChange?.invoke(Triple(updatedLeft, updatedRight, trackColor))
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Sunday Sounds White Ring Drag Handle with Dark Center
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(2.2.dp, Color.White, CircleShape)
                )
            }

            // ================= RIGHT DRAG HANDLE =================
            Box(
                modifier = Modifier
                    .offset(x = (rightDotX + (dotSize / 2f)) - (touchHitboxWidth / 2f))
                    .size(touchHitboxWidth, 28.dp)
                    .graphicsLayer {
                        alpha = handleAlpha
                        scaleX = expandProgress
                        scaleY = expandProgress
                    }
                    .pointerInput(track.id) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragMaxOffsetPx = 0f
                                initialMaxFrac = currentRightFrac
                                isDraggingMax = true
                                currentOnDragSelectionChange?.invoke(Triple(currentLeftFrac, currentRightFrac, trackColor))
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
                                currentOnDragSelectionChange?.invoke(Triple(updatedLeft, updatedRight, trackColor))
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Sunday Sounds White Ring Drag Handle with Dark Center
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(2.2.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
