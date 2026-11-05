package com.aetherkeys.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * LA PAGE D'UN EFFET — elle REMPLIT toute la fenêtre.
 *
 * Il n'y a pas de bande d'en-tête ici : le titre est écrit DANS le corps, en
 * haut de la colonne de gauche, comme le mot d'un plugin posé dans son panneau.
 * La version précédente réservait 46 unités à une bande d'en-tête et laissait
 * une réserve vide sous les contrôles ; la référence ne fait ni l'un ni l'autre.
 *
 * La disposition est celle de la référence, traduite :
 *
 *  - À GAUCHE, une colonne : le titre de l'effet, puis le GROS potard du réglage
 *    principal, avec son libellé et sa valeur.
 *  - À DROITE, les autres réglages, chacun dans SA cellule — deux colonnes de
 *    cellules, un potard par cellule. Ils ne sont plus tassés dans une grille
 *    unique : la référence sépare les réglages, elle ne les empile pas.
 *  - Le sélecteur de preset est posé en haut à droite, dans le flux.
 *
 * LE FOND VIENT DU PRESET. Chaque preset porte deux teintes (`bgA`, `bgB`) qui
 * se fondent en diagonale, et le STYLE DU TITRE (corps, interlettre, graisse)
 * suit le même preset.
 */
@Composable
fun FxEffectPage(
    state: FxWindowState,
    actions: FxWindowActions,
    arp: ArpState,
    onClose: () -> Unit = {},
) {
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

    // L'index du réglage principal : un seul le porte, et les cellules montrent les autres.
    val mainIndex = fx.params.indexOfFirst { it.main }.let { if (it < 0) 0 else it }
    val mainParam = fx.params[mainIndex]
    // L'ouverture de la liste est tenue ICI : la liste doit se dessiner AU-DESSUS
    // des cellules, donc elle est un frère de la grille, pas son descendant.
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
        Row(Modifier.fillMaxSize().padding(FxGeo.PAGE_PAD.dp)) {
            // ── LA COLONNE DE GAUCHE : les commandes, le titre, le gros potard ──
            Box(Modifier.width(FxGeo.COL_L.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fx.space) {
                            FxSaveButton(custom = state.custom) { actions.beginSave() }
                            Spacer(Modifier.width(6.dp))
                        }
                        FxPresetButton(
                            preset = preset, custom = state.custom, palette = pal,
                            open = presetOpen, onToggle = { presetOpen = !presetOpen },
                        )
                        Spacer(Modifier.weight(1f))
                        FxIconButton(AeFxIcons.BACK, onClick = { actions.closePage() })
                        Spacer(Modifier.width(6.dp))
                        FxIconButton(AeFxIcons.CLOSE, onClick = onClose)
                    }
                    Spacer(Modifier.height(10.dp))
                    FxPageTitle(fx.name, pal)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                }
                if (presetOpen) {
                    FxPresetList(
                        fx = fx, preset = preset,
                        userNames = state.userPresets[fx.id] ?: emptyList(),
                        modifier = Modifier.padding(top = 34.dp).width(180.dp).zIndex(2f),
                        onPick = { actions.pickPreset(it); presetOpen = false },
                    )
                }
            }
            Spacer(Modifier.width(FxGeo.COL_GAP.dp))

            // ── LA COLONNE DE DROITE : les cellules, sur toute la hauteur ────
            // Les commandes sont à gauche : la hauteur entière revient aux
            // cellules, et c'est ce qui permet au potard de grandir.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                FxControlsGrid(fx, values, mainIndex, actions)
            }
        }
    }
}

/**
 * LE TITRE DE LA PAGE — écrit DANS le corps, pas dans une bande d'en-tête.
 *
 * Son STYLE appartient au preset : [FxPalette.titleSize], [titleSpacing] et
 * [titleWeight] changent d'un preset à l'autre. Cathedral s'écrit large et
 * espacé, Room serré et petit, Shimmer léger et aéré. Le mot ne change pas —
 * c'est sa mise en forme qui suit l'ambiance, comme le wordmark d'un plugin.
 *
 * Le léger devers (1,2°) est repris de la maquette : un titre parfaitement
 * d'aplomb dans un panneau se lit comme un formulaire.
 */
