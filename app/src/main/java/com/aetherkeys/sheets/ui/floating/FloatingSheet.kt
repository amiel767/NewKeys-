package com.aetherkeys.sheets.ui.floating

import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.aetherkeys.sheets.model.Sheet
import com.aetherkeys.sheets.model.SheetsSettings
import com.aetherkeys.sheets.ui.chart.ChartLayout
import com.aetherkeys.sheets.ui.chart.ChartLines
import com.aetherkeys.sheets.ui.components.SectionTagChip
import com.aetherkeys.sheets.ui.components.SheetsIcons
import com.aetherkeys.sheets.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Feuille flottante : la carte du sheet posée SUR le mixer. Rectangle vertical sans
 * contour, à la couleur de la carte, déplaçable n'importe où (aucune aimantation).
 *
 * - appui long puis glisser : déplacer librement, sans contrainte d'axe
 * - bouton calques : choisir UNE section ou PLUSIEURS (Verse, Chorus, Bridge…)
 * - double-tap : ouvrir dans l'éditeur
 * - bouton fermer : refermer (le glissement ne ferme plus, il déplace)
 * - l'extérieur reste manipulable ; bottomInset = hauteur du mixer à laisser libre
 * - morphing carte -> fenêtre par graphicsLayer, sans API expérimentale
 */
