package com.soundstage.mixer.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for a track's Æther instrument, shared by the snapshot and app-state blobs.
 *
 * The enum name is written rather than its ordinal: the ordinal is an implementation
 * detail of `AeInstrument` and would silently mean a different instrument if the enum
 * were reordered. `null` is a valid value (the track carries a soundfont, or nothing)
 * and must be written as JSON `null` so a reader can tell "no instrument" from
 * "an older file that predates this field".
 */
object AetherJson {

    fun toJson(state: AetherTrackState?): Any = if (state == null) JSONObject.NULL else JSONObject().apply {
        put("instrument", state.instrument)
        put("preset", state.preset)
        put("values", JSONArray().apply { state.values.forEach { put(it.toDouble()) } })
    }

    /** Reads the Æther block, or null when absent/blank. */
    fun fromJson(o: JSONObject?): AetherTrackState? {
        if (o == null) return null
        val name = o.optString("instrument", "")
        if (name.isEmpty()) return null
        val arr = o.optJSONArray("values") ?: return null
        val values = ArrayList<Float>(arr.length())
        for (i in 0 until arr.length()) values.add(arr.optDouble(i, 0.5).toFloat())
        return AetherTrackState(name, o.optInt("preset", 0), values)
    }
}
