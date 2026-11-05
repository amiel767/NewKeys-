package com.aetherkeys.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * LA PAGE D'UN EFFET — un gros potard à gauche, les contrôles à droite.
 *
 * C'est la composition de la référence : UN réglage principal, tenu par le gros
 * potard, et tout le reste rangé en grille à côté. L'orbe a disparu — il ne
 * portait aucune information que le potard ne porte mieux.
 *
 * LE FOND VIENT DU PRESET. Chaque preset porte deux teintes (`bgA`, `bgB`) qui
 * se fondent en diagonale : violet-noir, orange-rouge, noir-bleu ciel. Changer
 * de preset change donc l'ambiance de la page entière, pas seulement un liseré.
 *
 * L'Arpeggiator n'a pas de potard principal : il a sa propre page, [FxArpPage].
 */
@Composable
fun FxEffectPage(state: FxWindowState, actions: FxWindowActions, arp: ArpState) {
    val fx = state.openEffect ?: return
    if (fx.kind == FxKindD.MIDI) {
        FxArpPage(fx, arp)
        return
    }
    val values = state.slots.firstOrNull { it?.info?.id == fx.id }?.values ?: fx.params.map { it.def }
    val preset = state.presetName ?: fx.firstPreset
    val pal = preset?.let { fx.palettes[it] } ?: fx.palettes[fx.firstPreset]
    val bgA = pal?.bgA ?: Color(0xFF0B0F18)
    val bgB = pal?.bgB ?: Color(0xFF161C2A)

    // ── LE FOND, FUSION DE DEUX TEINTES ─────────────────────────────────────
    // Un aplat sombre, puis DEUX voiles colorés tirés des deux teintes du preset
    // (h et h2), posés en coins opposés. C'est la « fusion de deux couleurs » :
    // violet/noir, orange/rouge, noir/bleu ciel. Un simple dégradé entre bgA et
    // bgB ne suffisait pas — les deux teintes étant toutes deux très sombres, la
    // page ne changeait pas d'ambiance d'un preset à l'autre (243° contre 249°
    // mesurés). Les voiles colorés, eux, se lisent.
    val hueA = pal?.let { Color.hsv(it.h, (it.s / 100f).coerceAtMost(.85f), .55f) } ?: fx.accent
    val hueB = pal?.let { Color.hsv(it.h2, (it.s / 100f).coerceAtMost(.85f), .50f) } ?: fx.accent

    // L'index du réglage principal : un seul le porte, et la grille montre les autres.
    val mainIndex = fx.params.indexOfFirst { it.main }.let { if (it < 0) 0 else it }
    val mainParam = fx.params[mainIndex]
    // L'ouverture de la liste est tenue ICI : la liste doit se dessiner AU-DESSUS
    // de la grille, donc elle est un frère de la grille, pas son descendant.
    var presetOpen by remember(fx.id) { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(bgA, bgB))),
    ) {
        // Les deux voiles : deux taches larges et douces, en coins opposés.
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val a = Brush.radialGradient(
                    listOf(hueA.copy(alpha = .34f), Color.Transparent),
                    center = Offset(size.width * .18f, size.height * .16f),
                    radius = size.minDimension * 1.05f,
                )
                val b = Brush.radialGradient(
                    listOf(hueB.copy(alpha = .30f), Color.Transparent),
                    center = Offset(size.width * .88f, size.height * .92f),
                    radius = size.minDimension * 1.15f,
                )
                onDrawBehind { drawRect(a); drawRect(b) }
            },
        )
        Row(Modifier.fillMaxSize().padding(10.dp)) {
            // ── LE GROS POTARD ──────────────────────────────────────────────
            Column(
                Modifier.width(250.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FxMagmaKnob(
                    label = mainParam.name,
                    value = values.getOrElse(mainIndex) { mainParam.def },
                    display = FxDesign.fmt(values.getOrElse(mainIndex) { mainParam.def }, mainParam),
                    palette = pal,
                    accent = fx.accent,
                    onChange = { actions.setValue(mainIndex, it) },
                    onReset = { actions.setValue(mainIndex, mainParam.def) },
                )
            }
            Spacer(Modifier.width(10.dp))

            // ── LA COLONNE DE DROITE : le sélecteur, puis les contrôles ──────
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    FxPresetButton(
                        preset = preset, custom = state.custom, palette = pal,
                        open = presetOpen, onToggle = { presetOpen = !presetOpen },
                    )
                    Spacer(Modifier.height(8.dp))
                    FxControlsGrid(fx, values, mainIndex, actions)
                }
                if (presetOpen) {
                    FxPresetList(
                        fx = fx, preset = preset,
                        userNames = state.userPresets[fx.id] ?: emptyList(),
                        modifier = Modifier.padding(top = 40.dp).zIndex(2f),
                        onPick = { actions.pickPreset(it); presetOpen = false },
                    )
                }
            }
        }
    }
}

