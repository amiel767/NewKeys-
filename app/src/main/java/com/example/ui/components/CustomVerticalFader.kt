package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.roundToInt

/**
 * CustomVerticalFader Composable faithful to user reference image `fader_visuel_cible.png`:
 * - Dark background (#1B1E2B)
 * - Fader slot card (#25293A)
 * - Rail & dB reference ticks (#4E556A)
 * - Ultra-bright neon green/aura line (#19EF71)
 * - Thumb cap indicator glowing with audio volume / aura
 */
@Composable
fun CustomVerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    auraColor: Color = Color(0xFF19EF71), // Ultra-bright neon green by default
    audioActivity: Float = 0f,
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
            .fillMaxWidth()
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
        contentAlignment = Alignment.Center
    ) {
        // ================= 0. dB REFERENCE TICKS AND TEXT (0 dB, -inf) =================
        val tickColor = Color(0xFF4E556A)
        val tickPositions = listOf(0.12f, 0.32f, 0.52f, 0.72f, 0.90f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val usableH = h - thumbHeightPx

            tickPositions.forEach { frac ->
                val y = thumbHeightPx / 2f + (1f - frac) * usableH
                // Left tick line
                drawLine(
                    color = tickColor,
                    start = Offset(4.dp.toPx(), y),
                    end = Offset(w * 0.30f, y),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Right tick line
                drawLine(
                    color = tickColor,
                    start = Offset(w * 0.70f, y),
                    end = Offset(w - 4.dp.toPx(), y),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // dB Text labels
        Box(modifier = Modifier.fillMaxSize()) {
            // "0 dB" label
            Text(
                text = "0 dB",
                color = Color(0xFF6B7280),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 2.dp)
            )
            // "-∞" label
            Text(
                text = "-∞",
                color = Color(0xFF6B7280),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 4.dp)
            )
        }

        // ================= 1. RAIL / TRACK AUTHENTIC PNG ELEMENT =================
        val isAudioSoundActive = isEnabled && audioActivity > 0.015f
        val neonAlpha = if (isAudioSoundActive) 1.0f else 0.85f
        val dynamicAura = auraColor.copy(alpha = neonAlpha)

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Glowing neon line in central slot
            Box(
                modifier = Modifier
                    .width(3.2.dp)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(1.6.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                dynamicAura.copy(alpha = 0.90f),
                                Color.White.copy(alpha = 0.95f),
                                dynamicAura.copy(alpha = 0.90f)
                            )
                        )
                    )
            )

            // Glowing neon crossbar slit near top of rail (matching authentic rail design)
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 34.dp)
                        .width(trackWidth * 0.55f)
                        .height(2.2.dp)
                        .clip(RoundedCornerShape(1.1.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    dynamicAura.copy(alpha = 0.70f),
                                    Color.White.copy(alpha = 0.95f),
                                    dynamicAura.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
            }

            // Authentic Rail PNG element (same colors & composition, zero modifications)
            Image(
                painter = painterResource(id = R.drawable.ic_fader_track),
                contentDescription = "Fader Track Rail",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF444444), androidx.compose.ui.graphics.BlendMode.SrcIn)
            )
        }

        // ================= 2. CAP / THUMB BONNET AUTHENTIC PNG ELEMENT =================
        val usableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val offsetYPx = ((1f - localValue.coerceIn(0f, 1f)) * usableHeightPx).roundToInt()

        // Fader Thumb Bonnet (exact PNG with baked-in drop shadow, bevels & colors)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight),
            contentAlignment = Alignment.Center
        ) {
            // Authentic Bonnet PNG element
            Image(
                painter = painterResource(id = R.drawable.ic_fader_thumb),
                contentDescription = "Fader Thumb Cap",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Central neon indicator slit following dynamic fader aura color
            Box(
                modifier = Modifier
                    .width(thumbWidth * 0.44f)
                    .height(3.2.dp)
                    .clip(RoundedCornerShape(1.6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                dynamicAura.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.95f),
                                dynamicAura.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }
    }
}
