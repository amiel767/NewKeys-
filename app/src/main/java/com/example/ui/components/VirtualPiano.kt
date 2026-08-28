package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun VirtualPianoKeyboard(
    heightFraction: Float,
    pressedKeys: Set<String>,
    onKeyDown: (String) -> Unit,
    onKeyUp: (String) -> Unit,
    onGrabberDrag: (Float) -> Unit,
    onGrabberClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMaxHeight = 180.dp
    val actualHeight = totalMaxHeight * heightFraction

    if (heightFraction <= 0.02f) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(actualHeight)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D0E1A), Color(0xFF07080F)))
            )
            .border(1.dp, Color(0x1F6496FF), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .testTag("virtual_piano_panel")
    ) {
        // Slim Top Grabber Bar (No clutter buttons per spec)
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

        // Piano Keys View (2 Full Octaves: C3 to B4)
        val whiteKeysList = listOf(
            "C3", "D3", "E3", "F3", "G3", "A3", "B3",
            "C4", "D4", "E4", "F4", "G4", "A4", "B4"
        )
        val blackKeyMap = mapOf(
            0 to "C#3", 1 to "D#3", 3 to "F#3", 4 to "G#3", 5 to "A#3",
            7 to "C#4", 8 to "D#4", 10 to "F#4", 11 to "G#4", 12 to "A#4"
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        ) {
            val totalWidth = maxWidth
            val numWhite = whiteKeysList.size
            val whiteKeyWidth = totalWidth / numWhite

            // White Keys Row
            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeysList.forEach { keyId ->
                    val isPressed = pressedKeys.contains(keyId)
                    val noteLabel = keyId.first().toString()

                    val keyBrush = if (isPressed) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF12141F), Color(0x8022D3EE), NeonCyan)
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFF141624), Color(0xFF090A12))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 1.dp)
                            .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                            .background(keyBrush)
                            .border(
                                1.dp,
                                if (isPressed) NeonCyan else Color(0x265078DC),
                                RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                            )
                            .pointerInput(keyId) {
                                detectTapGestures(
                                    onPress = {
                                        onKeyDown(keyId)
                                        tryAwaitRelease()
                                        onKeyUp(keyId)
                                    }
                                )
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = noteLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPressed) Color(0xFF002E38) else Color(0xFF4A5578),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            // Black Keys Layer
            val blackKeyWidth = whiteKeyWidth * 0.65f
            val blackKeyHeight = maxHeight * 0.60f

            blackKeyMap.forEach { (index, blackKeyId) ->
                val isPressed = pressedKeys.contains(blackKeyId)
                val leftOffset = (whiteKeyWidth * (index + 1)) - (blackKeyWidth / 2)

                val blackKeyBrush = if (isPressed) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF161822), Color(0x990891B2), NeonCyanLight)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFF1C1E2A), Color(0xFF05060A))
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = leftOffset)
                        .width(blackKeyWidth)
                        .height(blackKeyHeight)
                        .shadow(6.dp, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(blackKeyBrush)
                        .border(
                            1.dp,
                            if (isPressed) NeonCyan else Color(0x335078DC),
                            RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                        )
                        .pointerInput(blackKeyId) {
                            detectTapGestures(
                                onPress = {
                                    onKeyDown(blackKeyId)
                                    tryAwaitRelease()
                                    onKeyUp(blackKeyId)
                                }
                            )
                        }
                )
            }
        }
    }
}
