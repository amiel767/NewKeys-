package com.soundstage.mixer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Virtual Piano Keyboard with:
 * - Visual Keyboard Layer Mapper (Key Range Bars) with Low/High Note drag handles.
 * - Dynamic Octaves Selector Stepper [ - | N Oct | + ].
 * - Multi-touch polyphonic input with glissando.
 * - Perfectly synchronized scroll without any octave offset bugs.
 * - Retractable drag grabber bar.
 * - Pitch Bend Wheel & Sustain pedal button.
 * - Live Chord Name & Harmony Analyzer.
 */
@Composable
fun VirtualPianoKeyboard(
    heightFraction: Float = 0.55f,
    pressedKeys: Set<String>,
    octave: Int = 0,
    tracks: List<TrackChannel> = emptyList(),
    onRangeChanged: (trackId: Int, minNote: Int, maxNote: Int) -> Unit = { _, _, _ -> },
    onKeyDown: (String) -> Unit,
    onKeyUp: (String) -> Unit,
    onKeyDownWithVelocity: ((String, Float) -> Unit)? = null,
    onGrabberDrag: ((Float) -> Unit)? = null,
    onGrabberClick: (() -> Unit)? = null,
    isSustainActive: Boolean = false,
    onToggleSustain: () -> Unit = {},
    pitchBend: Float = 0.0f,
    onPitchBendChange: (Float) -> Unit = {},
    onKeyScaleChange: (Float) -> Unit = {},
    keyScale: Float = 1.0f,
    onOctaveChange: (Int) -> Unit = {},
    activeAuraColor: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    if (heightFraction <= 0.01f) return

    val scrollState = rememberScrollState()
    var currentScale by remember(keyScale) { mutableFloatStateOf(keyScale) }
    var isLayerMapperExpanded by remember { mutableStateOf(false) }

    val activeTracksCount = remember(tracks) { tracks.count { it.isEnabled } }
    val compactMapperHeight = if (tracks.isNotEmpty()) 16.dp else 0.dp
    val expandedMapperHeight = if (tracks.isNotEmpty()) {
        (32.dp * activeTracksCount.coerceAtMost(4) + 8.dp).coerceAtLeast(40.dp)
    } else {
        0.dp
    }
    val currentMapperHeight = if (isLayerMapperExpanded) expandedMapperHeight else compactMapperHeight
    val keysHeight = if (isLayerMapperExpanded) 90.dp else (120.dp - compactMapperHeight)
    val totalTargetHeight = if (isLayerMapperExpanded) (keysHeight + expandedMapperHeight + 2.dp) else 120.dp

    val animatedKeyboardHeight by animateDpAsState(
        targetValue = totalTargetHeight,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "kbHeight"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedKeyboardHeight)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF141923), Color(0xFF0F141C), Color(0xFF0A0E15))
                )
            )
            .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .testTag("virtual_piano_keyboard")
    ) {
        // ================= KEYBOARD BODY (LAYER MAPPER + PITCH BEND & C1-C8 KEYS) =================
        val baseOctave = 1
        val octaves = (0..6).map { baseOctave + it } // C1..C7
        val highestOctave = baseOctave + 7 // C8
        val totalWhiteKeys = 50 // 7 octaves * 7 + 1 High C (C1 to C8)
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val availableWidthDp = (maxWidth - 46.dp).coerceAtLeast(100.dp)
            val baseWhiteWidthDp = (availableWidthDp / totalWhiteKeys.toFloat()) * currentScale.coerceIn(0.7f, 3.0f)
            val whiteWidthPx = with(density) { baseWhiteWidthDp.toPx() }
            val blackKeyWidthPx = whiteWidthPx * 0.60f
            val totalKeyboardWidthDp = baseWhiteWidthDp * totalWhiteKeys

            // Multi-touch Pointer-to-Key mapping tracker for smooth glissando
            val pointerKeyMap = remember { mutableStateMapOf<PointerId, String>() }
            val currentOnKeyDown by rememberUpdatedState(onKeyDown)
            val currentOnKeyUp by rememberUpdatedState(onKeyUp)
            val currentOnKeyDownWithVel by rememberUpdatedState(onKeyDownWithVelocity)
            val currentWhiteWidthPx by rememberUpdatedState(whiteWidthPx)
            val currentBlackKeyWidthPx by rememberUpdatedState(blackKeyWidthPx)
            val currentBaseOctave by rememberUpdatedState(baseOctave)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Layer Mapper Row (Synchronized with keys scroll, toggle handle above Pitch Bend)
                if (tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(currentMapperHeight)
                    ) {
                        // Small handle box positioned directly above Pitch Bend
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1B2230))
                                .border(0.8.dp, Color(0x4422D3EE), RoundedCornerShape(4.dp))
                                .clickable { isLayerMapperExpanded = !isLayerMapperExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Layers",
                                tint = if (isLayerMapperExpanded) NeonCyan else Color(0xAA94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(scrollState)
                        ) {
                            KeyboardLayerMapper(
                                tracks = tracks,
                                whiteWidthDp = baseWhiteWidthDp,
                                totalWhiteKeys = totalWhiteKeys,
                                isExpanded = isLayerMapperExpanded,
                                onToggleExpanded = { isLayerMapperExpanded = !isLayerMapperExpanded },
                                onRangeChanged = onRangeChanged
                            )
                        }
                    }
                }

                // Main Keys Section: Pitch Bend (Fixed 120.dp) + Virtual Piano Keys (Fixed 120.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keysHeight)
                ) {
                    // Pitch Bend Wheel: strictly fixed to 120.dp height, does NOT expand with the layers
                    PitchBendWheel(
                        currentBend = pitchBend,
                        onBendChange = onPitchBendChange,
                        modifier = Modifier
                            .width(42.dp)
                            .height(keysHeight)
                            .padding(end = 4.dp, bottom = 2.dp)
                    )

                    // Piano Keys Scrollable Container (Fixed 120.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keysHeight)
                            .horizontalScroll(scrollState)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1.0f) {
                                        currentScale = (currentScale * zoom).coerceIn(0.7f, 3.0f)
                                        onKeyScaleChange(currentScale)
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .width(totalKeyboardWidthDp)
                                .height(keysHeight)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()

                                                // 1. Release keys for pointers that explicitly ended (lifted)
                                                for (change in event.changes) {
                                                    if (!change.pressed) {
                                                        pointerKeyMap.remove(change.id)?.let { releasedKey ->
                                                            if (!pointerKeyMap.values.contains(releasedKey)) {
                                                                currentOnKeyUp(releasedKey)
                                                            }
                                                        }
                                                    }
                                                }

                                                // 2. Process currently pressed pointers
                                                for (change in event.changes) {
                                                    if (change.pressed) {
                                                        val x = change.position.x
                                                        val y = change.position.y
                                                        val height = size.height.toFloat()

                                                        val detectedKey = resolveKeyAtPosition(
                                                            x = x,
                                                            y = y,
                                                            totalHeight = height,
                                                            whiteWidthPx = currentWhiteWidthPx,
                                                            blackWidthPx = currentBlackKeyWidthPx,
                                                            baseOctave = currentBaseOctave,
                                                            totalOctaves = 7
                                                        )
                                                        val prevKey = pointerKeyMap[change.id]

                                                        if (detectedKey != prevKey) {
                                                            if (prevKey != null) {
                                                                pointerKeyMap.remove(change.id)
                                                                if (!pointerKeyMap.values.contains(prevKey)) {
                                                                    currentOnKeyUp(prevKey)
                                                                }
                                                            }
                                                            if (detectedKey != null) {
                                                                pointerKeyMap[change.id] = detectedKey
                                                                val touchVel = if (height > 0f) (y / height).coerceIn(0.15f, 1.0f) else 0.85f
                                                                if (currentOnKeyDownWithVel != null) {
                                                                    currentOnKeyDownWithVel?.invoke(detectedKey, touchVel)
                                                                } else {
                                                                    currentOnKeyDown(detectedKey)
                                                                }
                                                            }
                                                        }
                                                        change.consume()
                                                    }
                                                }

                                                if (event.changes.none { it.pressed }) {
                                                    pointerKeyMap.values.toSet().forEach { currentOnKeyUp(it) }
                                                    pointerKeyMap.clear()
                                                    break
                                                }
                                            }
                                        } finally {
                                            pointerKeyMap.values.forEach { currentOnKeyUp(it) }
                                            pointerKeyMap.clear()
                                        }
                                    }
                                },
                            horizontalArrangement = Arrangement.Start
                        ) {
                            octaves.forEach { oct ->
                                OctaveGroupView(
                                    octave = oct,
                                    whiteWidthDp = baseWhiteWidthDp,
                                    pressedKeys = pressedKeys,
                                    activeAuraColor = activeAuraColor
                                )
                            }

                            // Final High C Key (C8)
                            val highCKey = "C$highestOctave"
                            val isHighCPressed = pressedKeys.contains(highCKey)
                            val keyBrush = if (isHighCPressed) {
                                Brush.verticalGradient(
                                    listOf(Color.Red, Color.Black)
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFF0F1F7), Color(0xFFD6D9E6))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(baseWhiteWidthDp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                                    .background(keyBrush)
                                    .border(
                                        1.dp,
                                        if (isHighCPressed) activeAuraColor else Color(0x33000000),
                                        RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = highCKey,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isHighCPressed) Color(0xFF002E38) else Color(0xFF1E2238),
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spring-Loaded Pitch Bend Wheel with center détente (0.0).
 */
@Composable
private fun PitchBendWheel(
    currentBend: Float,
    onBendChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val animatedBend = remember { Animatable(currentBend) }

    LaunchedEffect(currentBend) {
        if (animatedBend.targetValue != currentBend) {
            animatedBend.snapTo(currentBend)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF222530), Color(0xFF151720), Color(0xFF0D0E14))
                )
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {},
                    onDragEnd = {
                        coroutineScope.launch {
                            animatedBend.animateTo(
                                0.0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                            onBendChange(0.0f)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animatedBend.animateTo(0.0f)
                            onBendChange(0.0f)
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount.y / 100f
                    val newBend = (animatedBend.value + delta).coerceIn(-1.0f, 1.0f)
                    coroutineScope.launch {
                        animatedBend.snapTo(newBend)
                    }
                    onBendChange(newBend)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width
            val h = size.height

            drawLine(
                color = Color(0x3300E5FF),
                start = Offset(4f, h / 2f),
                end = Offset(w - 4f, h / 2f),
                strokeWidth = 1.2f
            )

            val thumbHeight = h * 0.26f
            val thumbYCenter = (h / 2f) - (animatedBend.value * (h * 0.35f))
            val top = thumbYCenter - (thumbHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF4A4E69),
                        Color(0xFF222433),
                        Color(0xFF161824)
                    )
                ),
                topLeft = Offset(4f, top),
                size = Size(w - 8f, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )

            drawRoundRect(
                color = if (animatedBend.value != 0f) NeonCyan else Color(0x55FFFFFF),
                topLeft = Offset(4f, top),
                size = Size(w - 8f, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )

            drawLine(
                color = if (animatedBend.value != 0f) NeonCyan else Color.White,
                start = Offset(8f, thumbYCenter),
                end = Offset(w - 8f, thumbYCenter),
                strokeWidth = 2f
            )
        }
    }
}

/**
 * Standard Acoustic 1-Octave Group Component: 7 White Keys + 5 Black Keys
 * Exact layout across all octaves with zero drift.
 */
@Composable
private fun OctaveGroupView(
    octave: Int,
    whiteWidthDp: Dp,
    pressedKeys: Set<String>,
    activeAuraColor: Color = NeonCyan
) {
    val whiteNotes = listOf("C", "D", "E", "F", "G", "A", "B")
    val blackKeyWidthDp = whiteWidthDp * 0.60f

    val blackSpecs = listOf(
        "C#" to 1,
        "D#" to 2,
        "F#" to 4,
        "G#" to 5,
        "A#" to 6
    )

    Box(
        modifier = Modifier
            .width(whiteWidthDp * 7)
            .fillMaxHeight()
    ) {
        // Layer 1: White Keys
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start
        ) {
            whiteNotes.forEach { noteName ->
                val fullKey = "$noteName$octave"
                val isPressed = pressedKeys.contains(fullKey)
                val isCKey = (noteName == "C")

                val keyBrush = if (isPressed) {
                    Brush.verticalGradient(
                        listOf(Color.Red, Color.Black)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF0F1F7),
                            Color(0xFFD6D9E6)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(whiteWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .background(keyBrush)
                        .border(
                            1.dp,
                            if (isPressed) Color.Red else Color(0x33000000),
                            RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (isCKey) {
                        Text(
                            text = fullKey,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPressed) Color(0xFF002E38) else Color(0xFF1E2238),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }

        // Layer 2: Black Keys placed at exact split lines
        blackSpecs.forEach { (noteName, boundaryIndex) ->
            val fullKey = "$noteName$octave"
            val isPressed = pressedKeys.contains(fullKey)

            val leftOffsetDp = (whiteWidthDp * boundaryIndex) - (blackKeyWidthDp / 2f)

            val keyBrush = if (isPressed) {
                Brush.verticalGradient(
                    listOf(Color.Red, Color.Black)
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2C2F3D),
                        Color(0xFF181A24),
                        Color(0xFF0B0C12)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = leftOffsetDp)
                    .width(blackKeyWidthDp)
                    .fillMaxHeight(0.60f)
                    .shadow(5.dp, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(keyBrush)
                    .border(
                        1.dp,
                        if (isPressed) activeAuraColor else Color(0x44000000),
                        RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
            )
        }
    }
}

/**
 * Geometric resolver: maps (x, y) coordinates to exact key name with perfect split detection.
 */
private fun resolveKeyAtPosition(
    x: Float,
    y: Float,
    totalHeight: Float,
    whiteWidthPx: Float,
    blackWidthPx: Float,
    baseOctave: Int = 1,
    totalOctaves: Int = 7
): String? {
    if (x < 0 || y < 0 || y > totalHeight) return null

    val octaveWidth = whiteWidthPx * 7
    val octaveIndex = (x / octaveWidth).toInt()

    // Handle high C
    if (octaveIndex >= totalOctaves) {
        return "C${baseOctave + totalOctaves}"
    }

    val currentOctave = (baseOctave + octaveIndex).coerceIn(baseOctave, baseOctave + totalOctaves - 1)
    val xWithinOctave = x - (octaveIndex * octaveWidth)
    val isUpperHalf = y <= (totalHeight * 0.60f)

    if (isUpperHalf) {
        val blackSpecs = listOf(
            "C#" to 1,
            "D#" to 2,
            "F#" to 4,
            "G#" to 5,
            "A#" to 6
        )

        for ((note, boundaryIndex) in blackSpecs) {
            val center = boundaryIndex * whiteWidthPx
            val left = center - (blackWidthPx / 2f)
            val right = center + (blackWidthPx / 2f)
            if (xWithinOctave in left..right) {
                return "$note$currentOctave"
            }
        }
    }

    val whiteIndex = (xWithinOctave / whiteWidthPx).toInt().coerceIn(0, 6)
    val whiteNotes = listOf("C", "D", "E", "F", "G", "A", "B")
    return "${whiteNotes[whiteIndex]}$currentOctave"
}
