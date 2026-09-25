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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
    isLayerExpanded: Boolean = false,
    onToggleLayerExpanded: (() -> Unit)? = null,
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
    val scrollState = rememberScrollState()
    var currentScale by remember(keyScale) { mutableFloatStateOf(keyScale) }
    var localLayerExpanded by remember { mutableStateOf(false) }
    val isLayerMapperExpanded = onToggleLayerExpanded?.let { isLayerExpanded } ?: localLayerExpanded
    val toggleMapperExpanded = {
        if (onToggleLayerExpanded != null) {
            onToggleLayerExpanded()
        } else {
            localLayerExpanded = !localLayerExpanded
        }
    }

    val activeTracksCount = remember(tracks) { tracks.count { it.isEnabled }.coerceAtLeast(1) }
    val keysHeight = 48.dp
    val visibleTracksCount = remember(tracks) { tracks.count { it.isEnabled }.coerceIn(1, 8) }
    val expandedTargetHeight = (24.dp * visibleTracksCount + 16.dp).coerceIn(58.dp, 160.dp)

    // Continuous smooth iOS-style growth animation: lines expand directly from resting strip into deployed layer
    val animatedLayerHeight by animateDpAsState(
        targetValue = if (isLayerMapperExpanded) expandedTargetHeight else 12.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "layer_growth_animation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(Color(0xFF141722))
            .padding(start = 2.dp, end = 2.dp, top = 1.dp, bottom = 2.dp)
            .testTag("virtual_piano_keyboard")
    ) {
        // ================= KEYBOARD BODY (LAYER MAPPER + PITCH BEND & A1-C8 KEYS) =================
        // Keyboard range starting at A1 (MIDI 33), Bb1 (34), B1 (35) then C2 to C8 (45 white keys total)
        val octaves = (2..7).toList() // 6 octaves from C2 to C7 (42 white keys)
        val highestOctave = 8 // C8
        val totalWhiteKeys = 45 // 2 (A1, B1) + 42 (C2..B7) + 1 (C8) = 45
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 1.dp)
        ) {
            val availableWidthDp = (maxWidth - 42.dp).coerceAtLeast(100.dp)
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

            // Computer mouse style live blue translucent selection overlay range over the keyboard
            var activeDragSelectionRange by remember { mutableStateOf<Pair<Float, Float>?>(null) }

            // Filter pressed keys for visualization: display only keys with active physical touch or USB MIDI press
            val physicalScreenTouches = pointerKeyMap.values.toSet()
            val displayedPressedKeys = if (physicalScreenTouches.isNotEmpty()) physicalScreenTouches else pressedKeys

            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Single Continuous Growing Layer Mapper: anchored above the keys, expands upwards without pushing faders
                if (tracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                            .zIndex(100f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(animatedLayerHeight)
                                .shadow(if (isLayerMapperExpanded) 16.dp else 0.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (isLayerMapperExpanded) Color(0xF2121520) else Color(0xFF181B26))
                                .border(
                                    width = if (isLayerMapperExpanded) 1.dp else 0.8.dp,
                                    color = if (isLayerMapperExpanded) Color(0x6060A5FA) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(start = 42.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(scrollState)
                            ) {
                                KeyboardLayerMapper(
                                    tracks = tracks,
                                    whiteWidthDp = baseWhiteWidthDp,
                                    totalWhiteKeys = totalWhiteKeys,
                                    isExpanded = isLayerMapperExpanded,
                                    onToggleExpanded = { toggleMapperExpanded() },
                                    onRangeChanged = onRangeChanged,
                                    onDragSelectionChange = { range -> activeDragSelectionRange = range },
                                    modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, end = if (isLayerMapperExpanded) 28.dp else 0.dp)
                                )
                            }

                            if (isLayerMapperExpanded) {
                                IconButton(
                                    onClick = { toggleMapperExpanded() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .padding(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fermer Layer Mapper",
                                        tint = Color.White.copy(alpha = 0.80f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }

                    // Piano Keys Row (Pitch bend + Keys)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(keysHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pitch Bend Wheel aligned with piano keys
                        PitchBendWheel(
                            currentBend = pitchBend,
                            onBendChange = onPitchBendChange,
                            modifier = Modifier
                                .width(38.dp)
                                .height(keysHeight)
                                .padding(end = 4.dp, bottom = 1.dp)
                        )

                        // Piano Keys Scrollable Container
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
                                                                blackWidthPx = currentBlackKeyWidthPx
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
                                // 1. Initial 2 White Keys: A1 and B1 (with Bb1 / A#1 black key)
                                IntroA1B1GroupView(
                                    whiteWidthDp = baseWhiteWidthDp,
                                    pressedKeys = displayedPressedKeys,
                                    activeAuraColor = activeAuraColor
                                )

                                // 2. Octaves C2 through C7 (6 complete octaves * 7 white keys = 42)
                                octaves.forEach { oct ->
                                    OctaveGroupView(
                                        octave = oct,
                                        whiteWidthDp = baseWhiteWidthDp,
                                        pressedKeys = displayedPressedKeys,
                                        activeAuraColor = activeAuraColor
                                    )
                                }

                                // 3. Final High C Key (C8)
                                val highCKey = "C$highestOctave"
                                val isHighCPressed = displayedPressedKeys.contains(highCKey)
                                StudioWhiteKey(
                                    keyName = highCKey,
                                    isPressed = isHighCPressed,
                                    isCKey = true,
                                    whiteWidthDp = baseWhiteWidthDp,
                                    activeAuraColor = activeAuraColor
                                )
                            }

                            // Computer mouse-style live blue translucent selection overlay over the Virtual Keyboard
                            activeDragSelectionRange?.let { (startFrac, endFrac) ->
                                val minFrac = minOf(startFrac, endFrac)
                                val maxFrac = maxOf(startFrac, endFrac)
                                val overlayStartXDp = (minFrac * baseWhiteWidthDp.value).dp
                                val overlayEndXDp = (maxFrac * baseWhiteWidthDp.value).dp
                                val overlayWidthDp = (overlayEndXDp - overlayStartXDp).coerceAtLeast(2.dp)

                                Box(
                                    modifier = Modifier
                                        .offset(x = overlayStartXDp)
                                        .width(overlayWidthDp)
                                        .height(keysHeight)
                                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(Color(0x403B82F6))
                                        .border(
                                            width = 1.2.dp,
                                            color = Color(0xCC60A5FA),
                                            shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                                        )
                                )
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
            if (w <= 8f || h <= 8f) return@Canvas
            val safeBend = if (animatedBend.value.isNaN()) 0f else animatedBend.value.coerceIn(-1f, 1f)

            drawLine(
                color = Color(0x3300E5FF),
                start = Offset(4f, h / 2f),
                end = Offset(w - 4f, h / 2f),
                strokeWidth = 1.2f
            )

            val thumbHeight = (h * 0.26f).coerceAtLeast(2f)
            val thumbYCenter = (h / 2f) - (safeBend * (h * 0.35f))
            val top = thumbYCenter - (thumbHeight / 2f)
            val thumbWidth = (w - 8f).coerceAtLeast(2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF4A4E69),
                        Color(0xFF222433),
                        Color(0xFF161824)
                    )
                ),
                topLeft = Offset(4f, top),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )

            drawRoundRect(
                color = if (safeBend != 0f) NeonCyan else Color(0x55FFFFFF),
                topLeft = Offset(4f, top),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )

            drawLine(
                color = if (safeBend != 0f) NeonCyan else Color.White,
                start = Offset(8f, thumbYCenter),
                end = Offset(w - 8f, thumbYCenter),
                strokeWidth = 2f
            )
        }
    }
}

/**
 * Ultra High Quality Studio White Piano Key (Ivoire Polie & Biseaux 3D)
 */
@Composable
private fun StudioWhiteKey(
    keyName: String,
    isPressed: Boolean,
    isCKey: Boolean,
    whiteWidthDp: Dp,
    activeAuraColor: Color,
    modifier: Modifier = Modifier
) {
    val idleKeyBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFAFBFC), // Ivory top crest specular
                Color(0xFFF3F5FA), // Polished ivory body
                Color(0xFFDFE4F0), // Lower acoustic body gradient
                Color(0xFFC7CEDF)  // Bottom edge 3D lip
            )
        )
    }

    val pressedKeyBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFF1E40), // Bright Red top impact
                Color(0xFFB80026), // Deep rich crimson
                Color(0xFF4A000E), // Shadow burgundy
                Color(0xFF160004)  // Deep black base
            )
        )
    }

    Box(
        modifier = modifier
            .width(whiteWidthDp)
            .fillMaxHeight()
            .offset(y = if (isPressed) 1.5.dp else 0.dp)
            .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
            .background(if (isPressed) pressedKeyBrush else idleKeyBrush)
            .border(
                width = if (isPressed) 1.3.dp else 0.8.dp,
                color = if (isPressed) Color(0xFFFF2A55) else Color(0x35000000),
                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Specular reflection along the left edge of the white key
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(0.7.dp)
                .fillMaxHeight(0.95f)
                .background(Color(0x66FFFFFF))
        )

        // Bottom bevel shadow (gives 3D physical keyboard depth into the chassis)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x2A000000))
                    )
                )
        )

        if (isCKey) {
            Text(
                text = keyName,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPressed) Color.White else Color(0x88111827),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * Ultra High Quality Studio Black Piano Key (Ébène Mat, Biseaux 3D & Facettes Spéculaires)
 */
@Composable
private fun StudioBlackKey(
    keyName: String,
    isPressed: Boolean,
    leftOffsetDp: Dp,
    blackKeyWidthDp: Dp,
    activeAuraColor: Color,
    modifier: Modifier = Modifier
) {
    val idleBlackBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF262936), // Matte charcoal top facet
                Color(0xFF171A24), // Smooth ebony body
                Color(0xFF0C0E14), // Front face drop
                Color(0xFF050608)  // Deep shadow base
            )
        )
    }

    val pressedBlackBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFF2247), // Glowing red top crest
                Color(0xFF99001C), // Deep crimson
                Color(0xFF3B000B), // Dark burgundy
                Color(0xFF120003)  // Black shadow base
            )
        )
    }

    Box(
        modifier = modifier
            .offset(x = leftOffsetDp, y = if (isPressed) 2.dp else 0.dp)
            .width(blackKeyWidthDp)
            .fillMaxHeight(0.60f)
            .shadow(
                elevation = if (isPressed) 1.5.dp else 5.dp,
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(if (isPressed) pressedBlackBrush else idleBlackBrush)
            .border(
                width = 1.dp,
                color = if (isPressed) Color(0xFFFF2A55) else Color(0x40000000),
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
    ) {
        // Specular left bevel highlight (liseré de lumière sur l'arête d'ébène)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(0.7.dp)
                .fillMaxHeight(0.92f)
                .background(Color(0x35FFFFFF))
        )

        // Front face 3D bevel ridge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x25FFFFFF), Color.Transparent)
                    )
                )
        )
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

                StudioWhiteKey(
                    keyName = fullKey,
                    isPressed = isPressed,
                    isCKey = isCKey,
                    whiteWidthDp = whiteWidthDp,
                    activeAuraColor = activeAuraColor
                )
            }
        }

        // Layer 2: Black Keys placed at exact split lines
        blackSpecs.forEach { (noteName, boundaryIndex) ->
            val fullKey = "$noteName$octave"
            val isPressed = pressedKeys.contains(fullKey)
            val leftOffsetDp = (whiteWidthDp * boundaryIndex) - (blackKeyWidthDp / 2f)

            StudioBlackKey(
                keyName = fullKey,
                isPressed = isPressed,
                leftOffsetDp = leftOffsetDp,
                blackKeyWidthDp = blackKeyWidthDp,
                activeAuraColor = activeAuraColor
            )
        }
    }
}

