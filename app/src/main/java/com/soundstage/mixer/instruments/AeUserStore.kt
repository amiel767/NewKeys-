package com.soundstage.mixer.instruments

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

/**
 * User presets of the Æther instruments, persisted on disk.
 *
 * A preset is just the 31/32/21 normalised knob values plus a name, which is exactly
 * what [AePreset] already is, so a saved preset behaves like a factory one everywhere
 * else (load, tween, double-tap reset, name readback). One JSON array per instrument,
 * under `SharedPreferences("aether_instruments")`, key `user_<INSTRUMENT>`.
 *
 * Shape: `[{"n":"My B3","v":[0.5,1,...]}]`
 *
 * The store is deliberately dumb: it reads and writes, and never throws. A malformed
 * blob (older build, hand edit) is ignored and the built-ins still load, which is the
 * rule for every persistence path in this app.
 */
object AeUserStore {
    private const val PREFS = "aether_instruments"
    private const val TAG = "AeUserStore"

    /** Set by [initAetherInstruments]; null in unit tests, where everything stays in memory. */
    @Volatile var app: Context? = null

    /**
     * In-memory mirror, so a unit test without a Context still gets a working store.
     * A snapshot list, not a plain one: the open preset browser reads it, and saving a
     * preset has to recompose that list. A plain list would leave the UI stale.
     */
    private val memory = HashMap<AeInstrument, SnapshotStateList<AePreset>>()

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(i: AeInstrument) = "user_${i.name}"

    fun load(inst: AeInstrument): SnapshotStateList<AePreset> {
        memory[inst]?.let { return it }
        val list = mutableStateListOf<AePreset>()
        val raw = try {
            prefs()?.getString(key(inst), null)
        } catch (t: Throwable) {
            Log.e(TAG, "load(${inst.name}) failed: ${t.message}")
            null
        }
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (k in 0 until arr.length()) {
                    val o = arr.optJSONObject(k) ?: continue
                    val name = o.optString("n", "").ifBlank { continue }
                    val va = o.optJSONArray("v") ?: continue
                    val v = FloatArray(inst.paramCount)
                    for (j in 0 until minOf(va.length(), v.size)) v[j] = va.optDouble(j, 0.0).toFloat()
                    list.add(AePreset(name, USER_CATEGORY, v))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "parse(${inst.name}) failed: ${t.message}")
            }
        }
        memory[inst] = list
        return list
    }

    /** Appends a preset and persists. Returns its index inside the user list. */
    fun append(inst: AeInstrument, name: String, values: FloatArray): Int {
        val list = load(inst)
        list.add(AePreset(name, USER_CATEGORY, values.copyOf()))
        save(inst)
        return list.size - 1
    }

    fun delete(inst: AeInstrument, index: Int) {
        val list = load(inst)
        if (index in list.indices) { list.removeAt(index); save(inst) }
    }

    private fun save(inst: AeInstrument) {
        val list = load(inst)
        try {
            val arr = JSONArray()
            list.forEach { p ->
                val o = JSONObject()
                o.put("n", p.name)
                val va = JSONArray()
                p.values.forEach { va.put(it.toDouble()) }
                o.put("v", va)
                arr.put(o)
            }
            prefs()?.edit()?.putString(key(inst), arr.toString())?.apply()
        } catch (t: Throwable) {
            Log.e(TAG, "save(${inst.name}) failed: ${t.message}")
        }
    }

    /** Category every saved preset lands in; also the label shown in the list. */
    const val USER_CATEGORY = "User"

    /** Drops the in-memory mirror so a test can re-read what is actually on disk. */
    internal fun clearCache() = memory.clear()
}
