package com.aetherkeys.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * QUATRE EMPLACEMENTS MASTER. La maquette montre quatre cases Audio plus une
 * MIDI ; ici il n'y a QUE du Master, donc quatre cases Audio et pas de case
 * MIDI — un emplacement MIDI vide occuperait une des quatre places pour rien.
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
    val title = when {
        state.openEffect != null -> state.openEffect.name
        state.catalogOpen -> "Add effect"
        else -> "Effects"
    }
    val count = state.slots.count { it != null }

    FxStage(modifier = modifier, scale = scale) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(FxGeo.RADIUS.dp))
                .background(Brush.verticalGradient(listOf(FxC.winTop, FxC.winBot))),
        ) {
            Column(Modifier.fillMaxSize()) {
                FxHeader(
                    title = title,
                    big = state.openEffect != null,
                    onBack = if (state.openEffect != null || state.catalogOpen) ({ actions.closePage() }) else null,
                    counter = if (state.openEffect == null && !state.catalogOpen) "$count / ${FxGeo.SLOTS}" else null,
                    onClose = { actions.onClose() },
                )
                Box(Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    when {
                        state.catalogOpen -> FxCatalog(state, actions)
                        state.openEffect != null -> FxEffectPage(state, actions)
                        else -> FxRack(state, actions)
                    }
                }
            }
        }
    }
}

/** La rangée des quatre emplacements. */
@Composable
private fun FxRack(state: FxWindowState, actions: FxWindowActions) {
    Row(Modifier.fillMaxSize()) {
        state.slots.forEachIndexed { i, slot ->
            if (i > 0) FxGap()
            if (slot != null) {
                FxCard(
                    info = slot.info,
                    accent = slot.accent,
                    on = slot.on,
                    presetName = slot.presetName,
                    onToggle = { actions.toggle(i) },
                    onOpen = { actions.open(slot.info.id) },
                    onRemove = { actions.remove(i) },
                )
            } else {
                FxEmptySlot("Audio") { actions.openCatalog() }
            }
        }
    }
}

/** Le catalogue : la liste des effets disponibles, à gauche. */
@Composable
private fun FxCatalog(state: FxWindowState, actions: FxWindowActions) {
    val full = state.slots.count { it != null } >= FxGeo.SLOTS
    Column(Modifier.fillMaxSize()) {
        if (full) {
            Text(
                "All slots are full — remove one first",
                color = FxC.dangerSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            val cat = state.catalog ?: return@LazyColumn
            items(cat.size) { idx ->
                val info = cat[idx]
                FxCatalogRow(info, state.accentOf(info.id), enabled = !full) {
                    actions.add(info.id)
                }
            }
        }
    }
}

/** Une ligne du catalogue : type, nom, et l'accent de l'effet. */
@Composable
private fun FxCatalogRow(info: FxInfo, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).padding(vertical = 3.dp)
            .clip(RoundedCornerShape(11.dp))
            .fxSvg(AeFxSvg.ghost(11f))
            .clickableNoRipple { if (enabled) onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(info.name, color = if (enabled) FxC.ink else FxC.mute,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(info.type.uppercase(), color = FxC.mute,
                fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, maxLines = 1)
        }
    }
}

/**
 * La page d'un effet : son titre, ses réglages, et la sauvegarde.
 *
 * C'est la partie la plus dense de la maquette. Elle est isolée ici pour que le
 * rack reste lisible, et parce que c'est elle qui bougera le plus.
 */
@Composable
private fun FxEffectPage(state: FxWindowState, actions: FxWindowActions) {
    val info = state.openEffect ?: return
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(info.type.uppercase(), color = state.accentOf(info.id),
                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Spacer(Modifier.weight(1f))
            FxSaveButton(custom = state.custom, onClick = { actions.beginSave() })
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).fxSvg(AeFxSvg.well(12f))) {
            FxKnobGrid(state, actions)
        }
    }
}

/** La grille des réglages : un potard par paramètre. */
@Composable
private fun FxKnobGrid(state: FxWindowState, actions: FxWindowActions) {
    val info = state.openEffect ?: return
    val accent = state.accentOf(info.id)
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        items(info.params.size) { idx ->
            val p = info.params[idx]
            FxKnob(
                label = p.name,
                value = state.values.getOrElse(idx) { p.default },
                min = p.min, max = p.max, unit = p.unit, decimals = p.decimals,
                accent = accent,
                onChange = { actions.setValue(idx, it) },
            )
        }
    }
}

/** Le bouton Save, qui s'allume dès qu'un réglage a bougé. */
@Composable
private fun FxSaveButton(custom: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (custom) 1f else 0f, tween(180), label = "save")
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(9.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(FxC.rail, FxC.acc.copy(alpha = .30f), a),
                        lerp(Color(0xFF171D28), FxC.acc.copy(alpha = .10f), a),
                    )
                )
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        FxIcon(AeFxIcons.SAVE, 12.dp, if (custom) FxC.ink else FxC.dim, sw = 2.1f)
        Spacer(Modifier.width(6.dp))
        Text("Save", color = if (custom) FxC.ink else FxC.dim,
            fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Mélange deux couleurs, pour les transitions d'état. */
private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