/**
 * LA GRILLE DES CONTRÔLES — trois colonnes, libellé et valeur sous chaque potard.
 *
 * C'est la disposition de la référence : des cellules alignées, jamais une
 * rangée qui s'étire. Le réglage principal n'y figure pas — il est déjà le gros
 * potard, le répéter en petit ferait doublon.
 */
@Composable
private fun ColumnScope.FxControlsGrid(
    fx: FxInfoD,
    values: List<Float>,
    mainIndex: Int,
    actions: FxWindowActions,
) {
    val others = fx.params.withIndex().filter { it.index != mainIndex }
    val rows = others.chunked(3)
    Column(
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(13.dp))
            // Un fond SOMBRE TRANSLUCIDE, pas seulement le creux : sans lui la
            // grille flotte sur le dégradé du preset et ne se lit pas comme un
            // panneau. Le creux par-dessus donne l'arête.
            .background(Color(0x4D06090F))
            .fxRectWell(13.dp, top = .12f, rim = .08f)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { (i, p) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FxKnob(
                            label = p.name, value = values.getOrElse(i) { p.def },
                            accent = fx.accent,
                            display = FxDesign.fmt(values.getOrElse(i) { p.def }, p),
                            size = 46,
                            onReset = { actions.setValue(i, p.def) },
                            onChange = { actions.setValue(i, it) },
                        )
                    }
                }
                // Une rangée incomplète garde ses colonnes : sinon les deux
                // derniers contrôles s'étalent et ne s'alignent plus sur la grille.
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * LE BOUTON DE PRESET — le titre « Space <Preset> », une pastille de la teinte
 * du preset, et un chevron qui dit si la liste est ouverte.
 *
 * Il ne tient PAS la liste : celle-ci est un frère de la grille, pour pouvoir
 * passer au-dessus. Le bouton ne fait que dire son état.
 */
@Composable
private fun FxPresetButton(
    preset: String?,
    custom: Boolean,
    palette: FxPalette?,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val hue = palette?.let { Color.hsv(it.h, it.s / 100f, it.l / 100f) } ?: FxC.acc
    Row(
        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
            .fxRectWell(10.dp, top = .14f, rim = .10f)
            .clickableNoRipple(onToggle)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(hue))
        Spacer(Modifier.width(9.dp))
        Text(
            "Space${preset ?: ""}", color = FxC.ink, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f),
        )
        if (custom) {
            Text("Custom", color = Color(0xFFF2C97D), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(7.dp))
        }
        FxIcon(if (open) AeFxIcons.CHEV_UP else AeFxIcons.CHEV_DOWN, 13.dp, FxC.dim)
    }
}

/**
 * LA LISTE DES PRESETS, dépliée sous le bouton.
 *
 * Elle est opaque : c'est ce qui la sépare de la grille qu'elle recouvre. Une
 * version translucide laissait lire les potards au travers et les deux se
 * mélangeaient.
 */
@Composable
private fun FxPresetList(
    fx: FxInfoD,
    preset: String?,
    userNames: List<String>,
    modifier: Modifier,
    onPick: (String) -> Unit,
) {
    val all = fx.presets.keys.toList() + userNames
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF141926))
            .fxRectWell(11.dp, top = .10f, rim = .08f)
            .padding(vertical = 4.dp),
    ) {
        all.forEach { name ->
            val p = fx.palettes[name]
            val dot = p?.let { Color.hsv(it.h, it.s / 100f, it.l / 100f) } ?: FxC.mute
            val here = name == preset
            Row(
                Modifier.fillMaxWidth().height(28.dp)
                    .clickableNoRipple { onPick(name) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(9.dp))
                Text(
                    name, color = if (here) FxC.ink else FxC.dim, fontSize = 12.sp,
                    fontWeight = if (here) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
                if (here) Box(Modifier.size(5.dp).clip(CircleShape).background(FxC.acc))
            }
        }
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
