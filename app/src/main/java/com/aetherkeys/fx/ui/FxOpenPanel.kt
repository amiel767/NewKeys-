package com.aetherkeys.fx.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelText
import com.soundstage.mixer.ui.fx.FxStyle
import com.soundstage.mixer.ui.fx.raised
import com.soundstage.mixer.ui.fx.drawFxRail

/**
 * The open effect window (`#ex` content), on the same 825x343 logical canvas as
 * the rest of the window. Geometry is `FxGeom` / `FxKnobBoxes`, measured in the
 * reference, and used verbatim: 1 logical px = 1 dp here, the caller owns the
 * single scale. Knob coordinates are window coordinates (the measured boxes
 * already include the content origin, verified on ref-10-amp.png: Gain sits at
 * x = 91.2 while its box starts at x = 68).
 */
@Composable
fun FxOpenPanel(
    module: EffectModule,
    params: FloatArray,
    presets: List<String>,
    activePreset: String?,
    bypassed: Boolean,
    abLabel: String,
    onParam: (index: Int, value: Float) -> Unit,
    onPreset: (String) -> Unit,
    onBypass: () -> Unit,
    onAb: () -> Unit,
    onSurprise: () -> Unit,
    onClose: () -> Unit,
    page: Int,
    onPage: (Int) -> Unit,
    onEdit: () -> Unit,
    editOpen: Boolean,
    tm: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val clock = rememberFxClock(fullRate = true)
    Box(modifier = modifier.size(CANVAS_W.dp, CANVAS_H.dp)) {
        // `.bd`: the scene canvas, 779x263 at the content origin (20,58). The
        // scenes are written in content-local coordinates and paint their `.xN`
        // background from (-OXL,-OYL), which lands exactly on the window, so the
        // effect colour fills the frame and no clip is needed here: the host's
        // rounded clip already bounds the window.
        Box(
            Modifier
                .offset(x = SCENE_X.dp, y = SCENE_Y.dp)
                .size(779.dp, 263.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val d = this.density
                // Only the logical-unit -> pixel scale. The scenes already carry the
                // (OXL, OYL) offset in their coordinates (`y0 = -OYL`), so they are
                // drawn at the window origin and the background lands at (0,0).
                // Adding translate(20,58) on top shifted everything down by 58 and
                // inset the background, which is what truncated the bottom.
                withTransform({ scale(d, d, pivot = Offset.Zero) }) {
                    with(module) { drawScene(params, clock.value, mini = false, tm = tm, page = page) }
                    // Only the instruments that actually have one get the bottom rail.
                    // The amp (10) and the bass (11) have no `RS` entry: their "Level"
                    // is a regular knob and the panel runs to the bottom.
                    if (FxStyle.hasRail(module.id)) {
                        drawFxRail(module.id, params.getOrElse(FxStyle.railLevelIndex(module.id)) { 0f }, tm)
                    }
                }
            }
        }

        // `#cl`: close chip pinned at 12,12 (top-left), the `.ph` padding clears it.
        Box(Modifier.offset(x = 12.dp, y = 12.dp)) {
            SmallChip("⌄", onClose)
        }

        // `#pm`: preset menu pinned top-right (176 wide, top 11). `zIndex` lifts the
        // open list above the knobs, which are declared later in this Box and would
        // otherwise paint over it (the reference has `#pm` on its own layer).
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 11.dp).zIndex(1f)) {
            PresetChip(activePreset ?: presets.firstOrNull() ?: "", presets, onPreset)
        }

        // `.ph`: height 58, padding 0 16 0 58.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(start = 58.dp, end = 200.dp),
        ) {
            BypassChip(bypassed, onBypass, fxPanelText(module.id))
            Spacer(Modifier.width(12.dp))
            Column {
                val style = NAME_STYLES[module.id] ?: NAME_STYLES.getValue(0)
                // `.ph .nm`/`.ty` take their colour from the panel's `.xN` rule: solar
                // (`.x4`) is dark ink on a cream face, and the hard-coded light grey
                // made the whole title invisible.
                val ink = fxPanelText(module.id)
                // `lineHeight = fontSize`: a `Text` in this 58 px header would
                // otherwise inherit the Material3 line height (24 sp) and sit ~2 px
                // low. Measured against the reference every title was +2 px down.
                // `.ph .nm{font-size:30px;line-height:1}` declares no weight and no
                // tracking, so the title keeps the per-effect face from the doc 03
                // table but takes weight 400 and zero tracking. `lineHeight =
                // fontSize` stops the Material3 24 sp line height from pushing it low.
                Text(module.name, fontSize = style.first.sp, fontFamily = FxFonts.title(module.id),
                    fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
                    lineHeight = style.first.sp, color = ink, maxLines = 1)
                Text(module.type.uppercase(), fontSize = SUB_TITLE_SIZE[module.id]?.sp ?: 9.sp, fontFamily = FxFonts.ui,
                    fontWeight = FontWeight.Bold, letterSpacing = (9 * 0.18f).sp,
                    color = ink.copy(alpha = 0.6f), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (module.id >= 5) {
                SmallChip("Surprise me", onSurprise)
                Spacer(Modifier.width(8.dp))
                SmallChip(abLabel, onAb)
                // `[5,7,8,9,12].includes(i)`: the soundfont-backed instruments
                // carry the Edit chip; the preview only toggles `.on` on `#ed`,
                // which has no markup of its own, so the panel below is a stand-in.
                if (module.id in EDIT_EFFECTS) {
                    Spacer(Modifier.width(8.dp))
                    SmallChip("Edit", onEdit, active = editOpen)
                }
            }
        }

        if (editOpen) EditPanel(module.id)

        // `.chg` groups (`TG(i,label,x,y,items)` in the preview): the preset/section
        // chips that sit above the knobs — CHANNEL + PEDALS for the amp, ADSR, BODY,
        // CABINET, MUTE, PERCUSSION, ROTARY for the others. Positions are window
        // coordinates, like the knob boxes.
        val chipGroups = FxChipGroups.byEffect[module.id].orEmpty()
        val pageChips = FxChipGroups.pages[module.id]
        (pageChips?.firstOrNull { it.page == page }?.groups ?: chipGroups).forEach { group ->
            ChipGroupRow(group, params) { onParam(it.first, it.second) }
        }

        // `.pgt` (`SPG` tabs): only the synth has pages. The reference shows SOUND.
        if (pageChips != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.offset(x = 288.dp, y = 14.dp),
            ) {
                listOf("SOUND", "MOTION", "VOICE").forEachIndexed { k, name ->
                    PageTab(name, active = k == page) { onPage(k) }
                }
            }
        }

        // Knobs, at their boxes. `FxKnobBoxes` is already in window coordinates
        // (the preview's `AB()` centre + (OXL,OYL)), so nothing is added here.
        // `.kn.ab` is translate(-50%,-50%) on the box centre.
        FxKnobBoxes.byEffect[module.id].orEmpty().forEachIndexed { i, box ->
            val spec = module.knobs.getOrNull(i) ?: return@forEachIndexed
            if (box.w <= 0f) return@forEachIndexed
            // `SPG`: knobs outside the active synth page are `display:none`.
            if (box.page >= 0 && page != -1 && box.page != page) return@forEachIndexed
            val v = params.getOrElse(spec.paramIndex) { 0f }
            val cx = box.x + box.w / 2f
            val cy = box.y + box.h / 2f
            // `AB(i,j,x,y,z)` passes the diameter z straight through; the old
            // 0.86 factor shrank every knob by 13 % (amp 40 vs 46, echo 47 vs 56).
            val diameter = box.w.dp
            FxKnob(
                label = box.label,
                value = v,
                accent = module.accent,
                diameter = diameter,
                gradientIndex = SKN[module.id] ?: box.sk,
                valueText = formatValue(module, spec.paramIndex, v),
                style = KnobStyle.forEffect(module.id),
                effectId = module.id,
                light = (SKN[module.id] ?: box.sk) in LIGHT_BODIES,
                onChange = { onParam(spec.paramIndex, it) },
                modifier = Modifier.offset(
                    x = cx.dp - diameter / 2f,
                    y = cy.dp - diameter / 2f,
                ),
            )
        }

    }
}

