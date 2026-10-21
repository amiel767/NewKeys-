package com.aetherkeys.fx.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aetherkeys.fx.FxRegistry

/**
 * Single source of truth for the FX window. Compose reads it; actions write it and push to [engine].
 * Lots are separate: effects (6 track slots, 8 on master) and one instrument per track (never on master).
 * Parameter arrays come from FxRegistry (the generated registry): size = number of engine params.
 * Convention: the LAST parameter of every module is the level (see FxBottomBar).
 */
class FxShellStore(
    val modules: Map<Int, EffectModule>,
    private val engine: FxEngine,
) {
    /** Currently edited strip: 0..7 tracks, 8 drum, 9 tonic pad, 10 drum-pad sampler, 15 master. */
    var strip by mutableStateOf(0)
    /** Id of the open panel, or null for the rack. */
    var openId by mutableStateOf<Int?>(null)
    var addOpen by mutableStateOf(false)
    var addTab by mutableStateOf(FxAddTab.EFFECTS)
    /** Short message shown in the window (refusals, replacements). Cleared by the UI after display. */
    var notice by mutableStateOf<String?>(null)

    private val slots = HashMap<Int, MutableList<Int?>>()
    private val instrument = HashMap<Int, Int?>()
    private val values = HashMap<Long, FloatArray>()
    private val bypass = HashMap<Long, Boolean>()
    private val abSnap = HashMap<Long, Pair<FloatArray, FloatArray>>()

    private fun key(strip: Int, id: Int) = (strip.toLong() shl 32) or id.toLong()

    fun slotsOf(strip: Int): MutableList<Int?> = slots.getOrPut(strip) { MutableList(if (strip == 15) 8 else 6) { null } }
    fun instrumentOf(strip: Int): Int? = instrument[strip]
    fun effectCount(strip: Int = this.strip) = slotsOf(strip).count { it != null }
    fun isOn(id: Int): Boolean = slotsOf(strip).contains(id) || instrument[strip] == id

    /** Current values of [id] on the current strip. Created from the registry defaults on first use. */
    fun valuesOf(id: Int): FloatArray = values.getOrPut(key(strip, id)) {
        FxRegistry.info(id).params.map { it.default }.toFloatArray()
    }

    fun isBypassed(id: Int) = bypass[key(strip, id)] ?: false

    fun addEffect(id: Int): Boolean {
        val m = modules[id] ?: return refuse("Unknown effect")
        if (m.kind != FxScreenKind.EFFECT) return refuse("${m.name} is an instrument")
        if (isOn(id)) return refuse("${m.name} is already on this track")
        val list = slotsOf(strip)
        val free = list.indexOfFirst { it == null }
        if (free < 0) return refuse("All effect slots are full")
        if (!engine.setEffect(strip, free, id)) return refuse("Engine refused ${m.name}")
        list[free] = id
        pushAll(id)
        return true
    }

    /** One instrument per track. Choosing one replaces the current one. Refused on master. */
    fun setInstrument(id: Int): Boolean {
        if (strip == 15) return refuse("Instruments are not available on master")
        val m = modules[id] ?: return refuse("Unknown instrument")
        if (m.kind == FxScreenKind.EFFECT) return refuse("${m.name} is an effect")
        val old = instrument[strip]
        if (!engine.setInstrument(strip, id)) return refuse("Engine refused ${m.name}")
        instrument[strip] = id
        if (old != null && old != id) notice = "${m.name} replaces ${modules[old]?.name ?: "previous"}"
        pushAll(id)
        return true
    }

    fun removeEffect(id: Int) {
        val list = slotsOf(strip)
        val slot = list.indexOf(id)
        if (slot >= 0) { engine.setEffect(strip, slot, -1); list[slot] = null }
        if (instrument[strip] == id) { engine.setInstrument(strip, -1); instrument[strip] = null }
    }

    fun setParam(id: Int, index: Int, value: Float) {
        val v = valuesOf(id)
        if (index !in v.indices) return
        v[index] = value.coerceIn(0f, 1f)
        pushParam(id, index)
    }

    fun toggleBypass(id: Int) {
        val now = !isBypassed(id)
        bypass[key(strip, id)] = now
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) engine.setBypass(strip, slot, now)
    }

    /** Applies preset [index] from the registry: updates local values and tells the engine. */
    fun applyPreset(id: Int, index: Int): Boolean {
        val preset = FxRegistry.info(id).presets.getOrNull(index) ?: return false
        val v = valuesOf(id)
        if (preset.values.size != v.size) return refuse("Preset size mismatch")
        preset.values.forEachIndexed { i, x -> v[i] = x }
        activePreset[key(strip, id)] = index
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) engine.applyPreset(strip, slot, index)
        pushAll(id)
        return true
    }

    /** Name of the preset last applied to [id] on this strip, or null when none was. */
    fun activePresetOf(id: Int): String? =
        activePreset[key(strip, id)]?.let { FxRegistry.info(id).presets.getOrNull(it)?.name }

    private val activePreset = HashMap<Long, Int>()

    /** A/B: first press stores the current state as A and copies it to B. Next presses swap. */
    fun toggleAB(id: Int) {
        val k = key(strip, id)
        val cur = valuesOf(id)
        val pair = abSnap[k] ?: (cur.copyOf() to cur.copyOf()).also { abSnap[k] = it }
        val (a, b) = pair
        // store the live state into whichever side is active, then load the other side
        val activeIsA = abActive[k] ?: true
        if (activeIsA) { cur.copyInto(a) } else { cur.copyInto(b) }
        val target = if (activeIsA) b else a
        target.copyInto(cur)
        abActive[k] = !activeIsA
        pushAll(id)
    }
    private val abActive = HashMap<Long, Boolean>()
    fun abLabel(id: Int): String = if (abActive[key(strip, id)] == false) "B" else "A"

    /**
     * Surprise me: random value for every parameter except the level and any locked index.
     * Ranges are the registry ranges (0..1 normalised). Locks come from the UI.
     */
    fun surprise(id: Int, locked: Set<Int> = emptySet()) {
        val v = valuesOf(id)
        val last = v.size - 1
        for (i in v.indices) {
            if (i == last || i in locked) continue
            v[i] = kotlin.random.Random.nextFloat()
        }
        pushAll(id)
    }

    private fun pushAll(id: Int) { valuesOf(id).indices.forEach { pushParam(id, it) } }

    private fun pushParam(id: Int, index: Int) {
        val v = valuesOf(id)
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) engine.setParam(strip, slot, index, v[index])
        else if (instrument[strip] == id) engine.setInstrumentParam(strip, index, v[index])
    }

    private fun refuse(msg: String): Boolean { notice = msg; return false }
}
