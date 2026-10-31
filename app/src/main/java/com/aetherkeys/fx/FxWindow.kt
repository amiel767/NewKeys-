package com.aetherkeys.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La fenêtre FX, détachée de l'application.
 *
 * ELLE NE LIT RIEN DU MIXER. Tout ce dont elle a besoin lui est passé par
 * [FxWindowState], et tout ce qu'elle fait remonte par [FxWindowActions]. C'est
 * ce qui permet de la sortir de l'app sans rien casser : elle ne connaît ni
 * `MixerViewModel`, ni `AppColors`, ni les couches du mixer.
 *
 * LA COMPOSITION EST CELLE DE LA MAQUETTE : quatre emplacements dans la rangée,
 * trois Audio puis un MIDI. La maquette en dessine cinq en vitrine ; l'app n'a
 * que Space et l'Arpeggiator, donc le catalogue ne propose que ces deux-là.
 *
 * La taille est celle de la maquette, 891 × 411 unités. Elle ne s'étend jamais
 * sur tout l'écran : [scale] ne dépasse pas 1.
 */
@Composable
fun FxWindow(
    state: FxWindowState,
    actions: FxWindowActions,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val inEffect = state.openEffect != null
    val title = when {
        state.openEffect != null -> state.openEffect.name
        state.catalogOpen -> "Add effect"
        else -> "Effects"
    }
    val count = state.slots.count { it != null }
    // L'état de l'arpégiateur vit ICI, pas dans sa page : l'en-tête porte BPM et
    // Swing, la page porte la grille. Deux composants, un seul état.
    val arp = remember { ArpState() }
    val isArp = state.openEffect?.kind == FxKindD.MIDI
    // Le pas courant avance tout seul tant que la page est ouverte : le curseur
    // de la maquette tourne, il ne reste pas sur le premier point.
    LaunchedEffect(isArp) {
        if (!isArp) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(220)
            arp.cur = (arp.cur + 1) % arp.steps
        }
    }

    FxStage(modifier = modifier, scale = scale) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(FxGeo.RADIUS.dp))
                .background(Brush.verticalGradient(listOf(FxC.winTop, FxC.winBot))),
        ) {
            Column(Modifier.fillMaxSize()) {
                FxHeader(
                    title = title,
                    big = inEffect,
                    onBack = if (inEffect || state.catalogOpen) ({ actions.closePage() }) else null,
                    counter = if (!inEffect && !state.catalogOpen) "$count / ${FxDesign.SLOTS}" else null,
                    trailing = {
                        if (inEffect && state.openEffect?.space == true) {
                            FxSaveButton(custom = state.custom) { actions.beginSave() }
                        }
                        // BPM et Swing sont MONTEES DANS L'EN-TETE, comme la
                        // maquette : ce sont des reglages du transport, pas de la
                        // grille. Les mettre dans la page les aurait noyes.
                        if (isArp) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width(1.dp).height(24.dp).background(FxC.line))
                            Spacer(Modifier.width(8.dp))
                            FxNumStep("BPM", arp.bpm, 40, 240, 32) { arp.bpm = it }
                            Spacer(Modifier.width(8.dp))
                            FxNumStep("Swing", arp.swing, 0, 80, 36, "%") { arp.swing = it }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width(1.dp).height(24.dp).background(FxC.line))
                        }
                    },
                    onClose = { actions.onClose() },
                )
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    when {
                        state.catalogOpen -> FxCatalog(state, actions)
                        state.openEffect != null -> FxEffectPage(state, actions, arp)
                        else -> FxRack(state, actions)
                    }
                }
            }
        }
    }
}

/**
 * La rangée des quatre emplacements : trois Audio, puis un MIDI.
 *
 * L'ordre est celui de la maquette. Le MIDI est en DERNIER parce que c'est sa
 * place dans la référence, et parce qu'un effet MIDI n'est pas un effet audio :
 * les mêler dans la rangée ferait croire qu'on peut en ajouter quatre.
 */
