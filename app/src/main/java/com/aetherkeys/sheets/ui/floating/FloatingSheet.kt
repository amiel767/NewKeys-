package com.aetherkeys.sheets.ui.floating

import android.provider.Settings
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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.aetherkeys.sheets.model.ChordText
import com.aetherkeys.sheets.model.Section
import com.aetherkeys.sheets.model.Sheet
import com.aetherkeys.sheets.model.SheetsSettings
import com.aetherkeys.sheets.ui.chart.ChartLayout
import com.aetherkeys.sheets.ui.chart.ChartLines
import com.aetherkeys.sheets.ui.components.SectionTagChip
import com.aetherkeys.sheets.ui.components.rememberPressRebound
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
    /** Edition rapide : la section relue au clavier systeme. Null = geste indisponible. */
    onQuickEdit: ((Section, String) -> Unit)? = null,
    /** Section quittee par glissement : le mixer suit la section affichee. */
    onSectionChange: ((Section) -> Unit)? = null,
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
    // Hauteur (pastille du bas) et largeur (pastille de gauche), reglables au doigt.
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    // Mode « remplir » : la pastille du bas allonge la fenetre et la section suivante
    // s'affiche tant qu'elle entre. Des qu'on choisit a la main, on obeit au choix.
    var fitSections by remember(sheet.id) { mutableStateOf(false) }
    // Edition rapide : la section en cours d'edition et son texte. Le clavier systeme
    // s'ouvre sur place ; l'editeur complet reste accessible au double tap.
    var quickSection by remember(sheet.id) { mutableStateOf<Section?>(null) }
    var quickText by remember(sheet.id) { mutableStateOf("") }
    val progress = remember { Animatable(0f) }

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }) {
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val pad = with(density) { 22.dp.toPx() }
        val baseW = with(density) { SheetsLayout.FloatingWidth.toPx() }
        val minCardW = with(density) { SheetsLayout.FloatingMinWidth.toPx() }
        val maxCardW = minOf(with(density) { SheetsLayout.FloatingMaxWidth.toPx() }, containerW - 2 * margin).coerceAtLeast(minCardW)
        if (cardWidthPx == 0f) cardWidthPx = baseW.coerceIn(minCardW, maxCardW)
        val cardW = cardWidthPx.coerceIn(minCardW, maxCardW)
        // Les polices suivent la largeur : elargir la fenetre agrandit les accords.
        val fontScale = (cardW / baseW).coerceIn(0.7f, 2f)
        val minX = margin
        val maxX = (containerW - cardW - margin).coerceAtLeast(minX)
        val minY = margin
        val maxY = (containerH - with(density) { bottomInset.toPx() } - 220f).coerceAtLeast(minY)
        val minCardH = with(density) { 150.dp.toPx() }
        val maxCardH = (containerH - with(density) { bottomInset.toPx() } - 2 * margin).coerceAtLeast(minCardH)
        if (cardHeightPx == 0f) cardHeightPx = with(density) { 300.dp.toPx() }

        // Position libre : x et y, chacun borné à l'écran. Plus d'aimantation.
        val x = remember(containerW, cardW) { Animatable(maxX) }
        val y = remember(containerH) { Animatable(minY) }

        // Un sheet « vu » a cette largeur : la taille des accords suit l'echelle.
        fun scaled(sec: Section): Sheet {
            val baseSize = sec.chordSizeSp ?: sheet.chordSizeSp
            return sheet.copy(
                chordSizeSp = (sheet.chordSizeSp * fontScale).coerceAtMost(SheetsLayout.ChordMaxSizeSp),
                sections = listOf(sec.copy(chordSizeSp = (baseSize * fontScale).coerceAtMost(SheetsLayout.ChordMaxSizeSp))),
            )
        }

        val measure100: (String, String) -> Float = { text, fontId ->
            textMeasurer.measure(text, TextStyle(fontFamily = SheetsFonts.family(fontId), fontSize = 100.sp), maxLines = 1, softWrap = false).size.width.toFloat()
        }

        // Hauteur naturelle de chaque section, sans pagination : c'est elle qui decide
        // combien de sections entrent dans la fenetre.
        val naturalHeights = remember(drawable, settings, cardW, fontScale) {
            val tagH = with(density) { 26.dp.toPx() }
            val gapH = with(density) { 16.dp.toPx() }
            drawable.map { sec ->
                val ps = ChartLayout.plan(
                    sheet = scaled(sec), settings = settings,
                    availWidthPx = cardW - 2 * pad,
                    availHeightPx = Float.MAX_VALUE,
                    pxPerSp = with(density) { 1.sp.toPx() },
                    barPaddingPx = with(density) { 16.dp.toPx() },
                    minSp = SheetsLayout.ChordMinSizeSp,
                    measure100 = measure100,
                )
                val lineH = (ps.firstOrNull()?.sizeSp ?: 0f) * with(density) { 1.sp.toPx() } * 1.35f
                ps.sumOf { it.lines.size } * lineH + tagH + gapH
            }
        }

        val contentH = cardHeightPx - 2 * pad
        val autoCount = remember(naturalHeights, contentH) {
            var acc = 0f; var n = 0
            for (h in naturalHeights) {
                if (n == 0 || acc + h <= contentH) { acc += h; n++ } else break
            }
            n
        }

        val shown = if (fitSections) {
            drawable.take(autoCount.coerceIn(1, drawable.size.coerceAtLeast(1)))
        } else {
            drawable.filter { it.id in shownIds }.ifEmpty { listOfNotNull(drawable.firstOrNull()) }
        }

        val plans = remember(sheet, settings, shown, cardW, maxCardH, fontScale) {
            shown.flatMap { sec ->
                val idx = sheet.sections.indexOf(sec)
                ChartLayout.plan(
                    sheet = scaled(sec), settings = settings,
                    availWidthPx = cardW - 2 * pad,
                    availHeightPx = (maxCardH - 2 * pad - with(density) { 60.dp.toPx() }).coerceAtLeast(120f),
                    pxPerSp = with(density) { 1.sp.toPx() },
                    barPaddingPx = with(density) { 16.dp.toPx() },
                    minSp = SheetsLayout.ChordMinSizeSp,
                    measure100 = measure100,
                ).map { it.copy(sectionIndex = idx) }
            }
        }

        LaunchedEffect(cardW, minX, maxX) { x.snapTo(x.value.coerceIn(minX, maxX)) }

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
                .height(with(density) { cardHeightPx.toDp() })
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
                .testTag("floating-card")
                .onSizeChanged { cardH = it.height }
                // Un seul geste par intention, dans cet ordre de priorite :
                //   appui long  -> edition rapide (clavier systeme, sur place)
                //   double tap  -> editeur complet
                //   tap         -> ouvre/ferme la liste des sections
                //   glisse      -> deplace la fenetre (simple toucher puis glisser)
                .pointerInput(locked, drawable) {
                    if (locked) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            pickerOpen = !pickerOpen
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDoubleTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenEditor() },
                        onLongPress = {
                            val sec = shown.firstOrNull() ?: return@detectTapGestures
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            quickSection = sec
                            quickText = ChordText.sectionLine(sec, sec.keyPc ?: sheet.keyPc, settings.notation)
                        },
                    )
                }
                // Glissement horizontal : change de section. La fenetre ne bouge pas
                // pendant ce geste, c'est ce qui le distingue du deplacement.
                .pointerInput(locked, drawable, shownIds) {
                    if (locked) return@pointerInput
                    detectHorizontalDragGestures { _, drag ->
                        val i = drawable.indexOfFirst { it.id == shownIds.firstOrNull() }
                        if (drag < -60f) {
                            val next = drawable.getOrNull(i + 1)
                            if (next != null) {
                                fitSections = false
                                shownIds = listOf(next.id)
                                onSectionChange?.invoke(next)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else if (drag > 60f) {
                            val prev = drawable.getOrNull(i - 1)
                            if (prev != null) {
                                fitSections = false
                                shownIds = listOf(prev.id)
                                onSectionChange?.invoke(prev)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
                // Deplacement : n'importe quel glissement vertical, ou un glissement
                // horizontal quand il n'y a qu'une section (rien a faire glisser).
                .pointerInput(containerW, cardW, minX, maxX, minY, maxY, locked, drawable.size) {
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
            // Barre de commandes : seul FERMER reste en haut a droite. Le verrou descend
            // en bas a gauche, la liste des sections vit DANS la carte.
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CloseButton(ink) { closeAnimated() }
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

            // Verrou, en bas a gauche (hors de la zone de la poignee centrale).
            Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                SceneIconButton(if (locked) SheetsIcons.Lock else SheetsIcons.Unlock, if (locked) "Unlock scene" else "Lock scene", ink, active = locked) {
                    locked = !locked
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            // Pastille fine du bas : collee au bord, quasi invisible. La cible tactile
            // reste large (24 dp), seul le trait est discret.
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(width = 84.dp, height = 24.dp)
                    .testTag("resize-grip")
                    .pointerInput(locked, minCardH, maxCardH) {
                        if (locked) return@pointerInput
                        detectDragGestures(
                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrag = { change, drag ->
                                change.consume()
                                fitSections = true
                                cardHeightPx = (cardHeightPx + drag.y).coerceIn(minCardH, maxCardH)
                            },
                        )
                    },
                contentAlignment = Alignment.BottomCenter,
            ) { Box(Modifier.width(38.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.18f))) }

            // Pastille fine de gauche : collee au bord, quasi invisible. Meme cible large.
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 2.dp).size(width = 24.dp, height = 92.dp)
                    .testTag("resize-grip-left")
                    .pointerInput(locked, minCardW, maxCardW, minX, maxX) {
                        if (locked) return@pointerInput
                        detectDragGestures(
                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrag = { change, drag ->
                                change.consume()
                                cardWidthPx = (cardWidthPx + drag.x).coerceIn(minCardW, maxCardW)
                            },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) { Box(Modifier.width(3.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.18f))) }

            // Pastille de section : ouvre la liste interne. Un simple tap sur le chart l'ouvre aussi.
            Box(
                Modifier.align(Alignment.BottomEnd).padding(14.dp).testTag("section-strip").clip(CircleShape)
                    .background(ink.copy(alpha = 0.15f))
                    .clickable { pickerOpen = !pickerOpen; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        if (shown.size == 1) shown.first().name else "${shown.size} sections",
                        style = SheetsType.Label.copy(fontSize = 11.sp), color = ink,
                    )
                    Icon(SheetsIcons.Layers, contentDescription = "Choose sections", tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
                }
            }

            quickSection?.let { sec ->
                QuickEditPanel(
                    section = sec,
                    text = quickText,
                    ink = ink,
                    onText = { quickText = it },
                    onDone = {
                        // On relit la ligne telle qu'elle est ecrite : c'est le meme
                        // langage que celui affiche, donc rien a expliquer a l'utilisateur.
                        onQuickEdit?.invoke(sec, quickText)
                        quickSection = null
                    },
                    onCancel = { quickSection = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (pickerOpen) {
                InCardSectionPicker(
                    sections = drawable,
                    shownIds = shownIds,
                    ink = ink,
                    onToggle = { id ->
                        fitSections = false
                        shownIds = if (id in shownIds) {
                            (shownIds - id).ifEmpty { listOf(id) }
                        } else shownIds + id
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDismiss = { pickerOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Liste des sections DANS la carte (et non un popover externe) : elle recouvre la carte
 * d'un voile teinte de son encre, avec un mode « une » ou « plusieurs ».
 */
@Composable
private fun InCardSectionPicker(
    sections: List<com.aetherkeys.sheets.model.Section>,
    shownIds: List<String>,
    ink: Color,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val veil = if (isLightInk(ink)) Color(0xFFFFFDF6) else Color(0xFF0A0D13)
    Box(modifier.testTag("section-picker").clip(RoundedCornerShape(SheetsRadius.Floating)).background(veil.copy(alpha = 0.72f)).clickable(onClick = onDismiss)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text("SECTIONS", style = SheetsType.Overline, color = ink.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                sections.forEach { sec ->
                    val on = sec.id in shownIds
                    val (press, source) = rememberPressRebound(0.97f)
                    Row(
                        Modifier.fillMaxWidth().then(press).clip(RoundedCornerShape(11.dp))
                            .background(ink.copy(alpha = if (on) 0.16f else 0.05f))
                            .clickable(interactionSource = source, indication = null) { onToggle(sec.id) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(sec.colorArgb?.toColor() ?: sec.kind.color()))
                        Text(sec.name, style = SheetsType.Body.copy(fontSize = 13.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = ink.copy(alpha = if (on) 1f else 0.72f), modifier = Modifier.weight(1f))
                        if (on) Icon(SheetsIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** L'encre claire signifie un fond sombre : le voile doit alors eclaircir. */
private fun isLightInk(ink: Color): Boolean = (ink.red * 0.2126f + ink.green * 0.7152f + ink.blue * 0.0722f) < 0.5f

/**
 * Fermeture : le X est volontairement fondu pour ne pas attirer l'oeil pendant qu'on
 * joue. La cible tactile reste large (40 dp) pour rester attrapable.
 */
@Composable
private fun CloseButton(ink: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(SheetsIcons.Close, contentDescription = "Close scene", tint = ink.copy(alpha = 0.45f), modifier = Modifier.size(17.dp))
    }
}

/**
 * Edition rapide : on relit la ligne d'accords telle qu'elle est affichee, au clavier
 * systeme, sans quitter la scene. Le langage est celui du chart (`1 4 | 5 6m | %`),
 * donc rien de nouveau a apprendre. L'editeur complet reste au double tap.
 */
@Composable
private fun QuickEditPanel(
    section: com.aetherkeys.sheets.model.Section,
    text: String,
    ink: Color,
    onText: (String) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(section.id) { focus.requestFocus() }
    Box(
        modifier.clip(RoundedCornerShape(SheetsRadius.Floating))
            .background(ink.copy(alpha = 0.14f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = 0.12f))
                .border(1.dp, ink.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(section.name, style = SheetsType.Overline, color = ink.copy(alpha = 0.75f))
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = SheetsType.chord(SheetsFonts.ArchitectsDaughter, 18f).copy(color = ink),
                cursorBrush = SolidColor(ink),
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Text("Barres separees par |   ·   - = case vide   ·   % = reprise", style = SheetsType.Caption, color = ink.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickEditAction("Annuler", ink, onCancel)
                Spacer(Modifier.weight(1f))
                QuickEditAction("Valider", ink, onDone, strong = true)
            }
        }
    }
}

@Composable
private fun QuickEditAction(label: String, ink: Color, onClick: () -> Unit, strong: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(ink.copy(alpha = if (strong) 0.28f else 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(label, style = SheetsType.Label, color = ink) }
}

@Composable
private fun SceneIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    ink: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val (press, source) = rememberPressRebound(0.90f)
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(30.dp).then(press).clip(RoundedCornerShape(9.dp))
            .background(ink.copy(alpha = if (active) 0.26f else 0.14f))
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ink, modifier = Modifier.size(16.dp))
    }
}
