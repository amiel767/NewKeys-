package com.aetherkeys.fx

/*
 * ARPEGGIATOR — the editor body shown inside FxSlotsWindow when the MIDI slot
 * holds an Arpeggiator.
 *
 * It used to live inside the old FX window's effect page, which is gone. It is
 * standalone on purpose: the shell gives it the whole body area, and it keeps
 * its own state ([ArpState]) so the host can push it to the engine.
 *
 * BPM and Swing stay in the mixer bar (Swing is a GLOBAL setting), so they are
 * not drawn here — copying them would have made the two displays drift apart.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset

/**
 * L'état de l'arpégiateur. L'hôte le détient pour pouvoir le pousser au moteur :
 * gardé dans un `remember` de la fenêtre, il aurait été illisible de l'extérieur.
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

/** `clickable` sans ondulation : la maquette n'en a aucune. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

@Composable
fun AeArpScreen(arp: ArpState, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF0B0F18)))).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArpSegmented(FxDesign.SUBS, arp.sub, 188.dp) { arp.sub = it }
            ArpSegmentedIcon(FxDesign.STYLES.size, arp.style, Modifier.weight(1f)) { arp.style = it }
            Box(Modifier.width(1.dp).height(30.dp).background(FxC.line))
            ArpNumStep("Steps", arp.steps, 1, 16, 20) { arp.steps = it }
            // La sortie de l'écran vit DANS sa barre de titre, comme la fermeture de
            // Space : la fenêtre n'ajoute plus d'en-tête, donc plus de cadre en haut.
            if (onBack != null) {
                Box(Modifier.width(1.dp).height(30.dp).background(FxC.line))
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickableNoRipple(onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = FxC.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
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
private fun ArpSegmented(options: List<String>, value: Int, width: Dp, onSelect: (Int) -> Unit) {
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
private fun ArpSegmentedIcon(count: Int, value: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
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

/**
 * Le compteur `− valeur +`. La valeur se tire VERTICALEMENT aussi : sur une plage
 * de 200 pas, appuyer 190 fois sur `+` n'est pas un réglage, c'est une punition.
 * Le glissement vaut `(max-min)/170` par unité, comme la maquette.
 */
@Composable
private fun ArpNumStep(label: String, value: Int, min: Int, max: Int, width: Int, onChange: (Int) -> Unit) {
    var drag by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    val sens = (max - min) / 170f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(), color = FxC.dim, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp, modifier = Modifier.padding(end = 8.dp),
        )
        Row(
            Modifier.width(width.dp + 64.dp).height(30.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xCC0F141D)).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepBtn("−") { onChange((value - 1).coerceIn(min, max)) }
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .pointerInput(value) {
                        detectVerticalDragGestures(
                            onDragStart = { drag = it to value },
                            onDragEnd = { drag = null },
                        ) { change, dy ->
                            val (p0, v0) = drag ?: return@detectVerticalDragGestures
                            drag = change.position to v0
                            val d = ((p0.y - change.position.y) * sens).toInt()
                            onChange((v0 + d).coerceIn(min, max))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("$value", color = Color(0xFFF1F4FA), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold) }
            StepBtn("+") { onChange((value + 1).coerceIn(min, max)) }
        }
    }
}

@Composable
private fun StepBtn(txt: String, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(txt, color = FxC.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}
