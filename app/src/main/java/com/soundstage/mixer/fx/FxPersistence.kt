package com.soundstage.mixer.fx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * FX state persistence, one JSON blob per strip, as specified in the FX package
 * (doc 01): a stable-key object holding the instrument, the effect slots and the
 * A/B pair. Parameters are always stored normalized 0..1.
 *
 * Shape:
 * ```json
 * { "track": 2,
 *   "instrument": {"id": 7, "preset": "Smooth", "params": [0.55, 0.4], "bypass": false},
 *   "effects": [ {"id": 0, "preset": "Hall", "params": [...], "bypass": false}, null, ... ],
 *   "ab": {"a": [...], "b": [...], "cur": "a"} }
 * ```
 * Master only stores `effects` (8 entries).
 */
object FxPersistence {
    private const val PREFS = "aether_fx"
    private const val KEY_PREFIX = "fx_strip_"

    /**
     * A patch (or a setlist's own FX) owns its strips. Keys used to be
     * `fx_strip_<strip>` alone, i.e. ONE rack shared by every patch of every
     * setlist: switching patch left the previous patch's effects installed on
     * the engine and the new patch showed the same rack. The key now carries the
     * owning scope (`fx_strip_<scope>_<strip>`), so two patches never overlap.
     * An empty scope keeps the legacy key, which is what a build with no patch
     * loaded still writes and what an older install is migrated from.
     */
    private fun key(scope: String, strip: Int): String =
        if (scope.isEmpty()) "$KEY_PREFIX$strip" else "$KEY_PREFIX$scope$SEP$strip"

    private const val SEP = "_s"

    /** True when [k] belongs to [scope] (and not to a longer scope with the same prefix). */
    private fun belongsTo(k: String, scope: String): Boolean {
        if (scope.isEmpty()) return k.matches(Regex("^$KEY_PREFIX\\d+$"))
        val wanted = "$KEY_PREFIX$scope$SEP"
        if (!k.startsWith(wanted)) return false
        return k.removePrefix(wanted).toIntOrNull() != null
    }

    /** Snapshot of one strip, kept free of Compose types so it can be tested. */
    data class Strip(
        val instrumentId: Int,
        val instrumentParams: List<Float>,
        val instrumentPreset: String?,
        val effects: List<Slot?>,
        val abA: List<Float>?,
        val abB: List<Float>?,
        val abCur: Int,
        /** The MIDI slot (arpeggiator). Null when the track has none. */
        val arp: Arp? = null,
    )

    /**
     * The arpeggiator, which is a track's MIDI slot.
     *
     * It is stored here rather than in `effects` because it is NOT a chain
     * effect: the engine has no effect id for it. What persists is the whole
     * setting of the card — the grid, the tempo — plus whether the card is
     * present and engaged. Without [present] a removed card would come back on
     * the next launch; without [engaged] a bypassed one would silently re-arm.
     */
    data class Arp(
        val sub: Int = 2,
        val style: Int = 0,
        val steps: Int = 16,
        val bpm: Int = 120,
        val swing: Int = 12,
        val on: List<Boolean> = List(16) { true },
        val vel: List<Float> = List(16) { 1f },
        val present: Boolean = true,
        val engaged: Boolean = true,
    )

    data class Slot(val id: Int, val preset: String?, val params: List<Float>, val bypass: Boolean)

    fun toJson(strip: Int, state: Strip): String {
        val root = JSONObject()
        root.put("track", strip)
        if (state.instrumentId >= 0) {
            root.put("instrument", JSONObject().apply {
                put("id", state.instrumentId)
                state.instrumentPreset?.let { put("preset", it) }
                put("params", JSONArray(state.instrumentParams.map { it.toDouble() }))
            })
        }
        root.put("effects", JSONArray().apply {
            state.effects.forEach { slot ->
                if (slot == null) put(JSONObject.NULL)
                else put(JSONObject().apply {
                    put("id", slot.id)
                    slot.preset?.let { put("preset", it) }
                    put("params", JSONArray(slot.params.map { it.toDouble() }))
                    put("bypass", slot.bypass)
                })
            }
        })
        if (state.arp != null) {
            root.put("arp", JSONObject().apply {
                put("present", state.arp.present)
                put("engaged", state.arp.engaged)
                put("sub", state.arp.sub)
                put("style", state.arp.style)
                put("steps", state.arp.steps)
                put("bpm", state.arp.bpm)
                put("swing", state.arp.swing)
                put("on", JSONArray(state.arp.on.map { it }))
                put("vel", JSONArray(state.arp.vel.map { it.toDouble() }))
            })
        }
        if (state.abA != null && state.abB != null) {
            root.put("ab", JSONObject().apply {
                put("a", JSONArray(state.abA.map { it.toDouble() }))
                put("b", JSONArray(state.abB.map { it.toDouble() }))
                put("cur", if (state.abCur == 0) "a" else "b")
            })
        }
        return root.toString()
    }