/**
 * `SKN={5:5,6:6,7:10,8:11,9:12,10:7,11:8,12:13,13:14,14:9}`: the cap material per
 * effect. In the preview `kk = SKN[i] !== undefined ? SKN[i] : sk`, so this wins
 * over the per-knob `sk` from `AB()`. The amp, for instance, asks for 1/4 in its
 * `AB()` calls but ends up on 7.
 */
private val SKN = mapOf(5 to 5, 6 to 6, 7 to 10, 8 to 11, 9 to 12, 10 to 7, 11 to 8, 12 to 13, 13 to 14, 14 to 9)

/** Effects whose knob bodies are dark, from `LIGHT` in the reference. */
/**
 * `LIGHT=[1,3,4,8,10,11,12,13]` from the preview. It is an index into the cap
 * materials `c0..c14`, NOT an effect id: the check is `LIGHT.includes(kk)` where
 * `kk = SKN[i] ?? sk`. Testing the effect id instead made every knob of the guitar
 * (id 9, kk 12) and of the bass (id 11, kk 8) dark when the reference draws them
 * light.
 */
private val LIGHT_BODIES = setOf(1, 3, 4, 8, 10, 11, 12, 13)

/** `.ph .nm`/`label` value readout: the reference prints the raw value plus unit. */
private fun formatValue(module: EffectModule, index: Int, norm: Float): String {
    val p = module.paramAt(index) ?: return "${(norm * 100).toInt()}"
    val raw = p.min + (p.max - p.min) * norm
    val d = p.decimals
    return "%.${d}f%s".format(raw, p.unit)
}

