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
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.soundstage.mixer.model.SubSceneSnapshot
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.components.FaderRail
import com.soundstage.mixer.ui.components.FaderThumb
import com.soundstage.mixer.ui.components.FloppyDiskIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PanelTop = Color(0xFF2B3348)
private val PanelBottom = Color(0xFF1D2433)
private val TextMain = Color(0xFFE9EDF5)
private val Accent = Color(0xFF8FB2E8)

@Composable
fun MixerBottomBarV2(
    masterTrack: TrackChannel,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterFxClick: () -> Unit,
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

    Row(modifier.height(46.dp), // +15% as asked (40 -> 46)
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {

        ResourceMonitorV2(94.dp)

        // The BPM case was removed from the bottom bar as asked: tempo is still
        // reachable from the drum pad and the top bar, and the freed room goes to
        // the snapshot cases so they read longer.

        // Snapshot cases share the free room, but each is capped at the JSX
        // max-width (Default 150, numbered 124). `weight(1f)` cannot be used here:
        // it hands the child an exact width and would ignore the cap, which is what
        // made a lone case stretch across the whole row.
        BoxWithConstraints(Modifier.weight(1f)) {
            val count = configuredSnapshotSlots.size + if (configuredSnapshotSlots.size < 5) 1 else 0
            // 4 dp between cases instead of the preview's 6 dp: the user reads the
            // 6 dp gutters as a large gap, and the tighter spacing gives every case a
            // little more length.
            // The width is frozen to the size a case has with 4 cases, as asked:
            // adding or removing a snapshot no longer resizes the others, the row
            // simply gains or loses a case. Only past 4 cases does the divisor grow,
            // so a 5th case shrinks the row instead of overflowing it.
            val divisor = maxOf(count, 4)
            val gapTotal = 4.dp * (divisor - 1).coerceAtLeast(0)
            val share = if (divisor > 0) (maxWidth - gapTotal) / divisor else 0.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                configuredSnapshotSlots.forEachIndexed { idx, key ->
                    val defaultLabel = if (key == "slot_default") "Default" else "Snapshot ${idx}"
                    val displayName = snapshotCustomNames[key] ?: defaultLabel
                    val isActive = activeSnapshotSlot == key || (activeSnapshotSlot == null && key == "slot_default")
                    val cap = if (key == "slot_default") 150.dp else 124.dp
                    // Never let a case collapse below a readable width: at 5 cases the
                    // shared room can be smaller than the text needs, and a 40 dp sliver
                    // is worse than a slightly narrower neighbour.
                    val width = minOf(cap, share).coerceAtLeast(56.dp)
                    SnapshotCaseV2(
                        key = key,
                        displayName = displayName,
                        fallbackNumber = if (key == "slot_default") null else idx,
                        isActive = isActive,
                        isDeletable = key != "slot_default",
                        isArming = isSnapshotArmMode,
                        width = width,
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
                    Text("+", color = Color(0xFF8E99AD), fontSize = 14.4.sp, fontWeight = FontWeight.ExtraBold)
                }

                // The floppy sits right after the last case, inside the weighted row:
                // it reads as "save into a snapshot" and no longer ends up glued to the
                // master slider. The free space of the row is left after it.
                FloppyCaseV2(
                    isArmed = isSnapshotArmMode,
                    onToggleArm = onToggleSnapshotArm
                )
            }
        }
    }

        // With the BPM case gone the bar has room again, so the master is back to
        // its full 178 dp and the snapshot cases keep the rest.
        val masterWidth = 178.dp
        MixerModuleV2(modifier = Modifier.width(masterWidth).fillMaxHeight()) {
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
    /** Armed: a tap writes the live mixer into this case instead of recalling it. */
    isArming: Boolean = false,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    val animatedY by animateFloatAsState(dragY,
        animationSpec = if (dragY > 0f) tween(0) else spring(0.6f, 500f), label = "snapY")
    val isSwiping = dragY > 30f
    val (bounce, pulse) = rememberPressBounce()

    // Default is the app's own slot: it shows only its name, never the
    // SNAPSHOT/number stack (the stack is for the user's numbered snapshots).
    val isDefaultSlot = key == "slot_default"
    val custom = isDefaultSlot || (displayName != "Default" && !displayName.matches(Regex("^Snapshot\\s+\\d+$")))

    val shape = RoundedCornerShape(14.dp)
    MixerModuleV2(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .graphicsLayer { scaleX = bounce; scaleY = bounce }
            .offset { IntOffset(0, animatedY.roundToInt()) }
            // Armed: a soft halo bleeds out of the case so the row reads as "tap a
            // case to record into it". Same falloff family as the strip aura.
            .recordingHalo(
                color = if (isArming) Color(0xFFFF2A55) else Color.Transparent,
                cornerRadius = 14.dp,
                intensity = if (isArming) 0.62f else 0f
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
                detectTapGestures(onTap = { if (!isSwiping) { pulse(); onClick() } }, onLongPress = { onLongPress() })
            },
        shape = shape,
        panelTop = if (isActive) Color(0xFFA6C5F1) else PanelTop,
        panelBottom = if (isActive) Color(0xFF86AAE3) else PanelBottom,
        highlightAlpha = if (isActive) 0.45f else 0.09f
    ) {
        Box(Modifier.fillMaxSize()) {
            // JSX SnapCase relief: inset top highlight only; the drop shadow comes
            // from MixerModuleV2. The old hand-rolled relief doubled the border and
            // read as a flat outline instead of a raised case.
            Box(
                Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = if (isActive) 0.45f else 0.09f))
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (custom) {
                Text(displayName, color = if (isActive) Color(0xFF0F1726) else Color(0xFFDDE3EE),
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SNAPSHOT",
                        color = if (isActive) Color(0x8C0F1726) else Color(0x8CDDE3EE),
                        fontSize = 6.8.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp, lineHeight = 9.sp)
                    Text(if (fallbackNumber == null) "Default" else "$fallbackNumber",
                        color = if (isActive) Color(0xFF0F1726) else Color(0xFFDDE3EE),
                        fontSize = 14.4.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 15.sp)
                }
            }
            }
        }
    }
}

