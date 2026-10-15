@file:Suppress("unused")

package com.soundstage.mixer.ui.patches

/*
 * ÆTHER KEYS — PATCHES OVERLAY (drawer right + create sheet)
 *
 * Scope: this file contains ONLY the Patches overlay UI and its pure models.
 * The patch storage, the snapshot engine, and the ViewModel logic stay in the app
 * and are wired through [PatchesActions].
 *
 * Design language is identical to SetlistsScreen (drawer shape, panel gradient,
 * row surfaces, staggered entry, sheet layout).
 * All user-facing text is English.
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════════════════
// 1. MODELS
// ════════════════════════════════════════════════════════════════════════════════════════

data class Patch(
    val id: String,
    val name: String,
    val snapshotCount: Int,
)

data class PatchesUiState(
    val patches: List<Patch> = emptyList(),
    val activePatchId: String? = null,
    val isLoading: Boolean = false,
)

interface PatchesActions {
    fun onClose() {}
    fun onSelectPatch(id: String) {}
    fun onToggleActive(id: String) {}
    fun onRenamePatch(id: String) {}
    fun onDeletePatch(id: String) {}
    fun onCreatePatch(name: String, duplicateFrom: String?) {}
}

object NoOpPatchesActions : PatchesActions

// ════════════════════════════════════════════════════════════════════════════════════════
// 2. STYLE
// ════════════════════════════════════════════════════════════════════════════════════════

internal object PatchColors {
    val PanelTop = Color(0xFF232C3E)
    val PanelBottom = Color(0xFF1A2230)
    val Ink = Color(0xFFE9EDF5)
    val Soft = Color(0xFFDCE2EE)
    val Mute = Color(0xFF8690A4)
    val Dim = Color(0xFF6F7A90)
    val Label = Color(0xFF7B859A)
    val Accent = Color(0xFF8FB2E8)
    val AccentDeep = Color(0xFF7FA3DD)
    val AccentText = Color(0xFF0F1726)
    val Icon = Color(0xFF9AA5BA)
    val IconMute = Color(0xFF7B859A)
    val Backdrop = Color(0x9E060910)
    val BackdropSheet = Color(0xB3060914)
    val Well = Color(0xFF0F141D)
    val Title = Color(0xFFF4F6FB)
    val TitleSoft = Color(0xFFF1F4FA)
}

private fun white(a: Float) = Color.White.copy(alpha = a)

private fun patchText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = PatchColors.Ink,
) = TextStyle(fontSize = size, fontWeight = weight, color = color, fontFeatureSettings = "tnum")

private class PatchHaptics(private val h: HapticFeedback, private val on: Boolean) {
    fun tick() { if (on) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 3. MODIFIER HELPERS
// ════════════════════════════════════════════════════════════════════════════════════════

private val DrawerShape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)

private fun Modifier.drawerPanel(): Modifier = this
    .shadow(
        14.dp, DrawerShape,
        ambientColor = Color.Black.copy(alpha = 0.30f),
        spotColor = Color.Black.copy(alpha = 0.30f),
    )
    .clip(DrawerShape)
    .background(Brush.verticalGradient(listOf(PatchColors.PanelTop, PatchColors.PanelBottom)))
    .border(1.dp, white(0.10f), DrawerShape)

private fun Modifier.sheetPanel(shape: Shape): Modifier = this
    .shadow(
        14.dp, shape,
        ambientColor = Color.Black.copy(alpha = 0.30f),
        spotColor = Color.Black.copy(alpha = 0.30f),
    )
    .clip(shape)
    .background(Brush.verticalGradient(listOf(PatchColors.PanelTop, PatchColors.PanelBottom)))
    .border(1.dp, white(0.10f), shape)

@Composable
private fun Modifier.patchTap(scaleDown: Float = 0.96f, onTap: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) scaleDown else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "patchTap",
    )
    val tap by rememberUpdatedState(onTap)
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { tap() },
            )
        }
}

@Composable
private fun Modifier.patchRowRise(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay((index * 45L).coerceAtMost(500L))
            p.animateTo(1f, tween(380, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)))
        }
    }
    return this.graphicsLayer {
        alpha = p.value
        translationX = (1f - p.value) * 14.dp.toPx()
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 4. OVERLAY
// ════════════════════════════════════════════════════════════════════════════════════════

private val DrawerWidth = 420.dp
private val SheetWidth = 460.dp

@Composable
fun PatchesOverlay(
    state: PatchesUiState,
    actions: PatchesActions,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val haptics = remember(haptic) { PatchHaptics(haptic, true) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var sheetOpen by remember { mutableStateOf(false) }
    var snackName by remember { mutableStateOf<String?>(null) }

    val p by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(380, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)),
        label = "drawerSlide",
    )

    val density = LocalDensity.current
    val drawerWidthPx = with(density) { DrawerWidth.toPx() }

    Box(modifier.fillMaxSize()) {
        // Backdrop
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p }
                .background(PatchColors.Backdrop)
                .pointerInput(Unit) { detectTapGestures { actions.onClose() } },
        )

        // Drawer
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(DrawerWidth)
                .graphicsLayer { translationX = (1f - p) * drawerWidthPx }
                .drawerPanel()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* absorb taps in empty areas */ },
        ) {
            Column(Modifier.fillMaxSize()) {
                DrawerHeader(state = state, haptics = haptics, onClose = { actions.onClose() })

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(white(0.07f)),
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.isLoading) {
                        PatchSkeletonList()
                    } else if (state.patches.isEmpty()) {
                        PatchEmptyState()
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            itemsIndexed(
                                state.patches,
                                key = { _, p -> p.id },
                            ) { i, patch ->
                                val active = patch.id == state.activePatchId
                                PatchRow(
                                    patch = patch,
                                    index = i,
                                    active = active,
                                    onSelect = { haptics.tick(); actions.onSelectPatch(patch.id) },
                                    onToggleActive = { haptics.tick(); actions.onToggleActive(patch.id) },
                                    onRename = { haptics.tick(); actions.onRenamePatch(patch.id) },
                                    onDelete = {
                                        snackName = patch.name
                                        actions.onDeletePatch(patch.id)
                                    },
                                )
                            }
                        }
                    }
                }

                DrawerFooter(haptics = haptics, onNew = { sheetOpen = true })
            }

            snackName?.let { name ->
                LaunchedEffect(name) {
                    delay(5000)
                    snackName = null
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(10f),
                ) {
                    UndoSnack(
                        name = name,
                        onUndo = {
                            actions.onCreatePatch(name, null)
                            snackName = null
                        },
                    )
                }
            }
        }

        if (sheetOpen) {
            val openPatch = state.patches.firstOrNull { it.id == state.activePatchId }
            CreatePatchSheet(
                openPatch = openPatch,
                nextIndex = state.patches.size + 1,
                onClose = { sheetOpen = false },
                onCreate = { name, duplicateFrom ->
                    haptics.tick()
                    sheetOpen = false
                    actions.onCreatePatch(name, duplicateFrom)
                },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.1 DRAWER HEADER
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawerHeader(
    state: PatchesUiState,
    haptics: PatchHaptics,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Patches",
                style = patchText(20.sp, FontWeight.ExtraBold, PatchColors.Title),
                maxLines = 1,
            )
            Text(
                "${state.patches.size} ${if (state.patches.size == 1) "patch" else "patches"}",
                style = patchText(11.sp, color = PatchColors.Dim),
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier
                .height(30.dp)
                .patchTap(0.94f) { }
                .clip(RoundedCornerShape(15.dp))
                .background(white(0.06f))
                .border(1.dp, white(0.08f), RoundedCornerShape(15.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PatchIcon(PatchIconKind.Stack, PatchColors.Icon, Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Setlists",
                style = patchText(12.sp, FontWeight.Bold, PatchColors.Icon),
            )
        }
        Box(
            Modifier
                .size(30.dp)
                .patchTap(0.90f) { haptics.tick(); onClose() }
                .clip(CircleShape)
                .background(white(0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            PatchIcon(PatchIconKind.Close, PatchColors.Icon, Modifier.size(15.dp))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.2 LIST — EMPTY / SKELETON
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PatchEmptyState() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No patches yet.", style = patchText(13.sp, color = PatchColors.Dim))
        Spacer(Modifier.height(4.dp))
        Text("Tap New Patch to start.", style = patchText(13.sp, color = PatchColors.Dim))
    }
}

@Composable
private fun PatchSkeletonList() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.verticalGradient(listOf(white(0.06f), white(0.03f)))),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.3 DRAWER FOOTER
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawerFooter(haptics: PatchHaptics, onNew: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
    ) {
        val shape = RoundedCornerShape(13.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .patchTap(0.97f) { haptics.tick(); onNew() }
                .shadow(
                    4.dp, shape,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.30f),
                )
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF9DBDEB), PatchColors.AccentDeep)))
                .border(1.dp, white(0.20f), shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PatchIcon(PatchIconKind.Plus, PatchColors.AccentText, Modifier.size(16.dp), stroke = 2.6.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "New Patch",
                style = patchText(14.sp, FontWeight.ExtraBold, PatchColors.AccentText),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.4 ROW
// ────────────────────────────────────────────────────────────────────────────────────────

    @Composable
    private fun PatchRow(
        patch: Patch,
        index: Int,
        active: Boolean,
        onSelect: () -> Unit,
        onToggleActive: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit,
    ) {
        val shape = RoundedCornerShape(13.dp)
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(0f) }
        var armed by remember { mutableStateOf(false) }
        var widthPx by remember { mutableIntStateOf(0) }
        val gapPx = with(density) { 8.dp.toPx() }
        val pillW = max(0f, -offset.value - gapPx)

        val borderColor by animateColorAsState(
            if (active) PatchColors.Accent.copy(alpha = 0.45f) else white(0.09f),
            tween(250),
            label = "rowBorder",
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .patchRowRise(index)
                .onSizeChanged { widthPx = it.width },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(with(density) { pillW.toDp() })
                    .alpha(min(1f, pillW / with(density) { 40.dp.toPx() }))
                    .clip(shape)
                    .background(
                        if (armed) Brush.verticalGradient(
                            listOf(Color(0xFFB8465F), Color(0xFFA03D57))
                        ) else Brush.verticalGradient(
                            listOf(Color(0xFF7A2E45), Color(0xFF5E2436))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (pillW > with(density) { 34.dp.toPx() }) {
                    val rot by animateFloatAsState(
                        if (armed) -18f else 0f, spring(0.5f, 500f), label = "rot"
                    )
                    val sc by animateFloatAsState(
                        if (armed) 1.18f else 1f, spring(0.5f, 500f), label = "sc"
                    )
                    PatchIcon(
                        PatchIconKind.Trash,
                        Color.White,
                        Modifier
                            .size(19.dp)
                            .graphicsLayer { rotationZ = rot; scaleX = sc; scaleY = sc },
                    )
                }
            }

            Row(
                Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var total = 0f
                        val resist = 22.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                scope.launch {
                                    if (armed) {
                                        offset.animateTo(-widthPx.toFloat(), tween(180))
                                        onDelete()
                                    } else {
                                        offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                    }
                                    armed = false
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                }
                                armed = false
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                total += dx
                                val d = min(0f, total)
                                val eff = if (d > -resist) d else -resist + (d + resist) * 0.72f
                                scope.launch { offset.snapTo(eff) }
                                val threshold = min(size.width * 0.36f, 200.dp.toPx())
                                val a = eff <= -threshold
                                if (a != armed) armed = a
                            },
                        )
                    }
                    .patchTap(0.985f, onSelect)
                    .shadow(
                        3.dp, shape,
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.30f),
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            if (active) listOf(white(0.11f), white(0.06f))
                            else listOf(white(0.07f), white(0.035f))
                        )
                    )
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .patchTap(0.90f, onToggleActive)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) PatchColors.Accent else white(0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PatchIcon(
                        kind = if (active) PatchIconKind.Pause else PatchIconKind.Play,
                        color = if (active) PatchColors.AccentText else PatchColors.Icon,
                        modifier = Modifier.size(13.dp),
                        fill = true,
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        patch.name,
                        style = patchText(15.sp, FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${patch.snapshotCount} ${if (patch.snapshotCount == 1) "Snapshot" else "Snapshots"}",
                        style = patchText(11.5.sp, color = PatchColors.Mute),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(PatchIconKind.Edit, "Rename", onRename)
                    if (active) {
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(PatchColors.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            PatchIcon(
                                PatchIconKind.Check,
                                PatchColors.AccentText,
                                Modifier.size(13.dp),
                                stroke = 3.dp,
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun IconAction(kind: PatchIconKind, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .patchTap(0.90f, onClick)
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        PatchIcon(kind, PatchColors.IconMute, Modifier.size(15.dp))
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 5. CREATE SHEET
// ════════════════════════════════════════════════════════════════════════════════════════

private enum class CreateMode { Blank, Duplicate }

@Composable
private fun CreatePatchSheet(
    openPatch: Patch?,
    nextIndex: Int,
    onClose: () -> Unit,
    onCreate: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("Patch $nextIndex") }
    var mode by remember { mutableStateOf(CreateMode.Blank) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val valid = name.trim().isNotEmpty() &&
        (mode == CreateMode.Blank || openPatch != null)

    fun submit() {
        if (!valid) return
        val source = if (mode == CreateMode.Duplicate) openPatch?.id else null
        onCreate(name.trim(), source)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(PatchColors.BackdropSheet)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(SheetWidth)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { /* absorb clicks inside the sheet */ }
                    .sheetPanel(RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SheetHeader(onClose = onClose)

                NameField(
                    name = name,
                    onNameChange = { if (it.length <= 40) name = it },
                    focus = focus,
                    onDone = { submit() },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PatchTile(
                        modifier = Modifier.weight(1f),
                        icon = PatchIconKind.Copy,
                        title = "Duplicate",
                        sub = if (openPatch != null) "Copy of ${openPatch.name}" else "No patch open",
                        selected = mode == CreateMode.Duplicate,
                        enabled = openPatch != null,
                        onClick = { mode = CreateMode.Duplicate },
                    )
                    PatchTile(
                        modifier = Modifier.weight(1f),
                        icon = PatchIconKind.Plus,
                        title = "Blank",
                        sub = "Start with no snapshots",
                        selected = mode == CreateMode.Blank,
                        enabled = true,
                        onClick = { mode = CreateMode.Blank },
                    )
                }

                SheetActions(
                    valid = valid,
                    onCancel = onClose,
                    onCreate = { submit() },
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "New patch",
            style = patchText(18.sp, FontWeight.ExtraBold, PatchColors.TitleSoft),
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(30.dp)
                .patchTap(0.90f, onClose)
                .clip(CircleShape)
                .background(white(0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            PatchIcon(PatchIconKind.Close, PatchColors.Icon, Modifier.size(15.dp))
        }
    }
}

@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
    focus: FocusRequester,
    onDone: () -> Unit,
) {
    Column {
        Row {
            Text(
                "NAME",
                style = TextStyle(
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = PatchColors.Label,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${name.length}/40",
                style = patchText(10.sp, FontWeight.Bold, PatchColors.Label),
            )
        }
        Spacer(Modifier.height(6.dp))
        val fieldShape = RoundedCornerShape(12.dp)
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = patchText(15.sp, FontWeight.SemiBold, PatchColors.TitleSoft),
            cursorBrush = SolidColor(PatchColors.Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusRequester(focus)
                .clip(fieldShape)
                .background(PatchColors.Well)
                .border(1.dp, PatchColors.Accent.copy(alpha = 0.40f), fieldShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SheetActions(
    valid: Boolean,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
    ) {
        Box(
            Modifier
                .height(38.dp)
                .patchTap(0.96f, onCancel)
                .clip(RoundedCornerShape(12.dp))
                .background(white(0.04f))
                .border(1.dp, white(0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cancel", style = patchText(14.sp, FontWeight.Bold, Color(0xFFC7CFDD)))
        }

        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .height(38.dp)
                .alpha(if (valid) 1f else 0.50f)
                .patchTap(0.96f) { if (valid) onCreate() }
                .clip(shape)
                .background(
                    if (valid) Brush.verticalGradient(listOf(Color(0xFF9DBDEB), PatchColors.AccentDeep))
                    else SolidColor(PatchColors.Accent.copy(alpha = 0.20f))
                )
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Create",
                style = patchText(
                    14.sp,
                    FontWeight.ExtraBold,
                    if (valid) PatchColors.AccentText else white(0.35f),
                ),
            )
        }
    }
}

@Composable
private fun PatchTile(
    modifier: Modifier,
    icon: PatchIconKind,
    title: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val border by animateColorAsState(
        if (selected) PatchColors.Accent.copy(alpha = 0.65f) else white(0.08f),
        tween(200),
        label = "tileBorder",
    )
    val iconBg by animateColorAsState(
        if (selected) PatchColors.Accent else white(0.07f),
        tween(200),
        label = "tileIcon",
    )

    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .patchTap(0.97f) { if (enabled) onClick() }
            .clip(shape)
            .background(if (selected) white(0.08f) else white(0.04f))
            .border(1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            PatchIcon(
                icon,
                if (selected) PatchColors.AccentText else PatchColors.Icon,
                Modifier.size(17.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = patchText(15.sp, FontWeight.Bold), maxLines = 1)
            Text(
                sub,
                style = patchText(11.5.sp, color = PatchColors.Mute),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) PatchColors.Accent else Color.Transparent)
                .border(1.5.dp, if (selected) Color.Transparent else white(0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                PatchIcon(
                    PatchIconKind.Check,
                    PatchColors.AccentText,
                    Modifier.size(12.dp),
                    stroke = 3.dp,
                )
            }
        }
    }
}

@Composable
private fun UndoSnack(name: String, onUndo: () -> Unit) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            0f,
            tween(5000, easing = androidx.compose.animation.core.LinearEasing),
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(
                    12.dp, shape,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.30f),
                )
                .clip(shape)
                .background(Color(0xF5222C40))
                .border(1.dp, white(0.10f), shape),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    "\u201C$name\u201D deleted",
                    style = patchText(13.5.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "UNDO",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = PatchColors.Accent,
                    ),
                    modifier = Modifier
                        .patchTap(0.92f, onUndo)
                        .padding(4.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.value)
                    .height(3.dp)
                    .background(PatchColors.Accent),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 6. ICONS
// ════════════════════════════════════════════════════════════════════════════════════════

private enum class PatchIconKind { Close, Plus, Edit, Trash, Check, Copy, Stack, Play, Pause }

@Composable
private fun PatchIcon(
    kind: PatchIconKind,
    color: Color,
    modifier: Modifier,
    stroke: Dp = 2.dp,
    fill: Boolean = false,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = stroke.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), sw, StrokeCap.Round)

        when (kind) {
            PatchIconKind.Close -> {
                line(0.15f, 0.15f, 0.85f, 0.85f)
                line(0.85f, 0.15f, 0.15f, 0.85f)
            }
            PatchIconKind.Plus -> {
                line(0.5f, 0.1f, 0.5f, 0.9f)
                line(0.1f, 0.5f, 0.9f, 0.5f)
            }
            PatchIconKind.Check -> {
                line(0.2f, 0.52f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.82f, 0.28f)
            }
            PatchIconKind.Edit -> {
                val p = Path().apply {
                    moveTo(w * 0.15f, h * 0.85f)
                    lineTo(w * 0.15f, h * 0.65f)
                    lineTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.35f)
                    lineTo(w * 0.35f, h * 0.85f)
                    close()
                }
                drawPath(p, color, style = st)
                line(0.68f, 0.18f, 0.82f, 0.32f)
            }
            PatchIconKind.Trash -> {
                line(0.15f, 0.25f, 0.85f, 0.25f)
                line(0.38f, 0.25f, 0.38f, 0.10f)
                line(0.38f, 0.10f, 0.62f, 0.10f)
                line(0.62f, 0.10f, 0.62f, 0.25f)
                val p = Path().apply {
                    moveTo(w * 0.25f, h * 0.25f)
                    lineTo(w * 0.30f, h * 0.90f)
                    lineTo(w * 0.70f, h * 0.90f)
                    lineTo(w * 0.75f, h * 0.25f)
                }
                drawPath(p, color, style = st)
                line(0.42f, 0.42f, 0.42f, 0.74f)
                line(0.58f, 0.42f, 0.58f, 0.74f)
            }
            PatchIconKind.Copy -> {
                drawRoundRect(
                    color,
                    Offset(w * 0.34f, h * 0.34f),
                    Size(w * 0.56f, h * 0.56f),
                    CornerRadius(w * 0.10f),
                    style = st,
                )
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.66f)
                    lineTo(w * 0.18f, h * 0.66f)
                    lineTo(w * 0.10f, h * 0.58f)
                    lineTo(w * 0.10f, h * 0.18f)
                    lineTo(w * 0.18f, h * 0.10f)
                    lineTo(w * 0.58f, h * 0.10f)
                    lineTo(w * 0.66f, h * 0.18f)
                }
                drawPath(p, color, style = st)
            }
            PatchIconKind.Stack -> {
                drawRoundRect(
                    color, Offset(w * 0.17f, h * 0.25f),
                    Size(w * 0.66f, h * 0.12f), CornerRadius(w * 0.06f),
                    style = st,
                )
                drawRoundRect(
                    color, Offset(w * 0.17f, h * 0.44f),
                    Size(w * 0.66f, h * 0.12f), CornerRadius(w * 0.06f),
                    style = st,
                )
                drawRoundRect(
                    color, Offset(w * 0.17f, h * 0.63f),
                    Size(w * 0.66f, h * 0.12f), CornerRadius(w * 0.06f),
                    style = st,
                )
            }
            PatchIconKind.Play -> {
                if (fill) {
                    val p = Path().apply {
                        moveTo(w * 0.30f, h * 0.20f)
                        lineTo(w * 0.82f, h * 0.50f)
                        lineTo(w * 0.30f, h * 0.80f)
                        close()
                    }
                    drawPath(p, color)
                }
            }
            PatchIconKind.Pause -> {
                if (fill) {
                    drawRoundRect(
                        color,
                        Offset(w * 0.28f, h * 0.22f),
                        Size(w * 0.15f, h * 0.56f),
                        CornerRadius(w * 0.05f),
                    )
                    drawRoundRect(
                        color,
                        Offset(w * 0.57f, h * 0.22f),
                        Size(w * 0.15f, h * 0.56f),
                        CornerRadius(w * 0.05f),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 7. PREVIEW
// ════════════════════════════════════════════════════════════════════════════════════════

@Preview(widthDp = 891, heightDp = 411, showBackground = true, backgroundColor = 0xFF141B27)
@Composable
private fun PatchesOverlayPreview() {
    var state by remember {
        mutableStateOf(
            PatchesUiState(
                patches = listOf(
                    Patch("p1", "Patch 1", 1),
                    Patch("p2", "Patch 2", 3),
                    Patch("p3", "Ambient Pad", 5),
                ),
                activePatchId = "p1",
            )
        )
    }
    val actions = remember {
        object : PatchesActions {
            override fun onSelectPatch(id: String) { state = state.copy(activePatchId = id) }
            override fun onToggleActive(id: String) {
                state = state.copy(activePatchId = if (state.activePatchId == id) null else id)
            }
            override fun onDeletePatch(id: String) {
                state = state.copy(patches = state.patches.filterNot { it.id == id })
            }
            override fun onCreatePatch(name: String, duplicateFrom: String?) {
                val base = state.patches.firstOrNull { it.id == duplicateFrom }
                state = state.copy(
                    patches = listOf(
                        Patch("p" + System.nanoTime(), name, base?.snapshotCount ?: 0)
                    ) + state.patches,
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141B27), Color(0xFF0F141D))))
    ) {
        PatchesOverlay(state = state, actions = actions)
    }
}