/** `.pgt i`: 5x9 padding, radius 99, `#ffffff10` fill, active `#7ad0ff` on `#04101c`. */
@Composable
private fun PageTab(name: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) Color(0xFF7AD0FF) else Color(0x10FFFFFF))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(name, fontSize = 9.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            letterSpacing = (9 * 0.08f).sp,
            color = if (active) Color(0xFF04101C) else Color(0xFFE6EAF3))
    }
}

@Composable
private fun BypassChip(bypassed: Boolean, onClick: () -> Unit, ink: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(28.dp)
            // `.bt` carries `box-shadow:0 2px 4px #0006` plus the usual top bevel, so
            // it reads as a raised key like `.ib`. `raised` is the project's chip
            // relief (two-pass shadow, top highlight, bottom shade).
            .raised(9.dp, 4.dp, Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(50))
                .background(if (bypassed) Color(0xFF2A3040) else Color(0xFFF2D58A)),
        )
        Spacer(Modifier.width(7.dp))
        // `.bt{color:inherit}`: the chip takes the panel's `.xN` text colour, so it
        // is dark ink on solar's cream face instead of invisible light grey.
        Text("Bypass", fontSize = 10.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (10 * 0.06f).sp, color = ink)
    }
}


/**
 * The preview's `TS` table: a per-effect override for the name line that beats the
 * base `.nm{font-size:30px}`. Using 30 px everywhere made echo's name 253 px wide
 * against 160 in the reference, and synth's far too large.
 */
/** `.ph .ty` is a flat 9px in the preview; solar's block reads a touch large next
 * to its 22px title, so it drops one step. */
private val SUB_TITLE_SIZE: Map<Int, Int> = mapOf(4 to 8)

private val NAME_STYLES: Map<Int, Triple<Int, FontWeight, Float>> = mapOf(
    0 to Triple(28, FontWeight.Light, 0f),
    1 to Triple(22, FontWeight.Medium, 0f),
    2 to Triple(21, FontWeight.SemiBold, 21f * 0.04f),
    3 to Triple(23, FontWeight.Normal, 0f),
    4 to Triple(22, FontWeight.ExtraBold, 0f),
    5 to Triple(24, FontWeight.Medium, 24f * 0.02f),
    6 to Triple(24, FontWeight.Medium, 24f * 0.02f),
    7 to Triple(24, FontWeight.Medium, 0f),
    8 to Triple(26, FontWeight.Light, 0f),
    9 to Triple(24, FontWeight.SemiBold, 0f),
    10 to Triple(22, FontWeight.SemiBold, 22f * 0.06f),
    11 to Triple(24, FontWeight.ExtraBold, 0f),
    12 to Triple(24, FontWeight.SemiBold, 0f),
    13 to Triple(26, FontWeight.Light, 0f),
    14 to Triple(14, FontWeight.Bold, 14f * 0.03f),
)

