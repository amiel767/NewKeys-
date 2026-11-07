package com.soundstage.mixer.fx

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

/**
 * Presets utilisateur des effets FX, persistés sur disque.
 *
 * Un preset n'est qu'un nom et la liste des valeurs normalisées des réglages, ce que
 * [com.aetherkeys.fx.FxDesign] décrit déjà. Un preset enregistré se comporte donc comme
 * un preset d'usine partout ailleurs (chargement, sélection, lecture du nom). Un tableau
 * JSON par effet, dans `SharedPreferences("aether_fx")`, clé `fx_user_<effectId>` — la même
 * préférence que [FxPersistence], dont les clés sont `fx_strip_*`, donc aucune collision.
 *
 * Forme : `[{"n":"Mon Hall","v":[0.5,1,...]}]`
 *
 * Le store lit et écrit, et ne lève jamais : un blob malformé (build plus ancienne,
 * édition à la main) est ignoré et les presets d'usine se chargent quand même, la règle
 * de tous les chemins de persistance de l'app.
 */
object FxUserStore {
    private const val PREFS = "aether_fx"
    private const val TAG = "FxUserStore"

    /** Posé au démarrage ; null en test unitaire, où tout reste en mémoire. */
    @Volatile var app: Context? = null

    /**
     * Miroir en mémoire, pour qu'un test sans Context ait quand même un store qui
     * marche. Une liste snapshot et non une liste ordinaire : le navigateur de presets
     * la lit, et enregistrer un preset doit recomposer cette liste.
     */
    private val memory = HashMap<Int, SnapshotStateList<FxUserPreset>>()

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(id: Int) = "fx_user_$id"

    fun load(id: Int): SnapshotStateList<FxUserPreset> {
        memory[id]?.let { return it }
        val list = mutableStateListOf<FxUserPreset>()
        val raw = try {
            prefs()?.getString(key(id), null)
        } catch (t: Throwable) {
            Log.e(TAG, "load($id) failed: ${t.message}")
            null
        }
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (k in 0 until arr.length()) {
                    val o = arr.optJSONObject(k) ?: continue
                    val name = o.optString("n", "").ifBlank { continue }
                    val va = o.optJSONArray("v") ?: continue
                    val v = List(va.length()) { va.optDouble(it, 0.0).toFloat() }
                    list.add(FxUserPreset(name, v))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "parse($id) failed: ${t.message}")
            }
        }
        memory[id] = list
        return list
    }

    /** Ajoute ou remplace un preset du même nom, puis persiste. */
    fun put(id: Int, name: String, values: List<Float>) {
        val list = load(id)
        val clean = name.trim().ifEmpty { return }
        val at = list.indexOfFirst { it.name == clean }
        if (at >= 0) list[at] = FxUserPreset(clean, values.toList()) else list.add(FxUserPreset(clean, values.toList()))
        save(id)
    }

    fun delete(id: Int, name: String) {
        val list = load(id)
        val at = list.indexOfFirst { it.name == name }
        if (at >= 0) { list.removeAt(at); save(id) }
    }

    private fun save(id: Int) {
        val list = load(id)
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
            prefs()?.edit()?.putString(key(id), arr.toString())?.apply()
        } catch (t: Throwable) {
            Log.e(TAG, "save($id) failed: ${t.message}")
        }
    }

    /** Vide le miroir en mémoire pour qu'un test relise ce qui est réellement sur disque. */
    internal fun clearCache() = memory.clear()
}

/** Un preset utilisateur d'un effet : un nom, et les valeurs normalisées de ses réglages. */
data class FxUserPreset(val name: String, val values: List<Float>)
