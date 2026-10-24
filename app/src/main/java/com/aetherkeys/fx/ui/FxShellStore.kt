package com.aetherkeys.fx.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
    /** Strip this store edits. Passed in so the first frame never renders strip 0 by mistake. */
    initialStrip: Int = 0,
) {
    /** Currently edited strip: 0..7 tracks, 8 drum, 9 tonic pad, 10 drum-pad sampler, 15 master. */
    var strip by mutableStateOf(initialStrip)
    /** Id of the open panel, or null for the rack. */
    var openId by mutableStateOf<Int?>(null)
    var addOpen by mutableStateOf(false)
    var addTab by mutableStateOf(FxAddTab.EFFECTS)
    /** Active `SPG` page of the open panel. Only the synth uses more than one. */
    var page by mutableStateOf(0)
    /** `#ed`: the soundfont edit panel, toggled by the Edit chip on effects 5/7/8/9/12. */
    var editOpen by mutableStateOf(false)
    /** Short message shown in the window (refusals, replacements). Cleared by the UI after display. */
    var notice by mutableStateOf<String?>(null)

    // Compose observes these: a plain HashMap made `addEffect` invisible. The rack
    // read `slotsOf(strip)` once and nothing invalidated it, so a newly added effect
    // never appeared until the window was closed and reopened. Snapshot state makes
    // every read of the rack recompose when a slot or the instrument changes.
    private val slots = mutableStateMapOf<Int, SnapshotStateList<Int?>>()
    private val instrument = mutableStateMapOf<Int, Int?>()
    // Parameter arrays are mutated in place by the UI, and an in-place FloatArray
    // write is invisible to Compose. Every mutation therefore goes through [edit],
    // which stores a fresh array so the readers of [valuesOf] recompose. The same
    // applies to the A/B snapshots, whose arrays are copied on write too.
    private val values = mutableStateMapOf<Long, FloatArray>()
    private val bypass = mutableStateMapOf<Long, Boolean>()
    private val abSnap = mutableStateMapOf<Long, Pair<FloatArray, FloatArray>>()

    private fun key(strip: Int, id: Int) = (strip.toLong() shl 32) or id.toLong()

    /**
     * Copy-on-write mutation of the parameter array of [id] on [s]. Compose only
     * sees a change when the map entry is replaced, so the block works on a copy.
     */
    private inline fun edit(s: Int, id: Int, block: (FloatArray) -> Unit) {
        val k = key(s, id)
        val next = (values[k] ?: FxRegistry.info(id).params.map { it.default }.toFloatArray()).copyOf()
        block(next)
        values[k] = next
    }

    fun slotsOf(strip: Int): SnapshotStateList<Int?> = slots.getOrPut(strip) {
        mutableStateListOf<Int?>().apply { repeat(if (strip == 15) 8 else 6) { add(null) } }
    }
    fun instrumentOf(strip: Int): Int? = instrument[strip]
    fun effectCount(strip: Int = this.strip) = slotsOf(strip).count { it != null }
    fun isOn(id: Int): Boolean = slotsOf(strip).contains(id) || instrument[strip] == id

    /** Current values of [id] on the current strip. Created from the registry defaults on first use. */
    fun valuesOf(id: Int): FloatArray = valuesOf(strip, id)

    private fun valuesOf(s: Int, id: Int): FloatArray = values.getOrPut(key(s, id)) {
        FxRegistry.info(id).params.map { it.default }.toFloatArray()
    }

    /**
     * Fills the local state of [s] from an already-restored source *without*
     * pushing to the engine. Used when the shell adopts an existing strip: the
     * engine is the authority at that point, re-pushing would only replay it.
     */
    fun seed(s: Int, ids: List<Int>, params: List<List<Float>>, byp: List<Boolean>, inst: Int?) {
        val list = slotsOf(s)
        ids.forEachIndexed { i, id -> if (i < list.size) list[i] = id.takeIf { it >= 0 } }
        ids.forEachIndexed { i, id ->
            if (id >= 0) {
                val k = key(s, id)
                val v = (values[k] ?: FxRegistry.info(id).params.map { it.default }.toFloatArray()).copyOf()
                params.getOrNull(i)?.forEachIndexed { j, x -> if (j < v.size) v[j] = x }
                values[k] = v
                bypass[k] = byp.getOrNull(i) ?: false
            }
        }
        instrument[s] = inst
    }

    fun isBypassed(id: Int) = bypass[key(strip, id)] ?: false

    fun addEffect(id: Int): Boolean {
        val m = modules[id] ?: return refuse("Unknown effect")
        if (m.kind != FxScreenKind.EFFECT) return refuse("${m.name} is an instrument")
        if (isOn(id)) return refuse("${m.name} is already on this track")
        val list = slotsOf(strip)
        val free = list.indexOfFirst { it == null }
        if (free < 0) return refuse("All effect slots are full")
        if (!guardBool("add effect") { engine.setEffect(strip, free, id) }) return refuse("Engine refused ${m.name}")
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
        if (!guardBool("instrument") { engine.setInstrument(strip, id) }) return refuse("Engine refused ${m.name}")
        instrument[strip] = id
        if (old != null && old != id) notice = "${m.name} replaces ${modules[old]?.name ?: "previous"}"
        pushAll(id)
        return true
    }

    fun removeEffect(id: Int) {
        val list = slotsOf(strip)
        val slot = list.indexOf(id)
        if (slot >= 0) { guard("remove effect") { engine.setEffect(strip, slot, -1) }; list[slot] = null }
        if (instrument[strip] == id) { guard("remove instrument") { engine.setInstrument(strip, -1) }; instrument[strip] = null }
    }

    fun setParam(id: Int, index: Int, value: Float) {
        val v = valuesOf(id)
        if (index !in v.indices) return
        edit(strip, id) { it[index] = value.coerceIn(0f, 1f) }
        pushParam(id, index)
    }

    fun toggleBypass(id: Int) {
        val now = !isBypassed(id)
        bypass[key(strip, id)] = now
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) guard("bypass") { engine.setBypass(strip, slot, now) }
    }

    /** Applies preset [index] from the registry: updates local values and tells the engine. */
    fun applyPreset(id: Int, index: Int): Boolean {
        val preset = FxRegistry.info(id).presets.getOrNull(index) ?: return false
        val v = valuesOf(id)
        if (preset.values.size != v.size) return refuse("Preset size mismatch")
        edit(strip, id) { arr -> preset.values.forEachIndexed { i, x -> arr[i] = x } }
        activePreset[key(strip, id)] = index
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) guard("preset") { engine.applyPreset(strip, slot, index) }
        pushAll(id)
        return true
    }

    /** Name of the preset last applied to [id] on this strip, or null when none was. */
    fun activePresetOf(id: Int): String? =
        activePreset[key(strip, id)]?.let { FxRegistry.info(id).presets.getOrNull(it)?.name }

    private val activePreset = mutableStateMapOf<Long, Int>()

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
        // Copy-on-write so the readers of `valuesOf` recompose on the A/B switch.
        values[k] = target.copyOf()
        abActive[k] = !activeIsA
        pushAll(id)
    }
    private val abActive = mutableStateMapOf<Long, Boolean>()
    fun abLabel(id: Int): String = if (abActive[key(strip, id)] == false) "B" else "A"

    /**
     * Surprise me: random value for every parameter except the level and any locked index.
     * Ranges are the registry ranges (0..1 normalised). Locks come from the UI.
     */
    fun surprise(id: Int, locked: Set<Int> = emptySet()) {
        val v = valuesOf(id)
        val last = v.size - 1
        edit(strip, id) { arr ->
            for (i in arr.indices) {
                if (i == last || i in locked) continue
                arr[i] = kotlin.random.Random.nextFloat()
            }
        }
        pushAll(id)
    }

    private fun pushAll(id: Int) { valuesOf(id).indices.forEach { pushParam(id, it) } }

    private fun pushParam(id: Int, index: Int) {
        val v = valuesOf(id)
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) guard("param") { engine.setParam(strip, slot, index, v[index]) }
        else if (instrument[strip] == id) guard("param") { engine.setInstrumentParam(strip, index, v[index]) }
    }

    private fun refuse(msg: String): Boolean { notice = msg; return false }

    /**
     * Every engine call goes through here. The engine is a native bridge: a
     * refused call or a missing library raises instead of returning, and an
     * exception thrown from a Compose event handler tears the window down
     * without a way back. Folding it into [notice] keeps the UI alive and tells
     * the user what did not apply.
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            notice = "$what failed: ${e.message ?: e::class.simpleName}"
        }
    }

    /** [guard] for the engine calls that report success with a Boolean. */
    private inline fun guardBool(what: String, block: () -> Boolean): Boolean = try {
        block()
    } catch (e: Throwable) {
        notice = "$what failed: ${e.message ?: e::class.simpleName}"
        false
    }
}