/** `.ib`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, 10 px bold. */
@Composable
private fun SmallChip(label: String, onClick: () -> Unit, active: Boolean = false) {
    RaisedChip(label, active = active, onClick = onClick)
}

/** `[5,7,8,9,12].includes(i)`: the instruments backed by a soundfont. */
private val EDIT_EFFECTS = setOf(5, 7, 8, 9, 12)

/**
 * `#ed`, the panel the Edit chip toggles. The preview's `#ed` has no markup of its
 * own, so this is the soundfont group list the older window carried, kept verbatim.
 */
@Composable
private fun EditPanel(effectId: Int) {
    Column(
        Modifier
            .offset(x = 545.dp, y = 44.dp)
            .width(268.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF01A2130))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("Soundfont edit", fontSize = 11.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, color = FxStyle.accent(effectId))
        Text("Applies to the soundfont of this track. No reload.", fontSize = 10.sp,
            fontFamily = FxFonts.ui, color = Color(0x99FFFFFF))
        Spacer(Modifier.height(8.dp))
        EDIT_GROUPS.forEach { (head, keys) ->
            Text(head, fontSize = 9.sp, fontFamily = FxFonts.mono,
                fontWeight = FontWeight.Bold, color = FxStyle.accent(effectId))
            keys.forEach { k ->
                Text(k, fontSize = 10.sp, fontFamily = FxFonts.ui, color = Color(0xFFB9C2D4))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private val EDIT_GROUPS: List<Pair<String, List<String>>> = listOf(
    "ENVELOPE" to listOf("Attack", "Decay", "Sustain", "Release"),
    "FILTER" to listOf("Cutoff", "Resonance"),
    "TUNING" to listOf("Coarse", "Fine"),
    "VELOCITY" to listOf("Curve"),
    "VIBRATO" to listOf("Vib depth", "Vib rate"),
    "KEY RANGE" to listOf("Key low", "Key high"),
)

/**
 * `.bt` chip: a low-contrast outline, a top-edge sheen and a short drop shadow so
 * it reads as raised. The active state (`.chg i.a`) is the brass fill.
 */
@Composable
private fun RaisedChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .raised(
                9.dp, 4.dp,
                if (active) Brush.verticalGradient(listOf(Color(0xFFF2D58A), Color(0xFFE0BE6F)))
                else Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (10 * 0.06f).sp,
            color = if (active) Color(0xFF2A1A10) else Color(0xFFE6EAF3), maxLines = 1)
    }
}

/**
 * `.chg i`: `padding:6px 8px; border-radius:7px; background:#00000066; border:1px
 * solid #ffffff14; font:700 10px`, active `#f2d58a` on `#2a1a10`. This is NOT the
 * same chip as `.ib` in the header (radius 9, gradient fill, 0 11 px, drop shadow):
 * reusing the header chip made every option 6 px too wide and gave it a shadow the
 * reference does not draw.
 */
@Composable
private fun ChgChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) Color(0xFFF2D58A) else Color(0x66000000))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF2A1A10) else Color(0xFFE9DCC0), maxLines = 1)
    }
}

/**
 * One `.chg` group: `.chg span` label (46 wide, brass) then `.chg i` options
 * (padding 6x8, radius 7, `#00000066` fill, `#ffffff14` border, active `#f2d58a`
 * on `#2a1a10`). An option with a `value` is one position of a stepped param; one
 * without is a toggle read as "on" above 0.5.
 */
