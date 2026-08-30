package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Ultra-Responsive Multi-touch Polyphonic Virtual Piano Keyboard with:
 * - Direct Octave Jump Chips (C2, C3, C4, C5, C6) for instant fluid scrolling.
 * - Dedicated Scroll Buttons (< and >) and Scroll Lock toggle.
 * - Continuous Glissando / "Notes Runs" across white & black keys without interruption.
 * - Multi-finger simultaneous polyphony with zero latency.
 * - Pitch Bend Wheel with spring-back physics.
 * - C-only markers ("C2", "C3", "C4", "C5", "C6", "C7") covering C2 to C7 range.
 */
@Composable
fun VirtualPianoKeyboard(
    heightFraction: Float,
    pressedKeys: Set<String>,
    onKeyDown: (String) -> Unit,
    onKeyUp: (String) -> Unit,
    onGrabberDrag: (Float) -> Unit,
    onGrabberClick: () -> Unit,
    keyScale: Float = 1.0f,
    onKeyScaleChange: (Float) -> Unit = {},
    pitchBend: Float = 0.0f,
    onPitchBendChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalMaxHeight = 205.dp
    val actualHeight = totalMaxHeight * heightFraction

    if (heightFraction <= 0.02f) {
        return
    }

    val scrollState = rememberScrollState()
    var currentScale by remember { mutableFloatStateOf(keyScale) }
    var isScrollLocked by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Octaves from C2 to C6 + Final C7 key = C2 to C7 (36 White keys + 25 Black keys)
    val octaves = listOf(2, 3, 4, 5, 6)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(actualHeight)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0F101A), Color(0xFF08090E)))
            )
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .testTag("virtual_piano_panel")
    ) {
        // ================= TOP STRIP: DRAG HANDLE & OCTAVE NAVIGATION =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xFF141724))
                .border(0.5.dp, Color(0x22FFFFFF))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(24.dp)
                    .clickable { onGrabberClick() }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onGrabberDrag(dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NeonCyan)
                )
            }

            // Octave Jump Buttons: < [C2] [C3] [C4] [C5] [C6] >
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Scroll Left
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo((scrollState.value - 240).coerceAtLeast(0))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "◀", fontSize = 8.sp, color = TextPrimary)
                }

                octaves.forEach { oct ->
                    val baseWhiteWidthDp = 42.dp * currentScale.coerceIn(0.65f, 1.8f)
                    val whiteWidthPx = with(density) { baseWhiteWidthDp.toPx() }
                    val targetScroll = ((oct - 2) * 7 * whiteWidthPx).toInt()

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2600E5FF))
                            .border(0.8.dp, Color(0x5500E5FF), RoundedCornerShape(4.dp))
                            .clickable {
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(targetScroll)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C$oct",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyanLight
                        )
                    }
                }

                // Scroll Right
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo((scrollState.value + 240).coerceAtMost(scrollState.maxValue))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "▶", fontSize = 8.sp, color = TextPrimary)
                }
            }

            // Lock & Zoom indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isScrollLocked) Color(0xFFFF9100) else Color(0x22FFFFFF))
                        .clickable { isScrollLocked = !isScrollLocked }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isScrollLocked) "🔒 Fixe" else "🔓 Libre",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Main Keyboard Row: Left Controls (Pitch Bend & Scroll Lock) + Piano Keys Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // ================= LEFT CONTROLS: PITCH BEND =================
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF141520))
                    .border(1.dp, Color(0x22FFFFFF))
                    .padding(vertical = 4.dp, horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Pitch Bend Wheel (Spring-loaded to return to center 0)
                PitchBendWheel(
                    currentBend = pitchBend,
                    onBendChange = onPitchBendChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                // Label
                Text(
                    text = "PITCH",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDim2
                )
            }

            // ================= PIANO KEYS CONTAINER =================
            val baseWhiteWidthDp = 42.dp * currentScale.coerceIn(0.65f, 1.8f)
            val whiteWidthPx = with(density) { baseWhiteWidthDp.toPx() }
            val blackKeyWidthPx = whiteWidthPx * 0.62f

            // Total 35 white keys (5 octaves * 7) + 1 C7 = 36 white keys
            val totalWhiteKeys = 36
            val totalKeyboardWidthDp = baseWhiteWidthDp * totalWhiteKeys

            // Multi-touch Pointer-to-Key mapping tracker for smooth glissando
            val pointerKeyMap = remember { mutableStateMapOf<PointerId, String>() }
            val currentOnKeyDown by rememberUpdatedState(onKeyDown)
            val currentOnKeyUp by rememberUpdatedState(onKeyUp)

            val scrollModifier = if (!isScrollLocked) {
                Modifier.horizontalScroll(scrollState)
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = 3.dp)
                    .then(scrollModifier)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (!isScrollLocked) {
                                currentScale = (currentScale * zoom).coerceIn(0.65f, 1.8f)
                                onKeyScaleChange(currentScale)
                            }
                        }
                    }
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
                                            blackWidthPx = blackKeyWidthPx
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
                    }
            ) {
                Row(
                    modifier = Modifier
                        .width(totalKeyboardWidthDp)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    octaves.forEach { oct ->
                        OctaveGroupView(
                            octave = oct,
                            whiteWidthDp = baseWhiteWidthDp,
                            pressedKeys = pressedKeys
                        )
                    }

                    // Final High C7 Key
                    val isC7Pressed = pressedKeys.contains("C7")
                    val keyBrush = if (isC7Pressed) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F2633), Color(0x9900E5FF), NeonCyan)
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFFF2F2F7), Color(0xFFDCDCE8), Color(0xFFC0C0D0))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(baseWhiteWidthDp)
                            .fillMaxHeight()
                            .padding(horizontal = 0.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                            .background(keyBrush)
                            .border(
                                1.dp,
                                if (isC7Pressed) NeonCyan else Color(0x33000000),
                                RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "C7",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isC7Pressed) Color(0xFF002E38) else Color(0xFF1E2238),
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
            .background(Color(0xFF10111A))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            animatedBend.animateTo(0.0f, tween(180))
                            onBendChange(0.0f)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animatedBend.animateTo(0.0f, tween(180))
                            onBendChange(0.0f)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val height = size.height.toFloat()
                        val delta = -dragAmount.y / (height * 0.45f)
                        val newBend = (animatedBend.value + delta).coerceIn(-1.0f, 1.0f)
                        coroutineScope.launch {
                            animatedBend.snapTo(newBend)
                            onBendChange(newBend)
                        }
                    }
                )
            }
            .testTag("pitch_bend_wheel"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
 * High-performance 1-Octave Group Component: 7 White Keys + 5 Black Keys
 */
@Composable
private fun OctaveGroupView(
    octave: Int,
    whiteWidthDp: androidx.compose.ui.unit.Dp,
    pressedKeys: Set<String>
) {
    val whiteNotes = listOf("C", "D", "E", "F", "G", "A", "B")
    val blackNotes = mapOf(
        0 to "C#",
        1 to "D#",
        3 to "F#",
        4 to "G#",
        5 to "A#"
    )

    Box(
        modifier = Modifier
            .width(whiteWidthDp * 7)
            .fillMaxHeight()
    ) {
        // Layer 1: White Keys
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
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
                        .weight(1f)
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
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPressed) Color(0xFF002E38) else Color(0xFF1E2238),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }

        // Layer 2: Black Keys
        blackNotes.forEach { (index, noteName) ->
            val fullKey = "$noteName$octave"
            val isPressed = pressedKeys.contains(fullKey)

            val leftOffsetDp = whiteWidthDp * (index + 0.69f)
            val blackKeyWidthDp = whiteWidthDp * 0.62f

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
                    .shadow(6.dp, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
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
 * Geometric resolver: maps (x, y) coordinates to exact key name.
 */
private fun resolveKeyAtPosition(
    x: Float,
    y: Float,
    totalHeight: Float,
    whiteWidthPx: Float,
    blackWidthPx: Float
): String? {
    if (x < 0 || y < 0 || y > totalHeight) return null

    val octaveIndex = (x / (whiteWidthPx * 7)).toInt()
    val octave = (2 + octaveIndex).coerceIn(2, 6)

    val xWithinOctave = x - (octaveIndex * (whiteWidthPx * 7))
    val isUpperHalf = y <= (totalHeight * 0.60f)

    if (isUpperHalf) {
        val blackSpecs = listOf(
            0 to "C#",
            1 to "D#",
            3 to "F#",
            4 to "G#",
            5 to "A#"
        )

        for ((idx, note) in blackSpecs) {
            val left = whiteWidthPx * (idx + 0.69f)
            val right = left + blackWidthPx
            if (xWithinOctave in left..right) {
                return "$note$octave"
            }
        }
    }

    val whiteIndex = (xWithinOctave / whiteWidthPx).toInt().coerceIn(0, 6)
    val whiteNotes = listOf("C", "D", "E", "F", "G", "A", "B")
    return "${whiteNotes[whiteIndex]}$octave"
}
