package com.example.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppStatePersistence:
 * Saves and restores complete Mixer & App state (Track volumes, Pans, loaded SoundFonts,
 * patch names, active scene, BPM, transpose, theme, drum assignments, etc.)
 * into persistent SharedPreferences storage so that whenever the app restarts,
 * the user immediately resumes their exact last session and activity.
 */
class AppStatePersistence(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("livekeys_mixer_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AppStatePersistence"
        private const val KEY_THEME = "current_theme"
        private const val KEY_BPM = "bpm"
        private const val KEY_TRANSPOSE = "transpose"
        private const val KEY_OCTAVE = "octave"
        private const val KEY_ACTIVE_SCENE_ID = "active_scene_id"
        private const val KEY_SOUNDGOODIZER_AMOUNT = "soundgoodizer_amount"
        private const val KEY_SOUNDGOODIZER_MODE = "soundgoodizer_mode"
        private const val KEY_MASTER_PUNCH = "master_punch"
        private const val KEY_SPATIAL_WIDENER = "spatial_widener"
        private const val KEY_MASTER_VOLUME = "master_volume"
        private const val KEY_TRACKS_JSON = "tracks_json"
        private const val KEY_DRUM_PADS_JSON = "drum_pads_json"
        private const val KEY_AUDIO_SLOTS_JSON = "audio_slots_json"
        private const val KEY_ACTIVE_SF2_TRACK_ID = "active_sf2_track_id"
        private const val KEY_LAST_ACTIVITY = "last_activity"
    }

    data class SavedTrackData(
        val id: Int,
        val name: String,
        val isEnabled: Boolean,
        val volume: Float,
        val pan: Float,
        val soundfontName: String,
        val patchName: String,
        val bank: Int,
        val program: Int,
        val reverbPreset: String,
        val reverbMix: Float
    )

    data class SavedAudioSlotData(
        val slotId: Int,
        val soundFontPath: String?,
        val bank: Int,
        val preset: Int,
        val patchName: String?,
        val volume: Float,
        val pan: Float
    )

    data class SavedDrumData(
        val id: Int,
        val label: String,
        val soundType: String,
        val sampleFileName: String,
        val sf2Note: String,
        val styleName: String
    )

    data class RestoredAppState(
        val themeName: String?,
        val bpm: Int?,
        val transpose: Int?,
        val octave: Int?,
        val activeSceneId: String?,
        val soundGoodizerAmount: Float?,
        val soundGoodizerMode: String?,
        val masterPunch: Float?,
        val spatialWidener: Float?,
        val masterVolume: Float?,
        val tracks: List<SavedTrackData>,
        val audioSlots: List<SavedAudioSlotData>,
        val drumPads: List<SavedDrumData>,
        val activeSf2TrackId: Int?,
        val lastActivity: String?
    )

    fun saveAppState(
        currentTheme: AppTheme,
        bpm: Int,
        transpose: Int,
        octave: Int,
        activeSceneId: String,
        soundGoodizerAmount: Float,
        soundGoodizerMode: SoundGoodizerMode,
        masterPunch: Float,
        spatialWidener: Float,
        masterVolume: Float,
        tracks: List<TrackChannel>,
        audioSlots: List<AudioSlot>,
        drumPads: List<DrumPadItem>,
        activeSf2TrackId: Int,
        lastActivity: String = "mixer"
    ) {
        try {
            val tracksArray = JSONArray()
            tracks.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("isEnabled", t.isEnabled)
                    put("volume", t.volume.toDouble())
                    put("pan", t.pan.toDouble())
                    put("soundfontName", t.soundfontName)
                    put("patchName", t.patchName)
                    put("bank", t.bank)
                    put("program", t.program)
                    put("reverbPreset", t.reverbPreset)
                    put("reverbMix", t.reverbMix.toDouble())
                }
                tracksArray.put(obj)
            }

            val slotsArray = JSONArray()
            audioSlots.forEach { s ->
                val obj = JSONObject().apply {
                    put("slotId", s.slotId)
                    put("soundFontPath", s.soundFontPath ?: "")
                    put("bank", s.bank)
                    put("preset", s.preset)
                    put("patchName", s.patchName ?: "")
                    put("volume", s.volume.toDouble())
                    put("pan", s.pan.toDouble())
                }
                slotsArray.put(obj)
            }

            val drumArray = JSONArray()
            drumPads.forEach { d ->
                val obj = JSONObject().apply {
                    put("id", d.id)
                    put("label", d.label)
                    put("soundType", d.soundType.name)
                    put("sampleFileName", d.sampleFileName)
                    put("sf2Note", d.sf2Note)
                    put("styleName", d.colorStyle.name)
                }
                drumArray.put(obj)
            }

            prefs.edit().apply {
                putString(KEY_THEME, currentTheme.name)
                putInt(KEY_BPM, bpm)
                putInt(KEY_TRANSPOSE, transpose)
                putInt(KEY_OCTAVE, octave)
                putString(KEY_ACTIVE_SCENE_ID, activeSceneId)
                putFloat(KEY_SOUNDGOODIZER_AMOUNT, soundGoodizerAmount)
                putString(KEY_SOUNDGOODIZER_MODE, soundGoodizerMode.name)
                putFloat(KEY_MASTER_PUNCH, masterPunch)
                putFloat(KEY_SPATIAL_WIDENER, spatialWidener)
                putFloat(KEY_MASTER_VOLUME, masterVolume)
                putString(KEY_TRACKS_JSON, tracksArray.toString())
                putString(KEY_AUDIO_SLOTS_JSON, slotsArray.toString())
                putString(KEY_DRUM_PADS_JSON, drumArray.toString())
                putInt(KEY_ACTIVE_SF2_TRACK_ID, activeSf2TrackId)
                putString(KEY_LAST_ACTIVITY, lastActivity)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving app state: ${e.message}")
        }
    }

    fun loadAppState(): RestoredAppState? {
        try {
            if (!prefs.contains(KEY_BPM) && !prefs.contains(KEY_TRACKS_JSON)) {
                return null
            }

            val themeName = prefs.getString(KEY_THEME, null)
            val bpm = if (prefs.contains(KEY_BPM)) prefs.getInt(KEY_BPM, 120) else null
            val transpose = if (prefs.contains(KEY_TRANSPOSE)) prefs.getInt(KEY_TRANSPOSE, 0) else null
            val octave = if (prefs.contains(KEY_OCTAVE)) prefs.getInt(KEY_OCTAVE, 0) else null
            val activeSceneId = prefs.getString(KEY_ACTIVE_SCENE_ID, null)
            val soundGoodizerAmount = if (prefs.contains(KEY_SOUNDGOODIZER_AMOUNT)) prefs.getFloat(KEY_SOUNDGOODIZER_AMOUNT, 0.45f) else null
            val soundGoodizerMode = prefs.getString(KEY_SOUNDGOODIZER_MODE, null)
            val masterPunch = if (prefs.contains(KEY_MASTER_PUNCH)) prefs.getFloat(KEY_MASTER_PUNCH, 0.55f) else null
            val spatialWidener = if (prefs.contains(KEY_SPATIAL_WIDENER)) prefs.getFloat(KEY_SPATIAL_WIDENER, 0.38f) else null
            val masterVolume = if (prefs.contains(KEY_MASTER_VOLUME)) prefs.getFloat(KEY_MASTER_VOLUME, 0.70f) else null
            val activeSf2TrackId = if (prefs.contains(KEY_ACTIVE_SF2_TRACK_ID)) prefs.getInt(KEY_ACTIVE_SF2_TRACK_ID, 1) else null
            val lastActivity = prefs.getString(KEY_LAST_ACTIVITY, null)

            val tracksList = mutableListOf<SavedTrackData>()
            val tracksJson = prefs.getString(KEY_TRACKS_JSON, null)
            if (tracksJson != null) {
                val array = JSONArray(tracksJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    tracksList.add(
                        SavedTrackData(
                            id = obj.optInt("id", i + 1),
                            name = obj.optString("name", "Piste ${i + 1}"),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            volume = obj.optDouble("volume", 0.65).toFloat(),
                            pan = obj.optDouble("pan", 0.0).toFloat(),
                            soundfontName = obj.optString("soundfontName", ""),
                            patchName = obj.optString("patchName", "-"),
                            bank = obj.optInt("bank", 0),
                            program = obj.optInt("program", 0),
                            reverbPreset = obj.optString("reverbPreset", "Concert Hall"),
                            reverbMix = obj.optDouble("reverbMix", 0.20).toFloat()
                        )
                    )
                }
            }

            val slotsList = mutableListOf<SavedAudioSlotData>()
            val slotsJson = prefs.getString(KEY_AUDIO_SLOTS_JSON, null)
            if (slotsJson != null) {
                val array = JSONArray(slotsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    slotsList.add(
                        SavedAudioSlotData(
                            slotId = obj.optInt("slotId", i),
                            soundFontPath = obj.optString("soundFontPath", "").let { if (it.isEmpty()) null else it },
                            bank = obj.optInt("bank", 0),
                            preset = obj.optInt("preset", 0),
                            patchName = obj.optString("patchName", null),
                            volume = obj.optDouble("volume", 0.8).toFloat(),
                            pan = obj.optDouble("pan", 0.0).toFloat()
                        )
                    )
                }
            }

            val drumList = mutableListOf<SavedDrumData>()
            val drumJson = prefs.getString(KEY_DRUM_PADS_JSON, null)
            if (drumJson != null) {
                val array = JSONArray(drumJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    drumList.add(
                        SavedDrumData(
                            id = obj.optInt("id", i + 1),
                            label = obj.optString("label", ""),
                            soundType = obj.optString("soundType", "SAMPLE"),
                            sampleFileName = obj.optString("sampleFileName", "sample_${i + 1}.wav"),
                            sf2Note = obj.optString("sf2Note", "C2"),
                            styleName = obj.optString("styleName", "GRADIENT_CYAN")
                        )
                    )
                }
            }

            return RestoredAppState(
                themeName = themeName,
                bpm = bpm,
                transpose = transpose,
                octave = octave,
                activeSceneId = activeSceneId,
                soundGoodizerAmount = soundGoodizerAmount,
                soundGoodizerMode = soundGoodizerMode,
                masterPunch = masterPunch,
                spatialWidener = spatialWidener,
                masterVolume = masterVolume,
                tracks = tracksList,
                audioSlots = slotsList,
                drumPads = drumList,
                activeSf2TrackId = activeSf2TrackId,
                lastActivity = lastActivity
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app state: ${e.message}")
            return null
        }
    }
}