@Composable
private fun ChipGroupRow(group: ChipGroup, params: FloatArray, onSet: (Pair<Int, Float>) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            // `TG` positions are content coordinates, like `AB`; shift by the same
            // (OXL, OYL) the scenes already carry.
            .offset(x = (group.x + FxStyle.OXL).dp, y = (group.y + FxStyle.OYL).dp),
    ) {
        if (group.label.isNotEmpty()) {
            // `TG` writes `style="width:auto;margin-right:6px"` on the span, which
            // beats `.chg span{width:46px}`: the label wraps its text and only the
            // 6 px gap separates it from the first option. Forcing 46 px pushed
            // every option to the right and made the row too wide.
            Text(group.label, fontSize = 8.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                letterSpacing = (8 * 0.12f).sp, color = Color(0xFFC9B88F),
                maxLines = 1, modifier = Modifier.padding(end = 6.dp))
        }
        group.items.forEach { item ->
            val v = params.getOrElse(item.param) { 0f }
            val on = if (item.value == null) v > 0.5f else kotlin.math.abs(v - item.value) < 0.01f
            ChgChip(item.label, on) { onSet(item.param to (item.value ?: if (v > 0.5f) 0f else 1f)) }
        }
    }
}

/**
 * Preset chip + list (`#pm` / `#pml`). `#pm` is its own dark surface —
 * `linear-gradient(#ffffff18,#ffffff0a)` over `#0d1119`, a `#ffffff1a` border and a
 * 10 px radius — not a `.bt` chip. Drawing it as a translucent chip made it vanish
 * on the light panels (effect 4), where the reference shows a dark 176x28 bar.
 */
@Composable
private fun PresetChip(current: String, presets: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // `#pm` is a fixed-height header (36 px) whose box grows to `max-height: 300px`
    // over 400 ms with cubic-bezier(.2,.9,.2,1); the list is clipped by the box and
    // the rows fade/translate in. Rendering it as a Material DropdownMenu made it
    // pop in a single frame (the "ugly" case the user flagged) and drew it in the
    // wrong place, outside the window chrome. The box below is the real animation:
    // only its height changes, so the header stays put and the rows are revealed.
    val rowH = 34.dp
    val headerH = 34.dp
    // Cap the visible list at 6 presets: `.pi` rows are 11px + 12px text (~34 px),
    // and `#pml` is clamped to `max-height: 236px`, so about six fit before the list
    // scrolls. The `+ Save preset` row is always the last entry.
    val listH = (rowH * (presets.size.coerceAtMost(6) + 1))
    val targetH = if (open) headerH + listH else headerH
    val height by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetH,
        animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)),
        label = "presetHeight",
    )
    val scrim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(300),
        label = "presetScrim",
    )
    Box(Modifier.width(176.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                // `#pm.o{box-shadow:0 14px 28px #000a}`: the shadow only appears while
                // the menu is open, which is what makes it read as a raised surface
                // instead of a flat bar.
                .shadow(if (open) 14.dp else 0.dp, RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D1119))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .clickable { open = !open },
        ) {
            // `.ph0`: the 34 px header, always visible; the current preset is the label.
            Row(
                Modifier.fillMaxWidth().height(headerH).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FxFonts.ui,
                    fontWeight = FontWeight.Bold, letterSpacing = (12 * 0.06f).sp,
                    color = Color(0xFFE6EAF3), maxLines = 1)
                Text("▾", fontSize = 12.sp, color = Color(0xFFE6EAF3))
            }
            if (scrim > 0f) {
                // `.pi` rows: revealed as the box grows, each fading in on the same
                // 300 ms curve. The scroll keeps a long list inside `max-height`.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    presets.forEach { p ->
                        val selected = p == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(rowH)
                                // `.pi{border-top:1px solid #ffffff0d}`: the hairline
                                // between rows is what separates them, not a gap.
                                .drawBehind {
                                    drawLine(Color(0x0DFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                                }
                                .clickable { open = false; onPick(p) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FxFonts.ui,
                                fontWeight = FontWeight.SemiBold,
                                // `.pi.c{color:#f2d58a}`: the selected preset is brass,
                                // not the shell's blue accent.
                                color = if (selected) Color(0xFFF2D58A) else Color(0xFFE6EAF3),
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    // `.pi sv`: the reference's "Save preset" row, dimmed to 60 %.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .drawBehind {
                                drawLine(Color(0x0DFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("＋ Save preset", fontSize = 12.sp, fontFamily = FxFonts.ui,
                            color = Color(0xFF6F7A90), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * `.bt` chip: a low-contrast outline, a top-edge sheen and a short drop shadow so
 * it reads as raised. The active state (`.chg i.a`) is the brass fill.
 */