/**
 * Initial Group Component: A1, Bb1 (A#1), B1
 * First two white keys and one black key of the 7-octave virtual keyboard.
 */
@Composable
private fun IntroA1B1GroupView(
    whiteWidthDp: Dp,
    pressedKeys: Set<String>,
    activeAuraColor: Color = NeonCyan
) {
    val blackKeyWidthDp = whiteWidthDp * 0.60f
    Box(
        modifier = Modifier
            .width(whiteWidthDp * 2)
            .fillMaxHeight()
    ) {
        // White Keys: A1 and B1
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start
        ) {
            listOf("A1", "B1").forEach { fullKey ->
                val isPressed = pressedKeys.contains(fullKey)
                StudioWhiteKey(
                    keyName = fullKey,
                    isPressed = isPressed,
                    isCKey = false,
                    whiteWidthDp = whiteWidthDp,
                    activeAuraColor = activeAuraColor
                )
            }
        }

        // Black Key: Bb1 / A#1 at split line between A1 and B1
        val fullBlackKey = "A#1"
        val isBlackPressed = pressedKeys.contains("A#1") || pressedKeys.contains("Bb1")
        val leftOffsetDp = whiteWidthDp - (blackKeyWidthDp / 2f)

        StudioBlackKey(
            keyName = fullBlackKey,
            isPressed = isBlackPressed,
            leftOffsetDp = leftOffsetDp,
            blackKeyWidthDp = blackKeyWidthDp,
            activeAuraColor = activeAuraColor
        )
    }
}

