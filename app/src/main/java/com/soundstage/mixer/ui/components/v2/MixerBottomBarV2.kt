package com.soundstage.mixer.ui.components.v2

import android.os.Debug
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SubSceneSnapshot
import com.soundstage.mixer.model.TrackChannel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val PanelTop = Color(0xFF2B3348)
private val PanelBottom = Color(0xFF1D2433)
private val TextMain = Color(0xFFE9EDF5)
private val TextDim = Color(0xFF7B859A)
private val Accent = Color(0xFF8FB2E8)

@Composable
fun MixerBottomBarV2(
    masterTrack: TrackChannel,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterFxClick: () -> Unit,
    bpm: Int, onBpmChange: (Int) -> Unit,
    isSnapshotArmMode: Boolean, onToggleSnapshotArm: () -> Unit,
    activeSnapshotSlot: String?,
    snapshots: Map<String, SubSceneSnapshot>,
    configuredSnapshotSlots: List<String>,
    onSnapshotSlotClick: (String) -> Unit,
    onAddSnapshot: (String) -> String?,
    onDeleteSnapshot: (String) -> Unit,
    snapshotCustomNames: Map<String, String>,
    onRenameSnapshotSlot: (String, String) -> Unit,
    isKeyboardActive: Boolean, onToggleKeyboard: () -> Unit,
    isLayerExpanded: Boolean = false, onToggleLayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var manageSlotKey by remember { mutableStateOf<String?>(null) }
    // Name prompt shown when a snapshot is created (JSX-like flow).
    var namingSlotKey by remember { mutableStateOf<String?>(null) }

    Row(modifier.height(58.dp), // JSX bottom bar is 48; stretched deeper as asked
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {

        ResourceMonitorV2(94.dp)

        MixerModuleV2(modifier = Modifier.width(92.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                RoundStepV2(size = 26.dp, onClick = { onBpmChange((bpm - 1).coerceIn(40, 240)) }) {
                    Text("−", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$bpm", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    Text("BPM", color = TextDim, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp, lineHeight = 9.sp)
                }
                RoundStepV2(size = 26.dp, onClick = { onBpmChange((bpm + 1).coerceIn(40, 240)) }) {
                    Text("+", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Snapshot cases share the free room, but each is capped at the JSX
        // max-width (Default 150, numbered 124). `weight(1f)` cannot be used here:
        // it hands the child an exact width and would ignore the cap, which is what
        // made a lone case stretch across the whole row.
        BoxWithConstraints(Modifier.weight(1f)) {
            val count = configuredSnapshotSlots.size + if (configuredSnapshotSlots.size < 5) 1 else 0
            val gapTotal = 6.dp * (count - 1).coerceAtLeast(0)
            val share = if (count > 0) (maxWidth - gapTotal) / count else 0.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                configuredSnapshotSlots.forEachIndexed { idx, key ->
                    val defaultLabel = if (key == "slot_default") "Default" else "Snapshot ${idx}"
                    val displayName = snapshotCustomNames[key] ?: defaultLabel
                    val isActive = activeSnapshotSlot == key || (activeSnapshotSlot == null && key == "slot_default")
                    val cap = if (key == "slot_default") 150.dp else 124.dp
                    SnapshotCaseV2(
                        key = key,
                        displayName = displayName,
                        fallbackNumber = if (key == "slot_default") null else idx,
                        isActive = isActive,
                        isDeletable = key != "slot_default",
                        width = minOf(cap, share),
                        onClick = { onSnapshotSlotClick(key) },
                        onLongPress = { manageSlotKey = key },
                        onDelete = { onDeleteSnapshot(key) }
                    )
                }
                if (configuredSnapshotSlots.size < 5) {
                    // JSX "+ snapshot" case (MixerPreview.jsx:470): a dashed outline, no
                    // fill, no elevation, so it reads as an empty slot next to the others.
                    Box(
                        Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .insetRelief(
                                cornerRadius = 14.dp,
                                light = Color.White.copy(alpha = 0.09f),
                                dark = Color.Black.copy(alpha = 0.35f)
                            )
                            .drawBehind {
                                drawRoundRect(
                                    color = Color(0x2EFFFFFF),
                                    cornerRadius = CornerRadius(14.dp.toPx()),
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                                        )
                                    )
                                )
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                val newKey = onAddSnapshot("")
                                if (newKey != null) namingSlotKey = newKey
                            },
                        contentAlignment = Alignment.Center) {
                    Text("+", color = Color(0xFF8E99AD), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

        MixerModuleV2(modifier = Modifier.width(46.dp).fillMaxHeight()) {
            Box(Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleSnapshotArm() },
                contentAlignment = Alignment.Center) {
                IconSaveV2(size = 19.dp,
                    color = if (isSnapshotArmMode) Color(0xFFFF2A55) else Color.White)
            }
        }

        MixerModuleV2(modifier = Modifier.width(178.dp).fillMaxHeight()) {
            MasterSliderV2(masterTrack.volume, onMasterVolumeChange,
                masterTrack.peakMeterL, masterTrack.peakMeterR, onMasterFxClick)
        }

        // Layer case: slides in from the piano case towards the left when the
        // virtual keyboard appears, pushing the master slider with it.
        AnimatedVisibility(
            visible = isKeyboardActive,
            enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
        ) {
            MixerModuleV2(
                modifier = Modifier.width(48.dp).fillMaxHeight(),
                panelTop = if (isLayerExpanded) Color(0xFF3D5A82) else PanelTop,
                panelBottom = if (isLayerExpanded) Color(0xFF2A4062) else PanelBottom
            ) {
                Box(Modifier.fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleLayer() },
                    contentAlignment = Alignment.Center) {
                    IconLayerV2(size = 22.dp, color = if (isLayerExpanded) Accent else Color(0xFFC9D1E0))
                }
            }
        }

        MixerModuleV2(
            modifier = Modifier.width(48.dp).fillMaxHeight(),
            panelTop = if (isKeyboardActive) Color(0xFF3D5A82) else PanelTop,
            panelBottom = if (isKeyboardActive) Color(0xFF2A4062) else PanelBottom
        ) {
            Box(Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleKeyboard() },
                contentAlignment = Alignment.Center) {
                IconPianoV2(size = 22.dp, color = if (isKeyboardActive) Accent else Color(0xFFC9D1E0))
            }
        }
    }

    manageSlotKey?.let { key ->
        ManageSnapshotDialogV2(
            slotKey = key,
            currentName = snapshotCustomNames[key] ?: if (key == "slot_default") "Default" else "Snapshot",
            onDismiss = { manageSlotKey = null },
            onSave = { newName, _ -> onRenameSnapshotSlot(key, newName); manageSlotKey = null })
    }
    namingSlotKey?.let { key ->
        ManageSnapshotDialogV2(
            slotKey = key,
            currentName = snapshotCustomNames[key] ?: "Snapshot",
            onDismiss = { namingSlotKey = null },
            onSave = { newName, _ -> onRenameSnapshotSlot(key, newName); namingSlotKey = null })
    }
}

/**
 * Snapshot case, translated from the JSX `SnapCase` (MixerPreview.jsx:277).
 *
 * The number uses the same colour as the label (no dark "engraved" styling). The
 * case is given an explicit width by the caller, capped at the JSX max-width so a
 * single case does not stretch across the whole row.
 */
@Composable
private fun SnapshotCaseV2(
    key: String, displayName: String, fallbackNumber: Int?,
    isActive: Boolean, isDeletable: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    val animatedY by animateFloatAsState(dragY,
        animationSpec = if (dragY > 0f) tween(0) else spring(0.6f, 500f), label = "snapY")
    val isSwiping = dragY > 30f

    // A custom name replaces the SNAPSHOT/number stack; the raw default keeps it.
    val custom = displayName != "Default" && !displayName.matches(Regex("^Snapshot\\s+\\d+$"))

    val shape = RoundedCornerShape(14.dp)
    MixerModuleV2(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .offset { IntOffset(0, animatedY.roundToInt()) }
            .insetRelief(
                cornerRadius = 14.dp,
                light = Color.White.copy(alpha = if (isActive) 0.45f else 0.09f),
                dark = Color.Black.copy(alpha = 0.35f)
            )
            .pointerInput(key) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        if (delta > 0 && isDeletable) dragY = (dragY + delta).coerceAtMost(60f)
                    },
                    onDragEnd = { if (dragY > 30f && isDeletable) onDelete(); dragY = 0f },
                    onDragCancel = { dragY = 0f })
            }
            .pointerInput(key) {
                detectTapGestures(onTap = { if (!isSwiping) onClick() }, onLongPress = { onLongPress() })
            },
        shape = shape,
        panelTop = if (isActive) Color(0xFFA6C5F1) else PanelTop,
        panelBottom = if (isActive) Color(0xFF86AAE3) else PanelBottom,
        highlightAlpha = if (isActive) 0.45f else 0.09f
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (custom) {
                Text(displayName, color = if (isActive) Color(0xFF0F1726) else Color(0xFFDDE3EE),
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SNAPSHOT",
                        color = if (isActive) Color(0x8C0F1726) else Color(0x8CDDE3EE),
                        fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp, lineHeight = 9.sp)
                    Text(if (fallbackNumber == null) "Default" else "$fallbackNumber",
                        color = if (isActive) Color(0xFF0F1726) else Color(0xFFDDE3EE),
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 19.sp)
                }
            }
        }
    }
}

