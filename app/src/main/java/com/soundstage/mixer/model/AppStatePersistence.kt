package com.soundstage.mixer.model

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
        private const val KEY_FX_PARAMETERS_JSON = "fx_parameters_json"
        private const val KEY_ACTIVE_SF2_TRACK_ID = "active_sf2_track_id"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        private const val KEY_TONIC_BRIGHTNESS = "tonic_brightness"
        private const val KEY_TONIC_SHIMMER = "tonic_shimmer"
        private const val KEY_DRUM_REVERB = "drum_reverb"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SELECTED_SCALE_MODE = "selected_scale_mode"
        private const val KEY_SNAPSHOTS_JSON = "snapshots_json"
        private const val KEY_ACTIVE_SNAPSHOT_SLOT = "active_snapshot_slot"
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
        val reverbMix: Float,
        val velocityCurve: Float = 0.5f,
        val splitNoteMin: Int = 24,
        val splitNoteMax: Int = 108
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
        val sampleFilePath: String = "",
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
        val fxParameters: Map<Int, FxParameters> = emptyMap(),
        val activeSf2TrackId: Int?,
        val lastActivity: String?,
        val tonicBrightness: Float? = null,
        val tonicShimmer: Float? = null,
        val drumReverb: Float? = null,
        val keepScreenOn: Boolean? = null,
        val selectedScaleMode: String? = null,
        val snapshots: Map<String, SubSceneSnapshot> = emptyMap(),
        val activeSnapshotSlot: String? = null
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
        fxParameters: Map<Int, FxParameters> = emptyMap(),
        activeSf2TrackId: Int,
        lastActivity: String = "mixer",
        tonicBrightness: Float = 0.70f,
        tonicShimmer: Float = 0.15f,
        drumReverb: Float = 0.24f,
        keepScreenOn: Boolean = true,
        selectedScaleMode: String = "Majeur",
        snapshots: Map<String, SubSceneSnapshot> = emptyMap(),
        activeSnapshotSlot: String? = null
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
                    put("velocityCurve", t.velocityCurve.toDouble())
                    put("splitNoteMin", t.splitNoteMin)
                    put("splitNoteMax", t.splitNoteMax)
                }
                tracksArray.put(obj)
            }

            val fxObject = JSONObject()
            fxParameters.forEach { (trackId, fx) ->
                val o = JSONObject().apply {
                    put("eqLow", fx.eqLow.toDouble())
                    put("eqMid", fx.eqMid.toDouble())
                    put("eqHigh", fx.eqHigh.toDouble())
                    put("eqGain", fx.eqGain.toDouble())
                    put("isReverbEnabled", fx.isReverbEnabled)
                    put("isChorusEnabled", fx.isChorusEnabled)
                    put("chorusRate", fx.chorusRate.toDouble())
                    put("chorusDepth", fx.chorusDepth.toDouble())
                    put("chorusMix", fx.chorusMix.toDouble())
                    put("isSgEnabled", fx.isSgEnabled)
                    put("sgAmount", fx.sgAmount.toDouble())
                    put("sgMode", fx.sgMode)
                    put("reverbPreset", fx.reverbPreset)
                    put("reverbMix", fx.reverbMix.toDouble())
                    put("reverbSize", fx.reverbSize.toDouble())
                    put("reverbDecay", fx.reverbDecay.toDouble())
                    put("reverbDamp", fx.reverbDamp.toDouble())
                    put("isCompEnabled", fx.isCompEnabled)
                    put("compThresh", fx.compThresh.toDouble())
                    put("compRatio", fx.compRatio.toDouble())
                    put("compAttack", fx.compAttack.toDouble())
                    put("compRelease", fx.compRelease.toDouble())
                    put("isDelayEnabled", fx.isDelayEnabled)
                    put("delayTime", fx.delayTime.toDouble())
                    put("delayFeedback", fx.delayFeedback.toDouble())
                    put("delayMix", fx.delayMix.toDouble())
                    put("delayPingPong", fx.delayPingPong.toDouble())
                }
                fxObject.put(trackId.toString(), o)
            }

            val snapshotsObject = JSONObject()
            snapshots.forEach { (slotKey, snap) ->
                val sObj = JSONObject().apply {
                    put("slotName", snap.slotName)
                    put("globalTranspose", snap.globalTranspose)
                    put("globalOctaveShift", snap.globalOctaveShift)
                    put("masterVolume", snap.masterVolume.toDouble())
                    put("timestamp", snap.timestamp)
                    val tArray = JSONArray()
                    snap.tracks.forEach { t ->
                        val to = JSONObject().apply {
                            put("id", t.id)
                            put("volume", t.volume.toDouble())
                            put("pan", t.pan.toDouble())
                            put("isMuted", t.isMuted)
                            put("isSolo", t.isSolo)
                            put("isEnabled", t.isEnabled)
                            put("soundfontName", t.soundfontName)
                            put("patchName", t.patchName)
                            put("bank", t.bank)
                            put("program", t.program)
                            put("transpose", t.transpose)
                            put("octave", t.octave)
                            put("reverbSend", t.reverbSend.toDouble())
                        }
                        tArray.put(to)
                    }
                    put("tracks", tArray)
                }
                snapshotsObject.put(slotKey, sObj)
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
                    put("sampleFilePath", d.sampleFilePath)
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
                putString(KEY_FX_PARAMETERS_JSON, fxObject.toString())
                putString(KEY_SNAPSHOTS_JSON, snapshotsObject.toString())
                putString(KEY_ACTIVE_SNAPSHOT_SLOT, activeSnapshotSlot)
                putString(KEY_AUDIO_SLOTS_JSON, slotsArray.toString())
                putString(KEY_DRUM_PADS_JSON, drumArray.toString())
                putInt(KEY_ACTIVE_SF2_TRACK_ID, activeSf2TrackId)
                putString(KEY_LAST_ACTIVITY, lastActivity)
                putFloat(KEY_TONIC_BRIGHTNESS, tonicBrightness)
                putFloat(KEY_TONIC_SHIMMER, tonicShimmer)
                putFloat(KEY_DRUM_REVERB, drumReverb)
                putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn)
                putString(KEY_SELECTED_SCALE_MODE, selectedScaleMode)
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
            val tonicBrightness = if (prefs.contains(KEY_TONIC_BRIGHTNESS)) prefs.getFloat(KEY_TONIC_BRIGHTNESS, 0.70f) else null
            val tonicShimmer = if (prefs.contains(KEY_TONIC_SHIMMER)) prefs.getFloat(KEY_TONIC_SHIMMER, 0.15f) else null
            val drumReverb = if (prefs.contains(KEY_DRUM_REVERB)) prefs.getFloat(KEY_DRUM_REVERB, 0.24f) else null
            val keepScreenOn = if (prefs.contains(KEY_KEEP_SCREEN_ON)) prefs.getBoolean(KEY_KEEP_SCREEN_ON, true) else null
            val selectedScaleMode = prefs.getString(KEY_SELECTED_SCALE_MODE, null)

            val tracksList = mutableListOf<SavedTrackData>()
            val tracksJson = prefs.getString(KEY_TRACKS_JSON, null)
            if (tracksJson != null) {
                val array = JSONArray(tracksJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    tracksList.add(
                        SavedTrackData(
                            id = obj.optInt("id", i + 1),
                            name = obj.optString("name", "Track ${i + 1}"),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            volume = obj.optDouble("volume", 0.65).toFloat(),
                            pan = obj.optDouble("pan", 0.0).toFloat(),
                            soundfontName = obj.optString("soundfontName", ""),
                            patchName = obj.optString("patchName", "-"),
                            bank = obj.optInt("bank", 0),
                            program = obj.optInt("program", 0),
                            reverbPreset = obj.optString("reverbPreset", "Concert Hall"),
                            reverbMix = obj.optDouble("reverbMix", 0.20).toFloat(),
                            velocityCurve = obj.optDouble("velocityCurve", 0.5).toFloat(),
                            splitNoteMin = obj.optInt("splitNoteMin", 24),
                            splitNoteMax = obj.optInt("splitNoteMax", 108)
                        )
                    )
                }
            }

            val fxMap = mutableMapOf<Int, FxParameters>()
            val fxJsonStr = prefs.getString(KEY_FX_PARAMETERS_JSON, null)
            if (fxJsonStr != null) {
                try {
                    val fxObj = JSONObject(fxJsonStr)
                    val keys = fxObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val trackId = key.toIntOrNull() ?: continue
                        val o = fxObj.getJSONObject(key)
                        fxMap[trackId] = FxParameters(
                            eqLow = o.optDouble("eqLow", 0.5).toFloat(),
                            eqMid = o.optDouble("eqMid", 0.5).toFloat(),
                            eqHigh = o.optDouble("eqHigh", 0.5).toFloat(),
                            eqGain = o.optDouble("eqGain", 0.5).toFloat(),
                            isReverbEnabled = o.optBoolean("isReverbEnabled", false),
                            reverbPreset = o.optString("reverbPreset", "Concert Hall"),
                            reverbMix = o.optDouble("reverbMix", 0.24).toFloat(),
                            reverbSize = o.optDouble("reverbSize", 0.6).toFloat(),
                            reverbDecay = o.optDouble("reverbDecay", 0.45).toFloat(),
                            reverbDamp = o.optDouble("reverbDamp", 0.3).toFloat(),
                            isChorusEnabled = o.optBoolean("isChorusEnabled", false),
                            chorusRate = o.optDouble("chorusRate", 0.35).toFloat(),
                            chorusDepth = o.optDouble("chorusDepth", 0.50).toFloat(),
                            chorusMix = o.optDouble("chorusMix", 0.0).toFloat(),
                            isSgEnabled = o.optBoolean("isSgEnabled", false),
                            sgAmount = o.optDouble("sgAmount", 0.0).toFloat(),
                            sgMode = o.optInt("sgMode", 0),
                            isCompEnabled = o.optBoolean("isCompEnabled", false),
                            compThresh = o.optDouble("compThresh", 0.4).toFloat(),
                            compRatio = o.optDouble("compRatio", 0.5).toFloat(),
                            compAttack = o.optDouble("compAttack", 0.2).toFloat(),
                            compRelease = o.optDouble("compRelease", 0.35).toFloat(),
                            isDelayEnabled = o.optBoolean("isDelayEnabled", false),
                            delayTime = o.optDouble("delayTime", 0.35).toFloat(),
                            delayFeedback = o.optDouble("delayFeedback", 0.4).toFloat(),
                            delayMix = o.optDouble("delayMix", 0.0).toFloat(),
                            delayPingPong = o.optDouble("delayPingPong", 0.0).toFloat()
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing saved fx parameters: ${e.message}")
                }
            }

            val snapshotsMap = mutableMapOf<String, SubSceneSnapshot>()
            val snapshotsJsonStr = prefs.getString(KEY_SNAPSHOTS_JSON, null)
            val activeSnapshotSlot = prefs.getString(KEY_ACTIVE_SNAPSHOT_SLOT, null)
            if (snapshotsJsonStr != null) {
                try {
                    val sObj = JSONObject(snapshotsJsonStr)
                    val sKeys = sObj.keys()
                    while (sKeys.hasNext()) {
                        val slotKey = sKeys.next()
                        val snapJson = sObj.getJSONObject(slotKey)
                        val slotName = snapJson.optString("slotName", slotKey)
                        val globalTranspose = snapJson.optInt("globalTranspose", 0)
                        val globalOctaveShift = snapJson.optInt("globalOctaveShift", 0)
                        val masterVol = snapJson.optDouble("masterVolume", 0.85).toFloat()
                        val timestamp = snapJson.optLong("timestamp", System.currentTimeMillis())

                        val trackSnapList = mutableListOf<TrackSnapshot>()
                        val tArray = snapJson.optJSONArray("tracks")
                        if (tArray != null) {
                            for (ti in 0 until tArray.length()) {
                                val to = tArray.getJSONObject(ti)
                                trackSnapList.add(
                                    TrackSnapshot(
                                        id = to.optInt("id", ti + 1),
                                        volume = to.optDouble("volume", 0.65).toFloat(),
                                        pan = to.optDouble("pan", 0.0).toFloat(),
                                        isMuted = to.optBoolean("isMuted", false),
                                        isSolo = to.optBoolean("isSolo", false),
                                        isEnabled = to.optBoolean("isEnabled", true),
                                        soundfontName = to.optString("soundfontName", ""),
                                        patchName = to.optString("patchName", ""),
                                        bank = to.optInt("bank", 0),
                                        program = to.optInt("program", 0),
                                        transpose = to.optInt("transpose", 0),
                                        octave = to.optInt("octave", 0),
                                        reverbSend = to.optDouble("reverbSend", 0.0).toFloat()
                                    )
                                )
                            }
                        }
                        snapshotsMap[slotKey] = SubSceneSnapshot(
                            slotName = slotName,
                            tracks = trackSnapList,
                            globalTranspose = globalTranspose,
                            globalOctaveShift = globalOctaveShift,
                            masterVolume = masterVol,
                            timestamp = timestamp
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing saved snapshots: ${e.message}")
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
                            patchName = if (obj.has("patchName")) obj.optString("patchName", "") else null,
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
                            sampleFilePath = obj.optString("sampleFilePath", ""),
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
                fxParameters = fxMap,
                activeSf2TrackId = activeSf2TrackId,
                lastActivity = lastActivity,
                tonicBrightness = tonicBrightness,
                tonicShimmer = tonicShimmer,
                drumReverb = drumReverb,
                keepScreenOn = keepScreenOn,
                selectedScaleMode = selectedScaleMode,
                snapshots = snapshotsMap,
                activeSnapshotSlot = activeSnapshotSlot
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app state: ${e.message}")
            return null
        }
    }

    fun saveLastPath(key: String, path: String) {
        try {
            prefs.edit().putString(key, path).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last path for $key: ${e.message}")
        }
    }

    fun getLastPath(key: String, defaultPath: String): String {
        return try {
            prefs.getString(key, defaultPath) ?: defaultPath
        } catch (e: Exception) {
            defaultPath
        }
    }
}