@Composable
private fun FxRack(state: FxWindowState, actions: FxWindowActions) {
    Row(Modifier.fillMaxSize()) {
        for (i in 0 until FxDesign.SLOTS) {
            if (i > 0) Spacer(Modifier.width(FxGeo.GAP.dp))
            val slot = state.slots.getOrNull(i)
            if (slot != null) {
                FxCard(
                    info = slot.info,
                    presetName = slot.presetName,
                    vals = slot.values,
                    palette = slot.palette,
                    on = slot.on,
                    onToggle = { actions.toggle(i) },
                    onOpen = { actions.open(slot.info.id) },
                    onRemove = { actions.remove(i) },
                )
            } else {
                val midi = i >= FxDesign.MAX_AUDIO
                FxEmptySlot(if (midi) "MIDI" else "Audio") {
                    actions.openCatalog(if (midi) FxKindD.MIDI else FxKindD.AUDIO)
                }
            }
        }
    }
}

/** Emplacement libre : le cadre en pointillé et le `+` centré. */
@Composable
fun FxEmptySlot(label: String, onAdd: () -> Unit) {
    Box(
        Modifier.width(FxGeo.CARD_W.dp).fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickableNoRipple(onAdd)
            .fxSvg(AeFxSvg.emptySlot()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                    .fxSvg(AeFxSvg.dashedBox()),
                contentAlignment = Alignment.Center,
            ) { FxIcon(AeFxIcons.PLUS, 18.dp, FxC.mute, sw = 2.4f) }
            Spacer(Modifier.height(10.dp))
            Text(
                label.uppercase(), color = Color(0xFF454E66),
                fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
            )
        }
    }
}

/**
 * Le catalogue : les familles en puces, une recherche, et les effets en DEUX
 * colonnes. La maquette pose deux colonnes de cartes de 68 unités — une liste
 * d'une seule colonne laisserait la moitié de la fenêtre vide.
 */
@Composable
private fun FxCatalog(state: FxWindowState, actions: FxWindowActions) {
    val list = state.catalog ?: return
    val kind = if (list.any { it.kind == FxKindD.MIDI }) FxKindD.MIDI else FxKindD.AUDIO
    val families = remember(list) { listOf("All") + list.map { it.family }.distinct() }
    var fam = remember { androidx.compose.runtime.mutableStateOf("All") }
    val shown = list.filter { fam.value == "All" || it.family == fam.value }
    val full = state.slots.count { it != null } >= FxDesign.SLOTS

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            families.forEach { f ->
                val on = fam.value == f
                Box(
                    Modifier.height(26.dp).clip(RoundedCornerShape(9.dp))
                        .then(
                            if (on) Modifier.background(Brush.verticalGradient(listOf(FxC.in1, FxC.in2)))
                            else Modifier.background(Color(0x0AFFFFFF))
                        )
                        .clickableNoRipple { fam.value = f }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        f, color = if (on) FxC.inText else Color(0xFF8791B5),
                        fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(shown) { f ->
                FxCatalogCard(f, enabled = !full) { actions.add(f.id) }
            }
        }
    }
}

/** Une carte du catalogue : la pastille d'icône, le nom, le type, et le `+`. */
@Composable
private fun FxCatalogCard(f: FxInfoD, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(13.dp))
            .fxRectWell(13.dp, top = .06f, rim = .07f)
            .clickableNoRipple { if (enabled) onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(13.dp))
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.radialGradient(
                        listOf(FxC.acc.copy(alpha = .30f), Color(0xFF141A28)),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) { FxIcon(iconFor(f), 17.dp, FxC.ink, sw = 1.9f) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(f.name, color = FxC.ink, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Text(
                    f.type.uppercase(), color = FxC.acc, fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, maxLines = 1,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${f.type} · ${f.family}", color = Color(0xFF7C869C), fontSize = 10.sp, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(FxC.in1, FxC.in2))),
            contentAlignment = Alignment.Center,
        ) { FxIcon(AeFxIcons.PLUS, 13.dp, FxC.inText, sw = 2.4f) }
        Spacer(Modifier.width(13.dp))
    }
}

/** L'icône d'un effet du catalogue. */
private fun iconFor(f: FxInfoD): String = when (f.type) {
    "Reverb" -> AeFxIcons.WAVE
    "Arp" -> AeFxIcons.ARP_UP
    "Delay" -> AeFxIcons.SLIDERS
    else -> AeFxIcons.SLIDERS
}

/**
 * En-tête : le repère ou le retour, le titre, le compteur, puis les actions.
 *
 * LE TITRE D'UNE PAGE EST EN CINZEL ET POSÉ DE TRAVERS — 1,2° de rotation et
 * 3 unités de descente. Bien aligné il se lit comme un libellé d'interface ;
 * décalé il se lit comme une enseigne. C'est le détail de la maquette.
 */