    fun fromJson(json: String?, slotCount: Int): Strip? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val inst = root.optJSONObject("instrument")
            val effectsArr = root.optJSONArray("effects")
            val effects = ArrayList<Slot?>(slotCount)
            for (i in 0 until slotCount) {
                val o = effectsArr?.optJSONObject(i)
                effects.add(
                    if (o == null || o.isNull("id")) null
                    else Slot(
                        id = o.optInt("id", -1),
                        preset = o.optString("preset").ifBlank { null },
                        params = floats(o.optJSONArray("params")),
                        bypass = o.optBoolean("bypass", false),
                    )
                )
            }
            val ab = root.optJSONObject("ab")
            Strip(
                instrumentId = inst?.optInt("id", -1) ?: -1,
                instrumentParams = floats(inst?.optJSONArray("params")),
                instrumentPreset = inst?.optString("preset")?.ifBlank { null },
                effects = effects,
                abA = ab?.let { floats(it.optJSONArray("a")).ifEmpty { null } },
                abB = ab?.let { floats(it.optJSONArray("b")).ifEmpty { null } },
                abCur = if (ab?.optString("cur") == "b") 1 else 0,
                arp = root.optJSONObject("arp")?.let { a ->
                    Arp(
                        sub = a.optInt("sub", 2),
                        style = a.optInt("style", 0),
                        steps = a.optInt("steps", 16),
                        bpm = a.optInt("bpm", 120),
                        swing = a.optInt("swing", 12),
                        on = bools(a.optJSONArray("on")),
                        vel = floats(a.optJSONArray("vel")).ifEmpty { List(16) { 1f } },
                        present = a.optBoolean("present", true),
                        engaged = a.optBoolean("engaged", true),
                    )
                },
            )
        }.getOrNull()
    }

    private fun floats(arr: JSONArray?): List<Float> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
    }

    private fun bools(arr: JSONArray?): List<Boolean> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optBoolean(it, true) }
    }

    fun save(context: Context, strip: Int, json: String) = save(context, "", strip, json)

    fun save(context: Context, scope: String, strip: Int, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(scope, strip), json).apply()
    }

    fun load(context: Context, strip: Int): String? = load(context, "", strip)

    fun load(context: Context, scope: String, strip: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(scope, strip), null)

    /**
     * Reads [scope]'s strip, adopting the legacy un-scoped rack the first time a scope
     * asks for it. Before scoping, every patch shared `fx_strip_<strip>`; on the first
     * open after the upgrade that shared rack is handed to the active patch and the
     * legacy key is dropped, so the other patches start empty instead of all inheriting
     * it (which is the overlap this change removes). After the first adoption no scope
     * ever sees a legacy key again.
     */
    fun loadMigrating(context: Context, scope: String, strip: Int): String? {
        if (scope.isEmpty()) return load(context, "", strip)
        load(context, scope, strip)?.let { return it }
        val legacy = load(context, "", strip) ?: return null
        save(context, scope, strip, legacy)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key("", strip)).apply()
        return legacy
    }

    /** All persisted strips of [scope], used to restore everything at startup. */
    fun allStrips(context: Context): List<Int> = allStrips(context, "")

    fun allStrips(context: Context, scope: String): List<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .filter { belongsTo(it, scope) }
            .mapNotNull { it.removePrefix("$KEY_PREFIX$scope$SEP").toIntOrNull() }

    /** Drops every strip of [scope]; the legacy keys when it is empty. */
    fun clear(context: Context, scope: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val doomed = prefs.all.keys.filter { if (scope.isEmpty()) it.matches(Regex("^$KEY_PREFIX\\d+$")) else belongsTo(it, scope) }
        if (doomed.isEmpty()) return
        prefs.edit().apply { doomed.forEach { remove(it) } }.apply()
    }
}
