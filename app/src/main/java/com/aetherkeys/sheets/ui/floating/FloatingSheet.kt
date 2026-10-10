package com.aetherkeys.sheets.ui.floating

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
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
import com.aetherkeys.sheets.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Feuille flottante : rectangle vertical sans contour, hauteur = contenu, une section à la fois.
 * - swipe gauche/droite : section suivante/précédente (une ligne n'est jamais tronquée)
 * - appui long puis glisser : déplacer, aimantation gauche/droite avec rebond
 * - swipe haut/bas : fermer (animation inverse vers la carte)
 * - double-tap : ouvrir dans l'éditeur
 * - l'extérieur reste manipulable (aucun fond bloquant) ; bottomInset = hauteur du mixer à laisser libre
 * - le morphing carte -> fenêtre est une animation propre (graphicsLayer), sans API expérimentale
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

    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var cardH by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(settings.lockOnOpen) }
    val progress = remember { Animatable(0f) }

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }) {
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val pad = with(density) { 22.dp.toPx() }
        val cardW = minOf(with(density) { SheetsLayout.FloatingWidth.toPx() }, containerW - 2 * margin)
        val maxCardH = containerH - with(density) { bottomInset.toPx() } - 2 * margin
        val xLeft = margin
        val xRight = containerW - cardW - margin
        val x = remember(containerW, cardW) { Animatable(xRight) }

        val plans = remember(sheet, settings, cardW, maxCardH) {
            ChartLayout.plan(
                sheet = sheet, settings = settings,
                availWidthPx = cardW - 2 * pad,
                availHeightPx = (maxCardH - 2 * pad - with(density) { 40.dp.toPx() }).coerceAtLeast(120f),
                pxPerSp = with(density) { 1.sp.toPx() },
                barPaddingPx = with(density) { 16.dp.toPx() },
                minSp = SheetsLayout.ChordMinSizeSp,
                measure100 = { text, fontId ->
                    textMeasurer.measure(text, TextStyle(fontFamily = SheetsFonts.family(fontId), fontSize = 100.sp), maxLines = 1, softWrap = false).size.width.toFloat()
                },
            )
        }
        val pageCount = plans.size
        if (pageIndex > pageCount - 1) pageIndex = (pageCount - 1).coerceAtLeast(0)

        LaunchedEffect(cardH > 0) {
            if (cardH > 0) progress.animateTo(1f, if (reduceMotion) tween(1) else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
        }

        fun closeAnimated() = scope.launch { progress.animateTo(0f, tween(if (reduceMotion) 1 else 240)); onClose() }

        fun go(delta: Int) {
            val n = (pageIndex + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (n != pageIndex) { pageIndex = n; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }

        val floatDelta = if (reduceMotion) 0f else rememberInfiniteTransition(label = "float").animateFloat(
            initialValue = -2.5f, targetValue = 2.5f, label = "floatY",
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        ).value

        val base = sheet.colorArgb.toColor()
        val ink = onColorFor(base)
        val shape = RoundedCornerShape(SheetsRadius.Floating)

        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), margin.roundToInt()) }
                .width(with(density) { cardW.toDp() })
                .graphicsLayer {
                    val p = progress.value
                    if (cardH == 0) { alpha = 0f } else if (originRect != null && !reduceMotion) {
                        val fl = rootPos.x + x.value
                        val ft = rootPos.y + margin
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = (originRect.left - fl) * (1f - p)
                        translationY = (originRect.top - ft) * (1f - p) + floatDelta * density.density * p
                        scaleX = lerp(originRect.width / cardW, 1f, p)
                        scaleY = lerp(originRect.height / cardH.toFloat(), 1f, p)
                    } else {
                        alpha = p
                        translationY = floatDelta * density.density
                    }
                }
                .shadow(24.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(listOf(base, lerpColor(base, Color.Black, 0.12f))))
                .onSizeChanged { cardH = it.height }
                .animateContentSize()
                .pointerInput(locked) { if (!locked) detectTapGestures(onDoubleTap = { onOpenEditor() }) }
                .pointerInput(pageCount, cardW, settings.swipeReversed, locked) {
                    if (locked) return@pointerInput
                    var dx = 0f; var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            if (abs(dx) > abs(dy)) {
                                if (abs(dx) > cardW * 0.22f) go((if (dx < 0) 1 else -1) * (if (settings.swipeReversed) -1 else 1))
                            } else if (abs(dy) > 80.dp.toPx()) closeAnimated()
                        },
                    ) { change, drag -> change.consume(); dx += drag.x; dy += drag.y }
                }
                .pointerInput(containerW, cardW, locked) {
                    if (locked) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDrag = { change, drag -> change.consume(); scope.launch { x.snapTo((x.value + drag.x).coerceIn(xLeft, xRight)) } },
                        onDragEnd = {
                            val target = if (x.value + cardW / 2f < containerW / 2f) xLeft else xRight
                            scope.launch { x.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
                        },
                    )
                },
        ) {
            // Verrou de scène : cadenas discret. Verrouillé = plus de swipe, de déplacement ni de fermeture.
            Text(
                if (locked) "\uD83D\uDD12" else "\uD83D\uDD13", style = TextStyle(fontSize = 12.sp), color = ink,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .graphicsLayer { alpha = if (locked) 0.85f else 0.3f }
                    .pointerInput(Unit) { detectTapGestures { locked = !locked; haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
            )
            Column(
                Modifier.padding(22.dp).graphicsLayer { alpha = ((progress.value - 0.25f) / 0.75f).coerceIn(0f, 1f) },
            ) {
                if (pageCount > 0) {
                    AnimatedContent(
                        targetState = pageIndex, label = "sections",
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally { w -> w * dir } + fadeIn()) togetherWith (slideOutHorizontally { w -> -w * dir } + fadeOut())
                        },
                    ) { idx ->
                        val plan = plans[idx.coerceIn(0, pageCount - 1)]
                        val section = sheet.sections[plan.sectionIndex]
                        Column {
                            SectionTagChip(
                                name = section.name + if (plan.parts > 1) " ${plan.part + 1}/${plan.parts}" else "",
                                kind = section.kind, colorOverride = section.colorArgb?.toColor(),
                            )
                            ChartLines(plan, sheet, settings, ink, selectedBarId = null, onBarClick = null, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