/**
 * Floppy save case, sitting right after the last snapshot case.
 *
 * It used to live after the master slider, where it read as an appendage of the
 * master module. It is the arm/disarm control for snapshot recording: while armed
 * the disk turns red, a LED lights up and a soft halo bleeds out of the case, and
 * every snapshot case lights its own halo so the row reads as "tap a case to save".
 */
@Composable
private fun FloppyCaseV2(
    isArmed: Boolean,
    onToggleArm: () -> Unit
) {
    val (bounce, pulse) = rememberPressBounce()
    // Slow breathing LED while armed, so the state reads at a glance without
    // flashing. `infiniteRepeatable` on a plain float keeps it to one animation.
    val breathe by animateFloatAsState(
        targetValue = if (isArmed) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "floppyArm"
    )

    MixerModuleV2(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .graphicsLayer { scaleX = bounce; scaleY = bounce }
            .recordingHalo(
                color = if (isArmed) Color(0xFFFF2A55) else Color.Transparent,
                cornerRadius = 14.dp,
                intensity = breathe
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                pulse(); onToggleArm()
            },
        panelTop = if (isArmed) Color(0xFF3E2430) else PanelTop,
        panelBottom = if (isArmed) Color(0xFF28161E) else PanelBottom,
        highlightAlpha = if (isArmed) 0.16f else 0.09f
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // LED: a small lit dot above the disk while armed. Drawn rather than
            // shadowed, because the offscreen capture does not render platform shadows.
            Canvas(Modifier.fillMaxSize()) {
                if (breathe <= 0.01f) return@Canvas
                val cx = size.width / 2f
                val cy = size.height * 0.17f
                // Tight glow: it must read as a small lit LED, not wash the case.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF6B84).copy(alpha = 0.40f * breathe),
                            Color(0xFFFF2A55).copy(alpha = 0.14f * breathe),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = size.minDimension * 0.17f
                    ),
                    radius = size.minDimension * 0.17f,
                    center = Offset(cx, cy)
                )
                drawCircle(Color(0xFFFF9AAC).copy(alpha = 0.92f * breathe), radius = 1.6.dp.toPx(), center = Offset(cx, cy))
            }
            FloppyDiskIcon(isArmed = isArmed, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * A fine lit rim inside a case, fading inward over a few dp.
 *
 * This used to be a wide radial bloom painted 12 % outside the case, so it spilled
 * onto the neighbours and, because a radial gradient ignores the rounded shape, it
 * filled the whole rectangle instead of tracing the border. It now draws concentric
 * rounded-rect strokes strictly inside the bounds: the glow follows the corners and
 * nothing escapes the case.
 *
 * Painted with `drawBehind` rather than `Modifier.shadow`, which is drawn over
 * translucent fills and is not rendered by the offscreen capture.
 */
fun Modifier.recordingHalo(
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    intensity: Float = 1f
): Modifier = this.drawWithContent {
    // `drawWithContent`, not `drawBehind`: the case paints an opaque panel gradient
    // after this modifier, so a rim drawn behind it is completely hidden. The strip
    // aura solves the same problem by drawing inside the already-filled box.
    drawContent()
    if (intensity <= 0.01f) return@drawWithContent
    val a = intensity.coerceIn(0f, 1f)
    val r = cornerRadius.toPx()
    val depth = 3.2.dp.toPx()
    val layers = 7
    val step = depth / layers
    // Each ring sits `step` further in and dimmer than the one outside it, so the
    // falloff reads as a lit edge rather than a fill.
    for (i in 0 until layers) {
        val inset = step * (i + 0.5f)
        val falloff = 1f - i / layers.toFloat()
        val alpha = falloff * falloff * 0.42f * a
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)),
            style = Stroke(width = step)
        )
    }
}

