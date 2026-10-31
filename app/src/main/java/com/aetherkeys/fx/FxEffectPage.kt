package com.aetherkeys.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LA PAGE D'UN EFFET — presets à gauche, orbe au centre, réglages à droite.
 *
 * C'est la composition de la maquette, et c'est ce que la première version avait
 * raté : elle empilait un titre puis une rangée de potards, sans orbe et sans
 * colonne de presets. Trois zones, dans cet ordre :
 *
 *   | orbe (grand) | presets + 4 potards + les sliders |
 *
 * Les QUATRE PREMIERS réglages sont des potards, en haut de la colonne ; les
 * suivants sont des faders, en dessous. C'est le découpage de la maquette, et il
 * est fonctionnel : les quatre premiers se règlent en tournant, les autres en
 * glissant, et ils ne se disputent pas la même case.
 *
 * L'Arpeggiator n'a pas d'orbe : il a sa propre page, [FxArpPage].
 */
@Composable
fun FxEffectPage(state: FxWindowState, actions: FxWindowActions, arp: ArpState) {
    val fx = state.openEffect ?: return
    if (fx.kind == FxKindD.MIDI) {
        FxArpPage(fx, arp)
        return
    }
    val values = state.slots.firstOrNull { it?.info?.id == fx.id }?.values ?: fx.params.map { it.def }
    val on = state.slots.firstOrNull { it?.info?.id == fx.id }?.on ?: true
    val preset = state.presetName ?: fx.firstPreset
    val pal = preset?.let { fx.palettes[it] } ?: fx.palettes[fx.firstPreset]

    Row(Modifier.fillMaxSize()) {
        // ── L'ORBE : la grande zone de gauche ──────────────────────────────
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(15.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF0B0F18)))),
        ) {
            FxOrb(vals = values, palette = pal, live = on)
            // La pastille : preset actif, puis la durée de queue.
            Row(
                Modifier.padding(start = 11.dp, top = 9.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x8C0A0E1A))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hue = pal?.let { Color.hsv(it.h, it.s / 100f, it.l / 100f) } ?: FxC.acc
                Box(Modifier.size(5.dp).clip(CircleShape).background(hue))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (state.custom) "Custom" else (preset ?: ""),
                    color = Color(0xFFC6CEE8), fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = .9.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${FxDesign.decaySeconds(values.getOrElse(1) { .7f })} s",
                    color = FxC.mute, fontSize = 10.sp, fontFamily = FxFonts.mono,
                )
            }
            Text(
                "Touch the orb to re-fire",
                color = FxC.mute, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 8.dp),
            )
        }
        Spacer(Modifier.width(12.dp))

        // ── LA COLONNE DES RÉGLAGES, 268 unités ────────────────────────────
        Column(Modifier.width(268.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FxPresetRow(fx, preset, state.custom, state.userPresets[fx.id] ?: emptyList(), actions)
            // Les quatre premiers : des potards.
            Row(
                Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF0B0F18)))),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fx.params.take(4).forEachIndexed { i, p ->
                    FxKnob(
                        label = p.name, value = values.getOrElse(i) { p.def },
                        accent = fx.accent, display = FxDesign.fmt(values.getOrElse(i) { p.def }, p),
                        onReset = { actions.setValue(i, p.def) },
                        onChange = { actions.setValue(i, it) },
                    )
                }
            }
            // Les suivants : des faders HORIZONTAUX, comme la maquette.
            Column(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(13.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF0B0F18))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                fx.params.drop(4).forEachIndexed { j, p ->
                    val i = j + 4
                    FxSlider(
                        label = p.name, value = values.getOrElse(i) { p.def }, accent = fx.accent,
                        display = FxDesign.fmt(values.getOrElse(i) { p.def }, p),
                        onReset = { actions.setValue(i, p.def) },
                        onChange = { actions.setValue(i, it) },
                    )
                }
            }
        }
    }
}

/**
 * La rangée des presets : un intitulé, deux flèches, et le preset actif.
 *
 * Les flèches parcourent la liste ENTIÈRE, presets utilisateur compris — sinon
 * sauvegarder un preset ne servirait qu'à la souris. Le compteur `02/16` dit où
 * on en est dans la liste.
 */
