package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.roundToInt

/**
 * CustomVerticalFader Composable.
 *
 * Implements exact fader design matching user's visual spec:
 * 1. Uses transparent PNG resources from `res/drawable/`:
 *    - `ic_fader_track.png` for the rail
 *    - `ic_fader_thumb.png` for the cap/thumb
 * 2. Overlays the thumb on top of the rail in a central Box container.
 * 3. Vertical position (offset Y) maps from 0.0f (bottom) to 1.0f (top) using detectVerticalDragGestures.
 * 4. The glowing neon indicator line (on track & thumb slot) dynamically reflects `auraColor`.
 */
@Composable
fun CustomVerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    auraColor: Color = Color(0xFFA855F7), // Dynamic slot aura color
    isEnabled: Boolean = true,
    trackWidth: Dp = 16.dp,
    trackHeight: Dp = 220.dp,
    thumbWidth: Dp = 38.dp,
    thumbHeight: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            localValue = value.coerceIn(0f, 1f)
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .width(maxOf(trackWidth, thumbWidth))
            .height(trackHeight)
            .onSizeChanged { size ->
                if (size.height > 0) {
                    containerHeightPx = size.height.toFloat()
                }
            }
            .pointerInput(isEnabled, containerHeightPx, thumbHeightPx) {
                if (!isEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val newValue = (1f - ((offset.y - thumbHeightPx / 2f) / usableHeight)).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val newValue = (1f - ((change.position.y - thumbHeightPx / 2f) / usableHeight)).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // ================= 1. RAIL / TRACK (ic_fader_track.png) =================
        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Base PNG track image
            Image(
                painter = painterResource(id = R.drawable.ic_fader_track),
                contentDescription = "Fader Track Rail",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic aura neon glow behind/along the rail slot
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                auraColor.copy(alpha = if (isEnabled) 0.90f else 0.35f),
                                auraColor.copy(alpha = if (isEnabled) 0.65f else 0.20f)
                            )
                        )
                    )
            )
        }

        // ================= 2. CAP / THUMB (ic_fader_thumb.png) =================
        val usableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val offsetYPx = ((1f - localValue.coerceIn(0f, 1f)) * usableHeightPx).roundToInt()

        Box(
            modifier = Modifier
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight),
            contentAlignment = Alignment.Center
        ) {
            // PNG Thumb Image
            Image(
                painter = painterResource(id = R.drawable.ic_fader_thumb),
                contentDescription = "Fader Thumb Cap",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic neon slot indicator line on thumb cap matching aura color
            Box(
                modifier = Modifier
                    .width(thumbWidth * 0.52f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(auraColor.copy(alpha = if (isEnabled) 0.95f else 0.40f))
            )
        }
    }
}