@Composable
fun FloatingSheetHost(
    sheet: Sheet,
    settings: SheetsSettings,
    originRect: Rect?,
    bottomInset: Dp,
    onOpenEditor: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val ctx = LocalContext.current
    val reduceMotion = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }

    val drawable = remember(sheet) { sheet.sections.filter { it.bars.isNotEmpty() } }

    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var cardH by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(settings.lockOnOpen) }
    // Sections affichées, par identifiant : vide = la première section.
    var shownIds by remember(sheet.id) { mutableStateOf(listOf(drawable.firstOrNull()?.id ?: "")) }
    var pickerOpen by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }) {
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val pad = with(density) { 22.dp.toPx() }
        val cardW = minOf(with(density) { SheetsLayout.FloatingWidth.toPx() }, containerW - 2 * margin)
        val maxCardH = (containerH - with(density) { bottomInset.toPx() } - 2 * margin).coerceAtLeast(120f)
        val minX = margin
        val maxX = (containerW - cardW - margin).coerceAtLeast(minX)
        val minY = margin
        val maxY = (containerH - with(density) { bottomInset.toPx() } - 220f).coerceAtLeast(minY)

        // Position libre : x et y, chacun borné à l'écran. Plus d'aimantation.
        val x = remember(containerW, cardW) { Animatable(maxX) }
        val y = remember(containerH) { Animatable(minY) }

        val shown = drawable.filter { it.id in shownIds }.ifEmpty { listOfNotNull(drawable.firstOrNull()) }

        val plans = remember(sheet, settings, shown, cardW, maxCardH) {
            shown.flatMap { sec ->
                val idx = sheet.sections.indexOf(sec)
                ChartLayout.plan(
                    sheet = sheet.copy(sections = listOf(sec)), settings = settings,
                    availWidthPx = cardW - 2 * pad,
                    availHeightPx = (maxCardH - 2 * pad - with(density) { 60.dp.toPx() }).coerceAtLeast(120f),
                    pxPerSp = with(density) { 1.sp.toPx() },
                    barPaddingPx = with(density) { 16.dp.toPx() },
                    minSp = SheetsLayout.ChordMinSizeSp,
                    measure100 = { text, fontId ->
                        textMeasurer.measure(text, TextStyle(fontFamily = SheetsFonts.family(fontId), fontSize = 100.sp), maxLines = 1, softWrap = false).size.width.toFloat()
                    },
                ).map { it.copy(sectionIndex = idx) }
            }
        }

        LaunchedEffect(cardH > 0) {
            if (cardH > 0) progress.animateTo(1f, if (reduceMotion) tween(1) else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
        }

        fun closeAnimated() = scope.launch { progress.animateTo(0f, tween(if (reduceMotion) 1 else 240)); onClose() }

        val floatDelta = if (reduceMotion) 0f else rememberInfiniteTransition(label = "float").animateFloat(
            initialValue = -2.5f, targetValue = 2.5f, label = "floatY",
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        ).value

        val base = sheet.colorArgb.toColor()
        val ink = onColorFor(base)
        val shape = RoundedCornerShape(SheetsRadius.Floating)

        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .width(with(density) { cardW.toDp() })
                .graphicsLayer {
                    val p = progress.value
                    if (cardH == 0) alpha = 0f else if (originRect != null && !reduceMotion) {
                        val fl = rootPos.x + x.value
                        val ft = rootPos.y + y.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = (originRect.left - fl) * (1f - p)
                        translationY = (originRect.top - ft) * (1f - p) + floatDelta * density.density * p
                        scaleX = lerp(originRect.width / cardW, 1f, p)
                        scaleY = lerp(originRect.height / cardH.toFloat(), 1f, p)
                    } else {
                        // Sans rectangle d'origine il n'y a rien a morpher : la carte est
                        // deja a sa place, l'assombrir attendrait une animation sans objet.
                        alpha = 1f
                        translationY = floatDelta * density.density
                    }
                }
                .shadow(24.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(listOf(base, lerpColor(base, Color.Black, 0.12f))))
                .onSizeChanged { cardH = it.height }
                .animateContentSize()
                .pointerInput(locked) { if (!locked) detectTapGestures(onDoubleTap = { onOpenEditor() }) }
                .pointerInput(containerW, cardW, minX, maxX, minY, maxY, locked) {
                    if (locked) return@pointerInput
                    detectDragGestures(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDrag = { change, drag ->
                            change.consume()
                            scope.launch {
                                x.snapTo((x.value + drag.x).coerceIn(minX, maxX))
                                y.snapTo((y.value + drag.y).coerceIn(minY, maxY))
                            }
                        },
                    )
                },
        ) {
            // Barre de commandes : calques (sections), verrou, fermer.
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SceneIconButton(SheetsIcons.Layers, "Choose sections", ink, active = pickerOpen) {
                    pickerOpen = !pickerOpen
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                SceneIconButton(if (locked) SheetsIcons.Lock else SheetsIcons.Unlock, if (locked) "Unlock scene" else "Lock scene", ink, active = locked) {
                    locked = !locked
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                SceneIconButton(SheetsIcons.Close, "Close scene", ink, active = false) { closeAnimated() }
            }
            Column(Modifier.padding(22.dp).graphicsLayer { alpha = ((progress.value - 0.25f) / 0.75f).coerceIn(0f, 1f) }) {
                shown.forEachIndexed { i, section ->
                    val sectionPlans = plans.filter { it.sectionIndex == sheet.sections.indexOf(section) }
                    if (sectionPlans.isNotEmpty()) {
                        if (i > 0) Spacer(Modifier.height(16.dp))
                        SectionTagChip(
                            name = section.name + if (sectionPlans.size > 1) " 1/${sectionPlans.size}" else "",
                            kind = section.kind, colorOverride = section.colorArgb?.toColor(),
                        )
                        sectionPlans.forEach { plan ->
                            ChartLines(plan, sheet, settings, ink, selectedBarId = null, onBarClick = null, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }

            if (pickerOpen) {
                SectionPicker(
                    sections = drawable,
                    shownIds = shownIds,
                    ink = ink,
                    onToggle = { id ->
                        shownIds = if (id in shownIds) {
                            (shownIds - id).ifEmpty { listOf(id) }
                        } else shownIds + id
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SceneIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    ink: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
            .background(ink.copy(alpha = if (active) 0.26f else 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ink, modifier = Modifier.size(16.dp))
    }
}

/** Choix des sections affichées : une ou plusieurs, dans l'ordre de la feuille. */
@Composable
private fun SectionPicker(
    sections: List<com.aetherkeys.sheets.model.Section>,
    shownIds: List<String>,
    ink: Color,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(230.dp).clip(RoundedCornerShape(14.dp))
            .background(SheetsColors.SurfaceRaised)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("SECTIONS SHOWN", style = SheetsType.Overline, color = SheetsColors.TextMuted, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        sections.forEach { sec ->
            val on = sec.id in shownIds
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                    .background(if (on) SheetsColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onToggle(sec.id) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(sec.colorArgb?.toColor() ?: sec.kind.color()))
                Text(
                    sec.name, style = SheetsType.Body.copy(fontSize = 13.sp),
                    color = if (on) SheetsColors.TextPrimary else SheetsColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
