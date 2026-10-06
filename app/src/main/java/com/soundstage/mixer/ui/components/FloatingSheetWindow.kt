package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SheetItem
import com.soundstage.mixer.utils.ChordNotationConverter
import com.soundstage.mixer.utils.NotationSystem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Modern Sleek Spotify-style Dynamic Floating Sheet:
 * - 0 Buttons (no X, no arrows), 0 text hints.
 * - 0 Contours / 0 borders.
 * - True free-floating physics: follows finger freely while dragged.
 * - On release, magnetically glides with spring physics to snap to the nearest lateral side (Left or Right).
 * - Multi-touch pinch to scale.
 * - Double-tap anywhere to dismiss with fluid pop-out animation.
 * - Displays complete sheet lyrics and chords with vertical scroll.
 */
@Composable
fun FloatingSheetWindow(
    sheet: SheetItem?,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onClose: () -> Unit,
    globalNotation: NotationSystem = NotationSystem.NASHVILLE,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = sheet != null,
        enter = scaleIn(
            initialScale = 0.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(tween(200)),
        exit = scaleOut(
            targetScale = 0.7f,
            animationSpec = tween(180)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        if (sheet == null) return@AnimatedVisibility

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

        var currentScale by remember(scale) { mutableFloatStateOf(scale.coerceIn(0.75f, 1.5f)) }
        val targetWidth = (330 * currentScale).dp.coerceIn(240.dp, 480.dp)
        val targetMaxHeight = (430 * currentScale).dp.coerceIn(280.dp, 580.dp)
        val targetWidthPx = with(density) { targetWidth.toPx() }

        val coroutineScope = rememberCoroutineScope()
        val offsetX = remember { Animatable(screenWidthPx - targetWidthPx - with(density) { 16.dp.toPx() }) }
        val offsetY = remember { Animatable(with(density) { 50.dp.toPx() }) }

        val pageBgColor = remember(sheet) { sheet.getPageColor() }
        val keyColor = remember(sheet) { sheet.getKeyColor() }

        // Convert chords to active global notation (e.g. Nashville)
        val formattedContent = remember(sheet.content, sheet.key, globalNotation) {
            ChordNotationConverter.convertSheetText(
                text = sheet.content,
                songKey = sheet.key,
                targetSystem = globalNotation,
                isMinor = sheet.key.contains("m") && !sheet.key.contains("maj")
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .width(targetWidth)
                .heightIn(min = 180.dp, max = targetMaxHeight)
                // 0 Borders / 0 contours: pure Spotify card relief
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black,
                    spotColor = Color(0x99000000)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(pageBgColor)
                // Drag gestures with magnetic snap on release
                .pointerInput(sheet.id, screenWidthPx) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(0f, screenWidthPx - targetWidthPx))
                                offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(0f, screenHeightPx - 200f))
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val currentCenterX = offsetX.value + (targetWidthPx / 2f)
                                val snapTargetX = if (currentCenterX < screenWidthPx / 2f) {
                                    with(density) { 14.dp.toPx() } // Snap to Left
                                } else {
                                    screenWidthPx - targetWidthPx - with(density) { 14.dp.toPx() } // Snap to Right
                                }
                                offsetX.animateTo(
                                    targetValue = snapTargetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    )
                }
                // Pinch to scale
                .pointerInput(sheet.id) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            currentScale = (currentScale * zoom).coerceIn(0.75f, 1.5f)
                            onScaleChange(currentScale)
                        }
                    }
                }
                // Double tap anywhere to dismiss
                .pointerInput(sheet.id) {
                    detectTapGestures(
                        onDoubleTap = { onClose() }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("floating_sheet_window")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Minimalist Sleek Header: Title + Key Chip (0 clutter, 0 extra buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sheet.title.ifBlank { "Untitled Sheet" },
                        fontSize = (16 * currentScale).coerceIn(14f, 20f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Tonality Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(keyColor.copy(alpha = 0.24f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sheet.key,
                            fontSize = (11 * currentScale).coerceIn(10f, 13f).sp,
                            fontWeight = FontWeight.Bold,
                            color = keyColor
                        )
                    }
                }

                // Full Vertically Scrollable Content Body (No truncation)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (formattedContent.isBlank()) {
                        Text(
                            text = "(Empty sheet)",
                            fontSize = (13 * currentScale).sp,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val annotatedText = buildAnnotatedString {
                            formattedContent.lines().forEachIndexed { index, line ->
                                val trimmed = line.trim()
                                if (trimmed.startsWith("[") && trimmed.contains("]")) {
                                    val tag = trimmed.substringBefore("]") + "]"
                                    val rest = trimmed.substringAfter("]")
                                    withStyle(SpanStyle(color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)) {
                                        append(tag)
                                    }
                                    if (rest.isNotEmpty()) {
                                        withStyle(SpanStyle(color = Color(0xFFF8FAFC), fontWeight = FontWeight.Medium)) {
                                            append(" $rest")
                                        }
                                    }
                                } else {
                                    withStyle(SpanStyle(color = Color(0xFFE2E8F0), fontWeight = FontWeight.Normal)) {
                                        append(line)
                                    }
                                }
                                if (index < formattedContent.lines().size - 1) {
                                    append("\n")
                                }
                            }
                        }

                        Text(
                            text = annotatedText,
                            fontSize = (sheet.fontSize.toFloat() * 0.85f * currentScale).coerceIn(12f, 24f).sp,
                            fontFamily = FontFamily.Default,
                            lineHeight = ((sheet.fontSize + 6).toFloat() * 0.85f * currentScale).coerceIn(16f, 32f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
