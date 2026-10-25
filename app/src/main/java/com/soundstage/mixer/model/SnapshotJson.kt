package com.soundstage.mixer.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for [TrackSnapshot] and [SubSceneSnapshot].
 *
 * The snapshot shape was written out by hand in six places (three readers, three
 * writers), so every field added to the model had to be added six times and any
 * missed spot silently dropped it. All of them now go through this object.
 *
 * Every field is read with a default, so a blob written by an older build still
 * loads: the new keys simply fall back to the model's own defaults.
 */
object SnapshotJson {

    fun fxToJson(fx: FxParameters): JSONObject = JSONObject().apply {
        put("eqLow", fx.eqLow.toDouble())
        put("eqMid", fx.eqMid.toDouble())
        put("eqHigh", fx.eqHigh.toDouble())
        put("eqGain", fx.eqGain.toDouble())
        put("isReverbEnabled", fx.isReverbEnabled)
        put("reverbPreset", fx.reverbPreset)
        put("reverbMix", fx.reverbMix.toDouble())
        put("reverbSize", fx.reverbSize.toDouble())
        put("reverbDecay", fx.reverbDecay.toDouble())
        put("reverbDamp", fx.reverbDamp.toDouble())
        put("isChorusEnabled", fx.isChorusEnabled)
        put("chorusRate", fx.chorusRate.toDouble())
        put("chorusDepth", fx.chorusDepth.toDouble())
        put("chorusMix", fx.chorusMix.toDouble())
        put("isSgEnabled", fx.isSgEnabled)
        put("sgAmount", fx.sgAmount.toDouble())
        put("sgMode", fx.sgMode)
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

    fun fxFromJson(o: JSONObject): FxParameters = FxParameters(
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

    /** FX racks keyed by track index (0 = master, 1..8 = tracks). */
    fun trackFxToJson(trackFx: Map<Int, FxParameters>): JSONObject = JSONObject().apply {
        trackFx.forEach { (slot, fx) -> put(slot.toString(), fxToJson(fx)) }
    }

    fun trackFxFromJson(o: JSONObject?): Map<Int, FxParameters> {
        if (o == null) return emptyMap()
        val out = mutableMapOf<Int, FxParameters>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val slot = k.toIntOrNull() ?: continue
            o.optJSONObject(k)?.let { out[slot] = fxFromJson(it) }
        }
        return out
    }

    fun trackToJson(t: TrackSnapshot): JSONObject = JSONObject().apply {
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
        put("splitNoteMin", t.splitNoteMin)
        put("splitNoteMax", t.splitNoteMax)
        put("velocityCurve", t.velocityCurve.toDouble())
        put("sustain", t.sustain)
        put("aftertouch", t.aftertouch)
        put("programChange", t.programChange)
        put("pitchBendRange", t.pitchBendRange)
        put("midiChannel", t.midiChannel)
        t.customColorArgb?.let { put("customColorArgb", it) }
    }

    fun trackFromJson(o: JSONObject, fallbackId: Int): TrackSnapshot = TrackSnapshot(
        id = o.optInt("id", fallbackId),
        volume = o.optDouble("volume", 0.65).toFloat(),
        pan = o.optDouble("pan", 0.0).toFloat(),
        isMuted = o.optBoolean("isMuted", false),
        isSolo = o.optBoolean("isSolo", false),
        isEnabled = o.optBoolean("isEnabled", true),
        soundfontName = o.optString("soundfontName", ""),
        patchName = o.optString("patchName", "-"),
        bank = o.optInt("bank", 0),
        program = o.optInt("program", 0),
        transpose = o.optInt("transpose", 0),
        octave = o.optInt("octave", 0),
        reverbSend = o.optDouble("reverbSend", 0.20).toFloat(),
        splitNoteMin = o.optInt("splitNoteMin", 24),
        splitNoteMax = o.optInt("splitNoteMax", 108),
        velocityCurve = o.optDouble("velocityCurve", 0.5).toFloat(),
        sustain = o.optBoolean("sustain", true),
        aftertouch = o.optBoolean("aftertouch", false),
        programChange = o.optBoolean("programChange", true),
        pitchBendRange = o.optInt("pitchBendRange", 2),
        midiChannel = o.optInt("midiChannel", 1),
        // Absent means "no override", which is not the same as 0 (opaque black).
        customColorArgb = if (o.has("customColorArgb")) o.optInt("customColorArgb") else null
    )

    fun toJson(snap: SubSceneSnapshot): JSONObject = JSONObject().apply {
        put("slotName", snap.slotName)
        put("globalTranspose", snap.globalTranspose)
        put("globalOctaveShift", snap.globalOctaveShift)
        put("masterVolume", snap.masterVolume.toDouble())
        put("visibleTracksCount", snap.visibleTracksCount)
        put("bpm", snap.bpm)
        put("timestamp", snap.timestamp)
        put("tracks", JSONArray().apply { snap.tracks.forEach { put(trackToJson(it)) } })
        snap.fxParameters?.let { put("fxParameters", fxToJson(it)) }
        if (snap.trackFx.isNotEmpty()) put("trackFx", trackFxToJson(snap.trackFx))
    }

    fun fromJson(slotKey: String, o: JSONObject): SubSceneSnapshot {
        val tracks = mutableListOf<TrackSnapshot>()
        o.optJSONArray("tracks")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { tracks.add(trackFromJson(it, i + 1)) }
            }
        }
        val visible = o.optInt("visibleTracksCount", -1)
        return SubSceneSnapshot(
            slotName = o.optString("slotName", slotKey),
            tracks = tracks,
            globalTranspose = o.optInt("globalTranspose", 0),
            globalOctaveShift = o.optInt("globalOctaveShift", 0),
            masterVolume = o.optDouble("masterVolume", 0.85).toFloat(),
            visibleTracksCount = if (visible in 1..8) visible else 1,
            fxParameters = o.optJSONObject("fxParameters")?.let { fxFromJson(it) },
            bpm = o.optInt("bpm", 120).coerceIn(40, 240),
            trackFx = trackFxFromJson(o.optJSONObject("trackFx")),
            timestamp = o.optLong("timestamp", System.currentTimeMillis())
        )
    }

    /** Serialises a whole snapshot map. */
    fun mapToJson(snapshots: Map<String, SubSceneSnapshot>): JSONObject = JSONObject().apply {
        snapshots.forEach { (key, snap) -> put(key, toJson(snap)) }
    }

    /** Reads a whole snapshot map, tolerating a missing or malformed blob. */
    fun mapFromJson(o: JSONObject?): Map<String, SubSceneSnapshot> {
        if (o == null) return emptyMap()
        val out = mutableMapOf<String, SubSceneSnapshot>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            o.optJSONObject(key)?.let { out[key] = fromJson(key, it) }
        }
        return out
    }
}
