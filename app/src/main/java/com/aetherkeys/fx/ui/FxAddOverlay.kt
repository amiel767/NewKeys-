package com.aetherkeys.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Add overlay: two tabs (Effects / Instrument), family chips, search, grid. Tiles are sized from FxGeom. */
enum class FxAddTab { EFFECTS, INSTRUMENT }

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
    val visible = modules
        .filter { if (tab == FxAddTab.EFFECTS) it.kind == FxScreenKind.EFFECT else it.kind != FxScreenKind.EFFECT }
        .filter { family == "All" || it.type == family }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("EFFECTS  $effectsUsed/6", tab == FxAddTab.EFFECTS) { onTab(FxAddTab.EFFECTS) }
            TabChip("INSTRUMENT  1 per track", tab == FxAddTab.INSTRUMENT) { onTab(FxAddTab.INSTRUMENT) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val families = if (tab == FxAddTab.EFFECTS) listOf("All", "Dynamics", "EQ", "Space", "Amps", "Voices")
                           else listOf("All", "Keys", "Guitar", "Winds", "Strings", "Synth")
            families.forEach { f -> TabChip(f, family == f) { family = f } }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (FxGeom.ADD_TILE_W.dp)), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible) { m ->
                val on = isOn(m.id)
                Column(
                    modifier = Modifier
                        .size(FxGeom.ADD_TILE_W.dp, FxGeom.ADD_TILE_H.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E2535))
                        .border(1.dp, if (on) m.accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .clickable { onPick(m) }
                        .padding(10.dp),
                ) {
                    Text(m.name, fontSize = 14.sp, color = if (on) Color(0xFF8D96AB) else Color(0xFFE6EAF3))
                    Text(m.type, fontSize = 10.sp, color = Color(0xFF8D96AB))
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (selected) Color(0xFF0B0F1C) else Color(0xFFE6EAF3),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFF2F4FA) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