/**
 * Geometric resolver: maps (x, y) coordinates to exact key name starting at A1 (MIDI 33).
 */
private fun resolveKeyAtPosition(
    x: Float,
    y: Float,
    totalHeight: Float,
    whiteWidthPx: Float,
    blackWidthPx: Float
): String? {
    if (x < 0 || y < 0 || y > totalHeight) return null

    val isUpperHalf = y <= (totalHeight * 0.60f)

    // Section 1: Intro (A1, Bb1 / A#1, B1) = 2 white keys width
    val introWidthPx = whiteWidthPx * 2f
    if (x < introWidthPx) {
        if (isUpperHalf) {
            val center = whiteWidthPx
            val left = center - (blackWidthPx / 2f)
            val right = center + (blackWidthPx / 2f)
            if (x in left..right) {
                return "A#1"
            }
        }
        return if (x < whiteWidthPx) "A1" else "B1"
    }

    // Section 2: Octaves C2 through C7 (6 octaves * 7 white keys)
    val xFromC2 = x - introWidthPx
    val octaveWidth = whiteWidthPx * 7f
    val octaveIndex = (xFromC2 / octaveWidth).toInt()

    // Handle high C8
    if (octaveIndex >= 6) {
        return "C8"
    }

    val currentOctave = 2 + octaveIndex
    val xWithinOctave = xFromC2 - (octaveIndex * octaveWidth)

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
