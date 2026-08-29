package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Multi-touch Polyphonic Virtual Piano Keyboard with:
 * - Simultaneous multi-finger chord playing & zero-latency glissando runs.
 * - C-only markers ("C1", "C2", "C3", "C4", "C5", "C6").
 * - Pinch-to-zoom to resize key width.
 * - Horizontal smooth scrolling.
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
    baseOctave: Int = 2,
    onOctaveChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalMaxHeight = 190.dp
    val actualHeight = totalMaxHeight * heightFraction

    if (heightFraction <= 0.02f) {
        return
    }

    val scrollState = rememberScrollState()
    var currentScale by remember { mutableFloatStateOf(keyScale) }

    // 4 Full Octaves available (e.g. C2 to B5)
    val octaves = listOf(2, 3, 4, 5)
    val noteLetters = listOf("C", "D", "E", "F", "G", "A", "B")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(actualHeight)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D0E18), Color(0xFF06070D)))
            )
            .border(1.dp, Color(0x226496FF), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .testTag("virtual_piano_panel")
    ) {
        // Slim Top Grabber Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Color(0x08FFFFFF))
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
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x6667E8F9))
            )
        }

        // Multi-touch Polyphonic Piano Keys Container with Horizontal Scroll & Pinch to Zoom
        val baseWhiteWidthDp = 40.dp * currentScale.coerceIn(0.6f, 1.8f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 3.dp)
                .horizontalScroll(scrollState)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        currentScale = (currentScale * zoom).coerceIn(0.6f, 1.8f)
                        onKeyScaleChange(currentScale)
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                octaves.forEach { oct ->
                    val isBase = (oct == 3)
                    OctaveGroupView(
                        octave = oct,
                        whiteWidthDp = baseWhiteWidthDp,
                        pressedKeys = pressedKeys,
                        onKeyDown = onKeyDown,
                        onKeyUp = onKeyUp,
                        onOctaveShift = { delta -> onOctaveChange((baseOctave + delta).coerceIn(1, 6)) }
                    )
                }
            }
        }
    }
}

/**
 * Renders 1 complete Octave (7 White keys + 5 Black keys) with multi-touch key tracking
 */
@Composable
private fun OctaveGroupView(
    octave: Int,
    whiteWidthDp: androidx.compose.ui.unit.Dp,
    pressedKeys: Set<String>,
    onKeyDown: (String) -> Unit,
    onKeyUp: (String) -> Unit,
    onOctaveShift: (Int) -> Unit
) {
    val whiteKeys = listOf("C", "D", "E", "F", "G", "A", "B").map { "$it$octave" }
    val blackKeyIndices = mapOf(
        0 to "C#$octave",
        1 to "D#$octave",
        3 to "F#$octave",
        4 to "G#$octave",
        5 to "A#$octave"
    )

    val currentOnKeyDown by rememberUpdatedState(onKeyDown)
    val currentOnKeyUp by rememberUpdatedState(onKeyUp)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(whiteWidthDp * 7)
    ) {
        // 1. White Keys Row
        Row(modifier = Modifier.fillMaxSize()) {
            whiteKeys.forEach { keyId ->
                val isPressed = pressedKeys.contains(keyId)
                val isCKey = keyId.startsWith("C") && !keyId.startsWith("C#")

                val keyBrush = if (isPressed) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F2633), Color(0x9906B6D4), NeonCyan)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFFE8E8EE), Color(0xFFD4D4DF), Color(0xFFB8B8C8))
                    )
                }

                Box(
                    modifier = Modifier
                        .width(whiteWidthDp)
                        .fillMaxHeight()
                        .padding(horizontal = 0.5.dp)
                        .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .background(keyBrush)
                        .border(
                            1.dp,
                            if (isPressed) NeonCyan else Color(0x33000000),
                            RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                        )
                        .pointerInput(keyId) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyDown = event.changes.any { it.pressed }
                                    if (anyDown) {
                                        currentOnKeyDown(keyId)
                                    } else {
                                        currentOnKeyUp(keyId)
                                        break
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Only display C note labels ("C1", "C2", "C3", "C4"...) per user request
                    if (isCKey) {
                        Text(
                            text = keyId,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPressed) Color(0xFF002E38) else Color(0xFF1E2238),
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                                .clickable { onOctaveShift(1) }
                        )
                    }
                }
            }
        }

        // 2. Black Keys Layer
        val blackKeyWidthDp = whiteWidthDp * 0.62f

        blackKeyIndices.forEach { (index, blackKeyId) ->
            val isPressed = pressedKeys.contains(blackKeyId)
            val leftOffsetDp = (whiteWidthDp * (index + 1)) - (blackKeyWidthDp / 2)

            val blackKeyBrush = if (isPressed) {
                Brush.verticalGradient(
                    listOf(Color(0xFF161822), Color(0xCC0891B2), NeonCyanLight)
                )
            } else {
                Brush.verticalGradient(
                    listOf(Color(0xFF282835), Color(0xFF14141C), Color(0xFF08080E))
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = leftOffsetDp)
                    .width(blackKeyWidthDp)
                    .fillMaxHeight(0.62f)
                    .shadow(5.dp, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(blackKeyBrush)
                    .border(
                        1.dp,
                        if (isPressed) NeonCyan else Color(0x44FFFFFF),
                        RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
                    .pointerInput(blackKeyId) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyDown = event.changes.any { it.pressed }
                                if (anyDown) {
                                    currentOnKeyDown(blackKeyId)
                                } else {
                                    currentOnKeyUp(blackKeyId)
                                    break
                                }
                            }
                        }
                    }
            )
        }
    }
}