@Composable
private fun FxPageTitle(name: String, pal: FxPalette?) {
    val weight = when (pal?.titleWeight ?: 700) {
        in 0..500 -> FontWeight.Medium
        in 501..750 -> FontWeight.Bold
        else -> FontWeight.Black
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.Start) {
        Text(
            name, color = FxC.ink, fontSize = (pal?.titleSize ?: 30f).sp,
            fontFamily = FxFonts.display, fontWeight = weight,
            letterSpacing = (pal?.titleSpacing ?: 1f).sp,
            maxLines = 1,
            modifier = Modifier.graphicsLayer(rotationZ = -1.2f),
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.width(56.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                .background(pal?.core ?: FxC.acc),
        )
    }
}

/**
 * LES CELLULES DES RÉGLAGES — chaque réglage dans SA case, jamais empilés.
 *
 * C'est la demande : « on sépare et on agrandit la taille des knobs au lieu de
 * tout rassembler dans un seul cadre ». La version précédente mettait les neuf
 * réglages dans UN panneau, à 46 unités de potard. Ici chaque réglage a sa
 * cellule, avec son propre creux, et le potard passe à [FxGeo.KNOB] = 96.
 *
 * DEUX colonnes, pas trois : à trois colonnes les cellules sont plus étroites
 * que le disque et le libellé n'entre plus. Les cellules remplissent toute la
 * hauteur disponible — `SpaceEvenly` les répartit, il n'y a pas de réserve vide.
 */
@Composable
private fun ColumnScope.FxControlsGrid(
    fx: FxInfoD,
    values: List<Float>,
    mainIndex: Int,
    actions: FxWindowActions,
) {
    val others = fx.params.withIndex().filter { it.index != mainIndex }
    val rows = others.chunked(FxGeo.CELLS)
    Column(
        Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(FxGeo.COL_GAP.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(FxGeo.COL_GAP.dp),
            ) {
                row.forEach { (i, p) ->
                    FxControlCell(
                        label = p.name,
                        value = values.getOrElse(i) { p.def },
                        accent = fx.accent,
                        display = FxDesign.fmt(values.getOrElse(i) { p.def }, p),
                        modifier = Modifier.weight(1f),
                        onChange = { actions.setValue(i, it) },
                        onReset = { actions.setValue(i, p.def) },
                    )
                }
                // Une rangée incomplète garde ses colonnes : sinon le dernier
                // contrôle s'étale et ne s'aligne plus sur les autres.
                repeat(FxGeo.CELLS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * UNE CELLULE : le creux, le potard, le libellé et la valeur.
 *
 * Le creux est ce qui SÉPARE les réglages. Sans lui, deux potards voisins se
 * lisent comme une rangée continue — c'est exactement ce que la référence évite.
 */
@Composable
private fun FxControlCell(
    label: String,
    value: Float,
    accent: Color,
    display: String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(FxGeo.CELL_R.dp))
            .background(Brush.verticalGradient(listOf(FxC.cellTop, FxC.cellBot)))
            .fxRectWell(FxGeo.CELL_R.dp, top = .14f, rim = .08f),
        contentAlignment = Alignment.Center,
    ) {
        // Le plus grand disque qui entre dans la case, plafonné à la cible : une
        // taille figée était écrasée par les rangées et rien ne le signalait.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = minOf(maxWidth.value, maxHeight.value - FxGeo.KNOB_TEXT, FxGeo.KNOB.toFloat())
            FxKnob(
                label = label, value = value, accent = accent, display = display,
                size = d.toInt(), onChange = onChange, onReset = onReset,
            )
        }
    }
}

/**
 * LE SÉLECTEUR DE PRESET — une pastille COMPACTE : la teinte du preset, son nom,
 * et un chevron qui dit si la liste est ouverte.
 *
 * Elle ne s'étire PAS sur toute la largeur : c'est un petit bouton, posé à
 * droite de la grille, pas une barre. Le nom du preset vit aussi dans le titre
 * de la page (« Space Hall »), donc ce bouton n'a pas besoin d'être large.
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
        Modifier.height(26.dp).clip(RoundedCornerShape(8.dp))
            .fxRectWell(8.dp, top = .14f, rim = .10f)
            .clickableNoRipple(onToggle)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(hue))
        Spacer(Modifier.width(7.dp))
        Text(
            preset ?: "Preset", color = FxC.ink, fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1,
        )
        if (custom) {
            Spacer(Modifier.width(6.dp))
            Text("•", color = Color(0xFFF2C97D), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(6.dp))
        FxIcon(if (open) AeFxIcons.CHEV_UP else AeFxIcons.CHEV_DOWN, 11.dp, FxC.dim)
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
        modifier
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
