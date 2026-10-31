package com.aetherkeys.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.fx.FxFonts

/** Add overlay: two tabs (Effects / Instrument), family chips, search, grid. Tiles are sized from FxGeom. */
enum class FxAddTab { EFFECTS, INSTRUMENT }

/**
 * The `#ad` sheet of fx-window-preview.html, ported from `renderAdd()` / `listAdd()`:
 * header, the two tab pills with their counts, family chips plus a search box, then
 * a three-column grid of tiles. Each tile carries the effect's own icon, its name in
 * the effect's face, and a `family · description` line, exactly as the preview's
 * `CAT2` table spells them. The old version was a bare name/type list with no icons,
 * families or search, which is why it read as "a weird, ugly page".
 */
@Composable
fun FxAddOverlay(
    modules: List<EffectModule>,
    tab: FxAddTab,
    onTab: (FxAddTab) -> Unit,
    onPick: (EffectModule) -> Unit,
    isOn: (Int) -> Boolean,
    effectsUsed: Int,
    onClose: () -> Unit,
) {
    var family by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }

    val families = if (tab == FxAddTab.EFFECTS) FAMS_EFFECTS else FAMS_INSTRUMENT
    if (family !in families) family = "All"

    val isInstrument = { m: EffectModule -> m.kind != FxScreenKind.EFFECT }
    val visible = modules
        .filter { if (tab == FxAddTab.EFFECTS) !isInstrument(it) else isInstrument(it) }
        // Æther track instruments (organ 6, synth 14, leads 15, sax 16) are no longer FX
        // entries: they load per track from the soundfont window's Æther Instruments tab.
        // They stay in the registry so a strip that carries one still resolves, but they must
        // not be offered here.
        .filter { it.id !in AE_RETIRED_INSTRUMENT_IDS }
        .filter { family == "All" || AddMeta.familyOf(it.id) == family }
        .filter {
            query.isBlank() || (it.name + " " + it.type + " " + AddMeta.descOf(it.id) + " " + AddMeta.familyOf(it.id))
                .contains(query, ignoreCase = true)
        }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // `.ah`: height 48, title left, close right.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (tab == FxAddTab.EFFECTS) "Add an effect" else "Choose the instrument",
                fontSize = 12.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (12 * 0.16f).sp, color = Color(0xFF8D96AB),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x08FFFFFF))))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(9.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
                    val s = 1.6f * density
                    drawLine(Color(0xFFE6EAF3), Offset(0f, 0f), Offset(size.width, size.height), s, cap = StrokeCap.Round)
                    drawLine(Color(0xFFE6EAF3), Offset(size.width, 0f), Offset(0f, size.height), s, cap = StrokeCap.Round)
                }
            }
        }

        // `.tbs`: EFFECTS n/6 | INSTRUMENT 1 per track.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPill("EFFECTS", "$effectsUsed/6", tab == FxAddTab.EFFECTS) { onTab(FxAddTab.EFFECTS) }
            TabPill("INSTRUMENT", "1 per track", tab == FxAddTab.INSTRUMENT) { onTab(FxAddTab.INSTRUMENT) }
        }

        // `.cf`: family chips, search box pushed to the right.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            families.forEach { f ->
                val on = family == f
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (on) Color(0xFFF2F4FA) else Color(0x0EFFFFFF))
                        .border(1.dp, if (on) Color.Transparent else Color(0x14FFFFFF), RoundedCornerShape(99.dp))
                        .clickable { family = f }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(f, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                        letterSpacing = (10 * 0.06f).sp,
                        color = if (on) Color(0xFF0B0F1C) else Color(0xFFE6EAF3))
                }
            }
            Box(Modifier.weight(1f))
            Box(
                Modifier
                    .width(150.dp).height(28.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x44000000))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(99.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text("Search…", fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6D778C))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFFFFFFF), fontSize = 11.sp, fontFamily = FxFonts.ui),
                    cursorBrush = SolidColor(Color(0xFF8FB2E8)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // `.gl`: three columns of tiles.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visible, key = { it.id }) { m ->
                val instrument = isInstrument(m)
                val on = isOn(m.id)
                val disabled = !instrument && (on || effectsUsed >= 6)
                AddTile(m = m, on = on, disabled = disabled, onClick = { onPick(m) })
            }
        }
    }
}

/** `.tbs span`: a pill with the label and its count on separate lines. */
@Composable
private fun TabPill(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFF2F4FA) else Color(0x0EFFFFFF))
            .border(1.dp, if (selected) Color.Transparent else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (10 * 0.08f).sp,
            color = if (selected) Color(0xFF0B0F1C) else Color(0xFFE6EAF3))
        Text(count, fontSize = 9.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF5A6478) else Color(0xFF8D96AB))
    }
}