@Composable
private fun FxPresetRow(
    fx: FxInfoD,
    preset: String?,
    custom: Boolean,
    userNames: List<String>,
    actions: FxWindowActions,
) {
    var open by remember { mutableStateOf(false) }
    val all = fx.presets.keys.toList() + userNames
    val i = all.indexOf(preset).coerceAtLeast(0)
    val pal = preset?.let { fx.palettes[it] }
    val hue = pal?.let { Color.hsv(it.h, it.s / 100f, it.l / 100f) } ?: FxC.acc

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Presets", color = FxC.mute, fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f),
            )
            FxMiniArrow(back = true) { actions.pickPreset(all[(i - 1 + all.size) % all.size]) }
            FxMiniArrow(back = false) { actions.pickPreset(all[(i + 1) % all.size]) }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp))
                .fxRectWell(9.dp, top = .14f, rim = .10f)
                .clickableNoRipple { open = !open }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(hue))
            Spacer(Modifier.width(8.dp))
            Text(
                preset ?: "", color = FxC.ink, fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (custom) {
                Text("Custom", color = Color(0xFFF2C97D), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${(i + 1).toString().padStart(2, '0')}/${all.size.toString().padStart(2, '0')}",
                color = FxC.mute, fontSize = 9.5.sp, fontFamily = FxFonts.mono,
            )
        }
    }
}

/** Une flèche de parcours des presets. */
@Composable
private fun FxMiniArrow(back: Boolean, onClick: () -> Unit) {
    FxButtonChip(side = 24.dp, onClick = onClick, radius = 7.dp) {
        FxIcon(if (back) AeFxIcons.BACK else AeFxIcons.STEP_NEXT, 12.dp, FxC.dim)
    }
}

/**
 * LA PAGE DE L'ARPEGGIATEUR.
 *
 * Une seule barre de commandes : la subdivision, les quatre sens (en schéma
 * seul), et le nombre de pas. Puis la grille : seize colonnes, une barre de
 * hauteur par pas, et sous chacune son numéro qui l'engage ou le coupe. Aucun
 * texte sur la grille — le dessin dit tout.
 *
 * L'ÉTAT VIENT DE LA FENÊTRE ([ArpState]) : BPM et Swing sont dans l'en-tête.
 * Les recopier ici les aurait dupliqués et fait diverger les deux affichages.
 */
@Composable
private fun FxArpPage(fx: FxInfoD, arp: ArpState) {
    val accent = fx.accent
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF0B0F18)))).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FxSegmented(FxDesign.SUBS, arp.sub, 188.dp) { arp.sub = it }
            FxSegmentedIcon(FxDesign.STYLES.size, arp.style, Modifier.weight(1f)) { arp.style = it }
            Box(Modifier.width(1.dp).height(30.dp).background(FxC.line))
            FxNumStep("Steps", arp.steps, 1, 16, 20) { arp.steps = it }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF0B0F18)))).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until 16) {
                    val active = i < arp.steps
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0xFF171B2E) else Color(0x08FFFFFF)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (active) {
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight(arp.vel[i])
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            if (arp.on[i]) Brush.verticalGradient(listOf(Color(0xFF6F79AE), Color(0xFF4D5788)))
                                            else Brush.verticalGradient(listOf(Color(0xFF323A5C), Color(0xFF323A5C)))
                                        ),
                                )
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (active && arp.on[i]) Brush.verticalGradient(listOf(Color(0xFF6B75A8), Color(0xFF505A8C)))
                                    else Brush.verticalGradient(listOf(Color(0xFF1F2539), Color(0xFF1F2539)))
                                )
                                .alpha(if (active) 1f else .35f)
                                .clickableNoRipple { if (active) arp.on[i] = !arp.on[i] },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}", color = Color(0xFFE7ECFB), fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
            // La rangée de points : le pas courant, sous la grille. Le quatrième
            // est plus clair — c'est le repère de temps. Aucun texte.
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 16) {
                    val cur = i == arp.cur
                    Box(
                        Modifier.padding(horizontal = 2.5.dp).size(if (cur) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (cur) FxC.in1
                                else if (i % 4 == 0) Color(0xFF3E4658) else Color(0xFF2A3040)
                            ),
                    )
                }
            }
        }
    }
}

/** Un toggle segmenté à libellés. */
@Composable
private fun FxSegmented(options: List<String>, value: Int, width: androidx.compose.ui.unit.Dp, onSelect: (Int) -> Unit) {
    Row(
        Modifier.width(width).height(34.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F141D)).padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                    .then(
                        if (i == value) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650))))
                        else Modifier
                    )
                    .clickableNoRipple { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == value) Color(0xFFF1F4FA) else FxC.mute,
                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Un toggle segmenté en schéma seul, pour les sens de parcours. */
@Composable
private fun FxSegmentedIcon(count: Int, value: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.height(34.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC0F141D)).padding(3.dp),
    ) {
        for (i in 0 until count) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                    .then(
                        if (i == value) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650))))
                        else Modifier
                    )
                    .clickableNoRipple { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                FxIcon(
                    listOf(AeFxIcons.ARP_UP, AeFxIcons.ARP_DOWN, AeFxIcons.ARP_UPDOWN, AeFxIcons.ARP_RANDOM)[i],
                    22.dp, if (i == value) Color(0xFFF1F4FA) else FxC.mute,
                )
            }
        }
    }
}
