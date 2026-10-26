package com.soundstage.mixer.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for the per-setlist track and slot lists.
 *
 * Both are written in several places and read back by `selectScene`. Because the
 * reader treats a missing key as "keep what is already in memory", a writer that
 * forgets `tracks` does not produce a blank setlist: the new setlist silently
 * inherits the tracks of the previous one, soundfont names included, and the
 * loader then pushes those soundfonts into the native engine. Every writer must
 * therefore emit the key, even when the list is empty.
 */
object TrackJson {

    fun toJson(tracks: List<TrackChannel>): JSONArray = JSONArray().apply {
        tracks.forEach { t ->
            put(JSONObject().apply {
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
            })
        }
    }
}

/** JSON codec for [AudioSlot]; see [TrackJson] for why every key must always be written. */
object AudioSlotJson {

    fun toJson(slots: List<AudioSlot>): JSONArray = JSONArray().apply {
        slots.forEach { s ->
            put(JSONObject().apply {
                put("slotId", s.slotId)
                put("soundFontPath", s.soundFontPath ?: "")
                put("bank", s.bank)
                put("preset", s.preset)
                put("patchName", s.patchName ?: "")
                put("volume", s.volume.toDouble())
                put("pan", s.pan.toDouble())
            })
        }
    }
}