/**
 * Master slider, translated from the JSX `MasterSlider` (MixerPreview.jsx:301) and
 * merged with the mixer fader codes as asked: the rail takes the fader rail
 * treatment (recessed groove, tick scale, unity mark, peak hold) and the thumb
 * takes the fader cap treatment (gradient, top highlight, grip groove, side ridges).
 *
 * Premium touches on top of the reference codes:
 *  - the thumb lifts and grows slightly while it is being dragged (press state),
 *  - the filled part of the rail gets a soft glow around the thumb,
 *  - the value is animated with a spring so a tap slides instead of jumping.
 */
@Composable
private fun MasterSliderV2(
    value: Float, onValueChange: (Float) -> Unit, peakL: Float, peakR: Float, onFxClick: () -> Unit
) {
    val currentValue by rememberUpdatedState(value)
    var localPeak by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(peakL, peakR) {
        val target = value * maxOf(peakL, peakR)
        if (target > localPeak) localPeak = target
    }

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f), label = "thumbPress")
    // Animate the displayed position so taps glide instead of snapping.
    val shown by animateFloatAsState(value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f), label = "thumbPos")

    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("MASTER", color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(dbText(value),
                        color = if (value > 0.75f) Color(0xFFD8A24A) else Color(0xFFF1F4FA),
                        fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    Text(" dB", color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(3.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp)) {
                val w = maxWidth
                Box(Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            }
                        ) { o -> onValueChange((o.x / size.width.toFloat()).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { pressed = true },
                            onDragEnd = { pressed = false },
                            onDragCancel = { pressed = false }
                        ) { change, delta ->
                            change.consume()
                            onValueChange((currentValue + delta / size.width.toFloat()).coerceIn(0f, 1f))
                        }
                    })
                // Rail: 6 dp groove with an inset shadow, plus a light bottom lip so
                // the groove reads as carved into the panel (relief).
                Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                    val railH = 6.dp.toPx()
                    val top = (size.height - railH) / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF080C14), Color(0xFF141A26), Color(0xFF0E131C))
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, railH),
                        cornerRadius = CornerRadius(railH / 2f)
                    )
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(0f, top + 1.dp.toPx()),
                        size = Size(size.width, railH * 0.5f),
                        cornerRadius = CornerRadius(railH / 2f)
                    )
                    // Light bottom lip of the groove: 1 px of relief under the rail.
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.05f),
                        topLeft = Offset(railH / 2f, top + railH),
                        size = Size((size.width - railH).coerceAtLeast(0f), 1.dp.toPx()),
                        cornerRadius = CornerRadius(0.5.dp.toPx())
                    )
                    // Fader scale: faint ticks every 10 % of the travel.
                    for (i in 0..10) {
                        val tx = size.width * (i / 10f)
                        drawLine(
                            color = Color.White.copy(alpha = if (i % 5 == 0) 0.16f else 0.07f),
                            start = Offset(tx, top + railH + 2.dp.toPx()),
                            end = Offset(tx, top + railH + if (i % 5 == 0) 5.dp.toPx() else 4.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    // Filled portion, from the fader rail colour code.
                    val filled = size.width * shown
                    if (filled > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0x668FB2E8), Accent),
                                endX = filled.coerceAtLeast(1f)
                            ),
                            topLeft = Offset(0f, top),
                            size = Size(filled, railH),
                            cornerRadius = CornerRadius(railH / 2f)
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(1.dp.toPx(), top + 0.5.dp.toPx()),
                            size = Size((filled - 2.dp.toPx()).coerceAtLeast(0f), railH * 0.22f),
                            cornerRadius = CornerRadius(railH / 4f)
                        )
                        // Soft glow around the thumb, stronger while dragging.
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    Accent.copy(alpha = if (pressed) 0.34f else 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(filled, top + railH / 2f),
                                radius = railH * 3.2f
                            ),
                            radius = railH * 3.2f,
                            center = Offset(filled, top + railH / 2f)
                        )
                    }
                    // Unity mark at 0 dB (JSX: 0.75), plus peak hold.
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(size.width * 0.75f, top - 3.dp.toPx()),
                        end = Offset(size.width * 0.75f, top + railH + 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    if (localPeak > 0.01f) {
                        val px = (size.width * localPeak).coerceIn(0f, size.width - 2.dp.toPx())
                        drawRoundRect(
                            color = Color(0xFFA9C6F0),
                            topLeft = Offset(px, top - 2.dp.toPx()),
                            size = Size(2.dp.toPx(), railH + 4.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }
                }
                // Thumb: fader cap treatment (gradient + top highlight + grip groove).
                Canvas(Modifier.offset(x = (w * shown).coerceIn(0.dp, w) - 9.dp)
                    .size(18.dp, 20.dp)) {
                    withTransform({ scale(pressScale, pressScale, pivot = center) }) {
                        // Drop shadow under the cap.
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.5f),
                            topLeft = Offset(0f, 2.dp.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                        // Cap body: three-stop gradient for a domed look.
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF8A94B6), Color(0xFF5C6688), Color(0xFF3F4766))
                            ),
                            size = size,
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                        // Darker lower third, so the cap looks bevelled.
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.45f),
                            topLeft = Offset(0f, size.height * 0.72f),
                            size = Size(size.width, size.height * 0.28f),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                        // Outer rim highlight (1 px) to separate cap from panel.
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.14f),
                            size = size,
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(1.dp.toPx())
                        )
                        // Top highlight ridge.
                        drawLine(
                            color = Color.White.copy(alpha = 0.40f),
                            start = Offset(size.width * 0.22f, 1.5.dp.toPx()),
                            end = Offset(size.width * 0.78f, 1.5.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round
                        )
                        // Left edge light, right edge shade: rounded relief.
                        drawLine(
                            color = Color.White.copy(alpha = 0.18f),
                            start = Offset(1.2.dp.toPx(), size.height * 0.28f),
                            end = Offset(1.2.dp.toPx(), size.height * 0.78f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.Black.copy(alpha = 0.30f),
                            start = Offset(size.width - 1.2.dp.toPx(), size.height * 0.28f),
                            end = Offset(size.width - 1.2.dp.toPx(), size.height * 0.78f),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Grip groove, like the fader cap centre line.
                        drawLine(
                            color = Color.Black.copy(alpha = 0.5f),
                            start = Offset(size.width * 0.20f, size.height * 0.55f),
                            end = Offset(size.width * 0.80f, size.height * 0.55f),
                            strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Accent.copy(alpha = if (pressed) 1f else 0.9f),
                            start = Offset(size.width * 0.22f, size.height * 0.52f),
                            end = Offset(size.width * 0.78f, size.height * 0.52f),
                            strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        MixerModuleV2(modifier = Modifier.size(30.dp), shape = RoundedCornerShape(10.dp),
            panelTop = Color(0xFF3A4258), panelBottom = Color(0xFF2E3549), elevation = 2.dp) {
            Box(Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFxClick() },
                contentAlignment = Alignment.Center) {
                Text("FX", color = Color(0xFFD4DAE8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun dbOf(m: Float): Float = if (m <= 0.75f) {
    if (m <= 0.001f) Float.NEGATIVE_INFINITY else -60f + (m / 0.75f) * 60f
} else {
    ((m - 0.75f) / 0.25f) * 6f
}

private fun dbText(m: Float): String {
    val d = dbOf(m)
    if (d.isInfinite() && d < 0) return "−∞"
    val sign = if (d > 0f) "+" else if (d < 0f) "−" else ""
    return sign + String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(d))
}

/**
 * Live CPU / RAM readout. Values come from Process.getElapsedCpuTime() and the
 * JVM + native heaps, so the case shows the real load instead of a fixed number.
 *
 * Design: each line is a small LED with a live arc ring (the ring is the gauge, so
 * no full-width bar is needed), the label, then the value right next to it. The
 * label and the value sit 5 dp apart instead of being pushed to opposite edges,
 * which is what made the two readouts look so far apart.
 */
@Composable
private fun ResourceMonitorV2(width: androidx.compose.ui.unit.Dp) {
    val runtime = remember { Runtime.getRuntime() }
    var cpuPercent by remember { mutableIntStateOf(0) }
    var ramMb by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val cores = runtime.availableProcessors().coerceAtLeast(1)
        var prevCpu = android.os.Process.getElapsedCpuTime()
        var prevWall = System.currentTimeMillis()
        while (true) {
            val jvm = runtime.totalMemory() - runtime.freeMemory()
            val native = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L)
            ramMb = ((jvm + native) / (1024L * 1024L)).toInt().coerceAtLeast(1)

            val nowCpu = android.os.Process.getElapsedCpuTime()
            val nowWall = System.currentTimeMillis()
            val dCpu = nowCpu - prevCpu
            val dWall = nowWall - prevWall
            if (dWall > 0) {
                cpuPercent = ((dCpu.toFloat() / (dWall * cores)) * 100f).roundToInt().coerceIn(0, 99)
            }
            prevCpu = nowCpu
            prevWall = nowWall
            delay(1200)
        }
    }

    MixerModuleV2(modifier = Modifier.width(width).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
            ResourceGaugeV2("CPU", "$cpuPercent%", cpuPercent / 100f, Color(0xFF3CC48A))
            ResourceGaugeV2("RAM", "$ramMb", (ramMb / 512f).coerceIn(0f, 1f), Color(0xFF4BA8F0))
        }
    }
}

/**
 * One compact readout: an LED with a live arc ring, the label, then the value.
 * The ring is drawn with the real fraction, so the line stays alive without
 * needing a separate bar under it.
 */
@Composable
private fun ResourceGaugeV2(label: String, value: String, fraction: Float, tint: Color) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "gauge_$label")
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(11.dp)) {
            val stroke = 1.6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(color = Color(0x33FFFFFF), startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = tint, startAngle = -90f, sweepAngle = 360f * animated,
                useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawCircle(tint, radius = 1.6.dp.toPx(), center = center)
        }
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp)
        Text(value, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace)
    }
}