@Composable
fun FxHeader(
    title: String,
    big: Boolean,
    onBack: (() -> Unit)?,
    counter: String? = null,
    onClose: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(FxGeo.HEAD.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            FxIconButton(AeFxIcons.BACK, onClick = onBack)
        } else {
            Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF7F9BC7)))
        }
        Spacer(Modifier.width(10.dp))
        if (big) {
            Text(
                title, color = FxC.ink, fontSize = 27.sp, fontFamily = FxFonts.title,
                fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.graphicsLayer(rotationZ = -1.2f, translationY = 3f),
            )
        } else {
            // Un cran plus grand que la maquette (13) : le titre « Effects » se
            // lisait trop petit en 13 sp une fois la fenêtre mise à l'échelle.
            Text(title, color = FxC.ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (counter != null) {
            Text(
                counter.uppercase(), color = FxC.mute,
                fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp,
            )
        }
        trailing()
        Spacer(Modifier.width(6.dp))
        FxIconButton(AeFxIcons.CLOSE, onClick = onClose)
    }
}

/** Bouton carré de l'en-tête : fond sobre, glyphe centré. */
@Composable
fun FxIconButton(icon: String, onClick: () -> Unit, size: Int = 28, glyph: Int = 14) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { FxIcon(icon, glyph.dp, FxC.dim) }
}

/** Le bouton Save, qui s'allume dès qu'un réglage a bougé. */
@Composable
private fun FxSaveButton(custom: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(9.dp))
            .background(
                Brush.verticalGradient(
                    if (custom) listOf(FxC.acc.copy(alpha = .30f), FxC.acc.copy(alpha = .10f))
                    else listOf(Color(0xFF232A38), Color(0xFF171D28)),
                )
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FxIcon(AeFxIcons.SAVE, 12.dp, if (custom) FxC.ink else FxC.dim, sw = 2.1f)
        Spacer(Modifier.width(6.dp))
        Text("Save", color = if (custom) FxC.ink else FxC.dim,
            fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * L'état de l'arpégiateur.
 *
 * Il vit dans la fenêtre et non dans la page, parce que l'en-tête porte BPM et
 * Swing : deux composants lisent le même état. `vel` et `on` sont des listes
 * mutables d'observables — une grille de 16 pas se modifie pas à pas, et une
 * liste immuable aurait forcé à recopier les seize à chaque geste.
 */
class ArpState {
    var sub by mutableStateOf(2)
    var style by mutableStateOf(0)
    var steps by mutableStateOf(16)
    var bpm by mutableStateOf(120)
    var swing by mutableStateOf(12)
    /** Le pas courant, pour la rangée de points. */
    var cur by mutableStateOf(0)
    val vel = mutableStateListOf(*Array(16) { i -> if (i % 4 == 0) 1f else if (i % 2 == 0) .78f else .62f })
    val on = mutableStateListOf(*Array(16) { true })
}

/**
 * Le compteur `− valeur +` de l'en-tête et de la grille.
 *
 * La valeur se tire VERTICALEMENT aussi : sur une plage de 200 pas, appuyer 190
 * fois sur `+` n'est pas un réglage, c'est une punition. Le glissement vaut
 * `(max-min)/170` par unité, comme la maquette.
 */
@Composable
fun FxNumStep(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    width: Int,
    unit: String = "",
    onChange: (Int) -> Unit,
) {
    var drag by remember { mutableStateOf<Pair<Float, Int>?>(null) }
    val sens = (max - min) / 170f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(), color = FxC.dim, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.3.sp,
        )
        Spacer(Modifier.width(6.dp))
        FxStepBtn("−") { onChange((value - 1).coerceAtLeast(min)) }
        Text(
            "$value$unit", color = FxC.ink, fontSize = 13.sp, fontFamily = FxFonts.mono,
            fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(width.dp).pointerInput(label) {
                detectVerticalDragGestures(
                    onDragStart = { drag = it.y to value },
                    onDragEnd = { drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    val d = drag ?: return@detectVerticalDragGestures
                    val next = (d.second + (d.first - change.position.y) * sens).toInt()
                    onChange(next.coerceIn(min, max))
                }
            },
        )
        FxStepBtn("+") { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun FxStepBtn(txt: String, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(txt, color = FxC.dim, fontSize = 14.sp) }
}