/**
 * Press bounce shared by the snapshot cases and the floppy case: the surface dips
 * then springs back, so a tap reads as a physical press.
 */
@Composable
fun rememberPressBounce(): Pair<Float, () -> Unit> {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 1100f),
        label = "pressBounce"
    )
    // The case dips, then the spring overshoots back past 1f. Releasing shortly after
    // keeps the bounce visible without leaving the case shrunk.
    val pulse: () -> Unit = {
        pressed = true
        scope.launch { delay(80); pressed = false }
    }
    return scale to pulse
}

/**
 * Measures the child with width and height swapped, reports the swapped size, and
 * centres the child. Combined with a 90° rotation this turns a vertical composable
 * (the fader rail) into a horizontal one without the parent clamping its height:
 * a plain `graphicsLayer(rotationZ)` only rotates the pixels, it does not change
 * the measured size, so a vertical rail inside a short horizontal slot collapses
 * to a square. Swapping the constraints is what keeps the rail at full length.
 */
fun Modifier.swapAxes(): Modifier = this.layout { measurable, constraints ->
    val swapped = Constraints(
        minWidth = constraints.minHeight,
        maxWidth = constraints.maxHeight,
        minHeight = constraints.minWidth,
        maxHeight = constraints.maxWidth,
    )
    val placeable = measurable.measure(swapped)
    layout(placeable.height, placeable.width) {
        // Centre the (unrotated) child inside the reported box; after the 90°
        // rotation about the box centre it lands exactly on the box.
        placeable.place(
            ((placeable.height - placeable.width) / 2f).toInt(),
            ((placeable.width - placeable.height) / 2f).toInt(),
        )
    }
}

/**
 * Master fader, rebuilt from the mixer's own fader parts as asked: the rail is
 * [FaderRail] and the cap is [FaderThumb] — the exact composables the track strips
 * use — rotated to run horizontally. The old version was a JSX re-translation drawn
 * with one-shot Canvas code, which is why it never matched the strips.
 *
 * Layout rules from the request:
 *  - no "MASTER" label and no permanent dB readout: the case is just the fader,
 *  - the dB value appears only while the fader is being touched, as a small pop-up
 *    above the cap, then fades away,
 *  - the rail is [FaderRail] at the strips' 18 dp thickness,
 *  - the cap is [FaderThumb] at the strips' 32x40 aspect, turned on its side.
 */
