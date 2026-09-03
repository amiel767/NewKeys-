package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Virtual Piano Keyboard with:
 * - 3 Full Octaves clearly visible on screen by default.
 * - Multi-touch polyphonic input with glissando.
 * - Perfectly synchronized scroll without any octave offset bugs.
 * - Retractable drag grabber bar.
 * - Pitch Bend Wheel & Sustain pedal button.
 * - Live Chord Name & Harmony Analyzer (11th, 13th, Altered, Shells).
 */
@Composable
fun VirtualPianoKeyboard(
    heightFraction: Float = 0.55f,
    pressedKeys: Set<String>,
    octave: Int = 0,
    onKeyDown: (String) -> Unit,
    onKeyUp: (String) -> Unit,
    onGrabberDrag: ((Float) -> Unit)? = null,
    onGrabberClick: (() -> Unit)? = null,
    isSustainActive: Boolean = false,
    onToggleSustain: () -> Unit = {},
    pitchBend: Float = 0.0f,
    onPitchBendChange: (Float) -> Unit = {},
    onKeyScaleChange: (Float) -> Unit = {},
    keyScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    if (heightFraction <= 0.01f) return

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var currentScale by remember(keyScale) { mutableFloatStateOf(keyScale) }

    val detectedChord = remember(pressedKeys) {
        ChordCalculator.detect(pressedKeys)
    }

    val keyboardHeightDp = (heightFraction * 340f).coerceIn(60f, 360f).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(keyboardHeightDp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF141923), Color(0xFF0F141C), Color(0xFF0A0E15))
                )
            )
            .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .testTag("virtual_piano_keyboard")
    ) {
        // Grabber bar to resize / collapse keyboard with prominent touch area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Color(0xFF181F2C))
                .clickable { onGrabberClick?.invoke() }
                .pointerInput(Unit) {
                    if (onGrabberDrag != null) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            onGrabberDrag(dragAmount)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x88FFFFFF))
            )
        }

        // ================= 1. CHORD ANALYZER & SCROLL CONTROLS TOP BAR =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color(0xFF161C28))
                .border(0.8.dp, Color(0x22FFFFFF))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Real-time Dynamic Chord & Inversion Formula Display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "CHORD:",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E95A5)
                )

                if (detectedChord != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x3322D3EE))
                            .border(1.dp, NeonCyan, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = detectedChord.primaryName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyanLight
                        )
                    }

                    Text(
                        text = detectedChord.alternateNames,
                        fontSize = 9.5.sp,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Jouez un accord...",
                        fontSize = 9.5.sp,
                        color = Color(0x55FFFFFF)
                    )
                }
            }

            // Right: Octave Jump Buttons & Sustain Pedal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Sustain Button
                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isSustainActive) NeonCyan else Color(0x1FFFFFFF))
                        .border(1.dp, if (isSustainActive) Color.White else Color(0x33FFFFFF), RoundedCornerShape(5.dp))
                        .clickable { onToggleSustain() }
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SUSTAIN",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSustainActive) Color(0xFF002E38) else Color(0xFFC4C7D5)
                    )
                }

                // Scroll Left Octave ◀
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(
                                    (scrollState.value - 200).coerceAtLeast(0),
                                    tween(250)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "◀", fontSize = 9.5.sp, color = Color.White)
                }

                // Scroll Right Octave ▶
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(
                                    (scrollState.value + 200).coerceAtMost(scrollState.maxValue),
                                    tween(250)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "▶", fontSize = 9.5.sp, color = Color.White)
                }
            }
        }

        // ================= 2. KEYBOARD BODY (PITCH BEND + SCROLLABLE 3-OCTAVE KEYS) =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 2.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
        ) {
            // Pitch Bend Wheel
            PitchBendWheel(
                currentBend = pitchBend,
                onBendChange = onPitchBendChange,
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .padding(end = 4.dp, bottom = 2.dp)
            )

            // Keys Container (5 full octaves shifted by global octave, styled to display 3 full octaves in default view)
            val baseOctave = (2 + octave).coerceIn(0, 4)
            val octaves = (0..4).map { baseOctave + it }
            val highestOctave = baseOctave + 5
            val density = LocalDensity.current

            // 27.5.dp per white key allows 3 full octaves (21 white keys ≈ 577dp) to be fully visible simultaneously
            val baseWhiteWidthDp = 27.5.dp * currentScale.coerceIn(0.55f, 2.2f)
            val whiteWidthPx = with(density) { baseWhiteWidthDp.toPx() }
            val blackKeyWidthPx = whiteWidthPx * 0.60f

            // Total 35 white keys (5 octaves * 7) + 1 C7 = 36 white keys
            val totalWhiteKeys = 36
            val totalKeyboardWidthDp = baseWhiteWidthDp * totalWhiteKeys

            // Multi-touch Pointer-to-Key mapping tracker for smooth glissando
            val pointerKeyMap = remember { mutableStateMapOf<PointerId, String>() }
            val currentOnKeyDown by rememberUpdatedState(onKeyDown)
            val currentOnKeyUp by rememberUpdatedState(onKeyUp)

            // Auto-center initially around C3/C4 so 3 full octaves (C3 to B5) are immediately in view
            LaunchedEffect(whiteWidthPx) {
                if (scrollState.value == 0 && whiteWidthPx > 0) {
                    val initialScrollPx = (whiteWidthPx * 7f).toInt()
                    scrollState.scrollTo(initialScrollPx)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = 2.dp)
                    .horizontalScroll(scrollState)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1.0f) {
                                currentScale = (currentScale * zoom).coerceIn(0.55f, 2.2f)
                                onKeyScaleChange(currentScale)
                            }
                        }
                    }
            ) {
                // Placing pointerInput directly on full-width Row ensures change.position.x
                // maps precisely to absolute key coordinates from 0 to totalKeyboardWidthPx with ZERO octave drift!
                Row(
                    modifier = Modifier
                        .width(totalKeyboardWidthDp)
                        .fillMaxHeight()
                        .pointerInput(whiteWidthPx, currentScale) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val activePointerIds = event.changes.filter { it.pressed }.map { it.id }.toSet()

                                    // Release keys for pointers that went up
                                    val releasedPointers = pointerKeyMap.keys.filter { it !in activePointerIds }
                                    for (pId in releasedPointers) {
                                        pointerKeyMap[pId]?.let { currentOnKeyUp(it) }
                                        pointerKeyMap.remove(pId)
                                    }

                                    // Process active pointers
                                    for (change in event.changes) {
                                        if (change.pressed) {
                                            val x = change.position.x
                                            val y = change.position.y
                                            val height = size.height.toFloat()

                                            val detectedKey = resolveKeyAtPosition(
                                                x = x,
                                                y = y,
                                                totalHeight = height,
                                                whiteWidthPx = whiteWidthPx,
                                                blackWidthPx = blackKeyWidthPx,
                                                baseOctave = baseOctave
                                            )

                                            val prevKey = pointerKeyMap[change.id]
                                            if (detectedKey != null && detectedKey != prevKey) {
                                                if (prevKey != null) {
                                                    currentOnKeyUp(prevKey)
                                                }
                                                pointerKeyMap[change.id] = detectedKey
                                                currentOnKeyDown(detectedKey)
                                            }
                                            change.consume()
                                        }
                                    }

                                    if (event.changes.none { it.pressed }) {
                                        // All fingers lifted
                                        pointerKeyMap.values.forEach { currentOnKeyUp(it) }
                                        pointerKeyMap.clear()
                                        break
                                    }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.Start
                ) {
                    octaves.forEach { oct ->
                        OctaveGroupView(
                            octave = oct,
                            whiteWidthDp = baseWhiteWidthDp,
                            pressedKeys = pressedKeys
                        )
                    }

                    // Final High C Key
                    val highCKey = "C$highestOctave"
                    val isHighCPressed = pressedKeys.contains(highCKey)
                    val keyBrush = if (isHighCPressed) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F2633), Color(0x9900E5FF), NeonCyan)
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
                                if (isHighCPressed) NeonCyan else Color(0x33000000),
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
    pressedKeys: Set<String>
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
                        listOf(Color(0xFF0F2633), Color(0x9900E5FF), NeonCyan)
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
                            if (isPressed) NeonCyan else Color(0x33000000),
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
                    listOf(NeonCyan, Color(0xFF006B80), Color(0xFF003844))
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
                        if (isPressed) Color.White else Color(0x44000000),
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
    baseOctave: Int = 2
): String? {
    if (x < 0 || y < 0 || y > totalHeight) return null

    val octaveWidth = whiteWidthPx * 7
    val octaveIndex = (x / octaveWidth).toInt()
    val currentOctave = (baseOctave + octaveIndex).coerceIn(baseOctave, baseOctave + 4)

    // Handle high C
    if (octaveIndex >= 5) {
        return "C${baseOctave + 5}"
    }

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