/** `.tl`: icon, name in the effect's face, `family · description`, optional ON badge. */
@Composable
private fun AddTile(m: EffectModule, on: Boolean, disabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth().height(FxGeom.ADD_TILE_H.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x12FFFFFF), Color(0x06FFFFFF))))
            .border(1.dp, Color(0x12FFFFFF), shape)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp)
            .then(if (disabled) Modifier.alpha(0.4f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(AddMeta.iconRes(m.id)),
            contentDescription = null,
            tint = Color(0xFFCFD6E6),
            modifier = Modifier.size(30.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                m.name,
                fontSize = 14.sp,
                fontFamily = FxFonts.title(m.id),
                color = Color(0xFFE6EAF3),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${AddMeta.familyOf(m.id)} · ${AddMeta.prefix(m.id)}${AddMeta.descOf(m.id)}",
                fontSize = 9.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8D96AB), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (on) {
            Text("✓ ON THIS TRACK", fontSize = 7.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                color = Color(0xFF9FE0B0))
        }
    }
}

private val FAMS_EFFECTS = listOf("All", "Dynamics", "EQ", "Space", "Amps", "Voices")
private val FAMS_INSTRUMENT = listOf("All", "Keys", "Guitar", "Winds", "Strings", "Synth")

/**
 * Instruments that left the FX window. Organ (6), Synth (14), Leads (15) and Sax (16) are now
 * real track instruments loaded from the soundfont window's Æther Instruments tab, not FX
 * entries. They keep their ids in FxRegistry (note routing and persistence still resolve them).
 */
private val AE_RETIRED_INSTRUMENT_IDS = setOf(6, 14, 15, 16, 17)

/**
 * `CAT2`, `ICK` and `INSTR` from the preview: per-effect family, description and icon.
 * Keeping the tables here means the sheet cannot drift from the reference.
 */
private object AddMeta {
    private val INSTR = setOf(5, 6, 7, 8, 9, 12, 14)
    private val SOURCES = setOf(6, 14)

    private val CAT2: Map<Int, Triple<String, String, String>> = mapOf(
        3 to Triple("comp", "Dynamics", "Controls the punch"),
        4 to Triple("max", "Dynamics", "Loud, clean, finished"),
        2 to Triple("eq", "EQ", "Shapes the tone"),
        1 to Triple("dly", "Space", "Echoes that follow the groove"),
        0 to Triple("rev", "Space", "Rooms, halls, deep space"),
        10 to Triple("amp", "Amps", "Tube amp · Cab · Mic"),
        11 to Triple("bas", "Amps", "Bass head · Cab · Graphic EQ"),
        5 to Triple("pno", "Keys", "Felt · Lid · Pedals"),
        6 to Triple("org", "Keys", "Drawbars · Rotary"),
        14 to Triple("syn", "Synth", "Oscillators · Filter · Motion"),
        7 to Triple("sax", "Winds", "Breath · Reed · Growl"),
        12 to Triple("brs", "Winds", "Mutes · Lips · Section"),
        8 to Triple("str", "Strings", "Bow · Section · Rosin"),
        9 to Triple("gtr", "Guitar", "Pick · Amp · Strum"),
        13 to Triple("voc", "Voices", "Vowels · Choir · Breath"),
    )

    fun familyOf(id: Int) = CAT2[id]?.second ?: "All"
    fun descOf(id: Int) = CAT2[id]?.third ?: ""

    fun prefix(id: Int) = when {
        SOURCES.contains(id) -> "Sound source · "
        INSTR.contains(id) -> "Shapes the soundfont · "
        else -> ""
    }

    fun iconRes(id: Int): Int = when (CAT2[id]?.first) {
        "rev" -> R.drawable.ic_fx_space
        "dly" -> R.drawable.ic_fx_echo
        "eq" -> R.drawable.ic_fx_tone
        "comp" -> R.drawable.ic_fx_grip
        "max" -> R.drawable.ic_fx_ceiling
        "pno" -> R.drawable.ic_fx_piano
        "org" -> R.drawable.ic_fx_organ
        "sax" -> R.drawable.ic_fx_sax
        "str" -> R.drawable.ic_fx_strings
        "gtr" -> R.drawable.ic_fx_guitar
        "amp" -> R.drawable.ic_fx_amp
        "bas" -> R.drawable.ic_fx_bass
        "brs" -> R.drawable.ic_fx_brass
        "voc" -> R.drawable.ic_fx_voices
        "syn" -> R.drawable.ic_fx_synth
        else -> R.drawable.ic_fx_tone
    }
}
