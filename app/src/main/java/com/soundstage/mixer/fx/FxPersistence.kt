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

    /** Snapshot of one strip, kept free of Compose types so it can be tested. */
    data class Strip(
        val instrumentId: Int,
        val instrumentParams: List<Float>,
        val instrumentPreset: String?,
        val effects: List<Slot?>,
        val abA: List<Float>?,
        val abB: List<Float>?,
        val abCur: Int,
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
            )
        }.getOrNull()
    }

    private fun floats(arr: JSONArray?): List<Float> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
    }

    fun save(context: Context, strip: Int, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREFIX + strip, json).apply()
    }

    fun load(context: Context, strip: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + strip, null)

    /** All persisted strips, used to restore everything at startup. */
    fun allStrips(context: Context): List<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { it.removePrefix(KEY_PREFIX).toIntOrNull() }
}