@Composable
private fun MasterSliderV2(
    value: Float, onValueChange: (Float) -> Unit, peakL: Float, peakR: Float, onFxClick: () -> Unit
) {
    val currentValue by rememberUpdatedState(value)
    var dragging by remember { mutableStateOf(false) }
    // Show the value only while the finger is down, plus a short tail so the number
    // stays readable for a beat after release.
    var showValue by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (dragging) showValue = true
        else { delay(900); showValue = false }
    }

    val shown by animateFloatAsState(value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f), label = "masterPos")

    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        // Fader case: same panel treatment as the strip faders, no extra chrome. It
        // takes the full bar height (46 dp) so the 40 dp cap is not clipped by the
        // case's rounded clip, which a shorter case would do.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom))),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp + 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                val travel = maxWidth
                // Same numbers as the strip faders (CustomVerticalFader defaults):
                // rail 18 dp, cap 32x40.
                val railThickness = 18.dp
                // Cap reduced 25% (32x40 -> 24x30) then turned on its side: the final
                // footprint is horizontal (30 wide x 24 tall) so the thumb lies along
                // the rail instead of standing upright.
                val capLong = 30.dp
                val capShort = 24.dp

                // Gesture layer: the full case height, so the touch target is the
                // whole band and not just the 18 dp rail.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                dragging = true
                                tryAwaitRelease()
                                dragging = false
                            }) { o -> onValueChange((o.x / size.width.toFloat()).coerceIn(0f, 1f)) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false }
                            ) { change, delta ->
                                change.consume()
                                onValueChange((currentValue + delta / travel.value).coerceIn(0f, 1f))
                            }
                        },
                )

                // Rail: the strip's own vertical rail, rotated clockwise 90° so its
                // "bottom" (where the level fills from) lands on the left.
                Box(
                    Modifier
                        .size(width = travel, height = railThickness)
                        .swapAxes()
                        .graphicsLayer(rotationZ = 90f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
                ) {
                    FaderRail(
                        accent = Accent,
                        modifier = Modifier.fillMaxSize(),
                        level = shown,
                        glow = if (dragging) 0.85f else 0.35f,
                    )
                }

                // Cap on top of the rail, positioned along the travel. The cap is the
                // strips' portrait FaderThumb, rotated 90° so it lies horizontally,
                // then reduced 25%. swapAxes() gives it a horizontal footprint
                // (capLong x capShort) before the rotation so nothing is clipped.
                // The offset keeps the cap's *whole* width inside the rail: centring it
                // on the position pushed half the cap past the right end and under the
                // FX case. Sliding from 0 to (travel - capLong) makes it stop flush.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = ((travel - capLong) * shown).coerceIn(0.dp, travel - capLong))
                        .size(capLong, capShort),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(capLong, capShort)
                            .swapAxes()
                            .graphicsLayer(rotationZ = 90f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
                    ) {
                        FaderThumb(
                            accent = Accent,
                            modifier = Modifier.fillMaxSize(),
                            glow = if (dragging) 1f else 0.6f,
                        )
                    }
                }
            }

            // FX entry point, kept on the right of the case (as before) so opening the
            // FX window stays reachable without a separate bar.
            MixerModuleV2(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(30.dp),
                shape = RoundedCornerShape(10.dp),
                panelTop = Color(0xFF3A4258), panelBottom = Color(0xFF2E3549), elevation = 2.dp,
            ) {
                Box(Modifier.fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFxClick() },
                    contentAlignment = Alignment.Center) {
                    Text("FX", color = Color(0xFFD4DAE8), fontSize = 8.8.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // dB pop-up: only while touched. It must escape the fader case — the case is
        // 46 dp tall and clipped, so a chip *inside* it was trapped under the cap.
        // A real Popup draws in its own window above everything, so the value sits
        // over the case instead of being cropped by it.
        if (showValue) {
            val popupOffsetPx = with(LocalDensity.current) { 26.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -popupOffsetPx),
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE60A0E16))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${dbText(value)} dB",
                        color = if (value > 0.75f) Color(0xFFD8A24A) else Color(0xFFF1F4FA),
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                    )
                }
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
        // JSX ResourceMonitor: column, justifyContent center, gap 4 (MixerPreview.jsx:454).
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
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
        Canvas(Modifier.size(10.dp)) {
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
        Text(label, color = tint, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp, lineHeight = 12.sp)
        Text(value, color = TextMain, fontSize = 8.8.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
    }
}
