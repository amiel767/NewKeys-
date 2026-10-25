package com.soundstage.mixer.model

/**
 * Pure decisions behind the snapshot and patch-switch machinery.
 *
 * These used to live inline in [com.soundstage.mixer.viewmodel.MixerViewModel],
 * rebuilt from scratch at every call site. Six copies of the same track mapping
 * meant a field added to [TrackSnapshot] silently missed five of them, and the
 * patch-switch release rule was written twice with two different answers. Keeping
 * them here makes both testable without an Android runtime.
 */
object SnapshotLogic {

    /**
     * Captures a live track into a snapshot entry. Every field the snapshot model
     * carries is written here, so a new field cannot be forgotten at one call site.
     */
    fun captureTrack(track: TrackChannel): TrackSnapshot = TrackSnapshot(
        id = track.id,
        volume = track.volume,
        pan = track.pan,
        isMuted = track.isMuted,
        isSolo = track.isSolo,
        isEnabled = track.isEnabled,
        soundfontName = track.soundfontName,
        patchName = track.patchName,
        bank = track.bank,
        program = track.program,
        reverbSend = track.reverbMix,
        splitNoteMin = track.splitNoteMin,
        splitNoteMax = track.splitNoteMax,
        velocityCurve = track.velocityCurve,
        transpose = track.transpose,
        octave = track.octave,
        sustain = track.sustain,
        aftertouch = track.aftertouch,
        programChange = track.programChange,
        pitchBendRange = track.pitchBendRange,
        midiChannel = track.midiChannel,
        customColorArgb = track.customColorArgb
    )

    /**
     * Applies a snapshot entry back onto a live track.
     *
     * [snap] may be null: the track was created after the snapshot was taken. It is
     * then powered off and muted instead of removed, which protects the snapshot's
     * mix without destroying the instrument the user just added.
     *
     * The soundfont identity is only overwritten when the snapshot carries one, so a
     * snapshot taken before a track had an instrument does not blank the current one.
     *
     * The caller must push `transpose + octave * 12` and the reverb send to the engine
     * after calling this: [TrackChannel.transpose], [TrackChannel.octave] and
     * [TrackChannel.reverbMix] live in the native synth, so writing them into the
     * state alone would leave the engine playing the previous snapshot's pitch.
     */
    fun applyToTrack(track: TrackChannel, snap: TrackSnapshot?): TrackChannel {
        if (snap == null) return track.copy(isEnabled = false, isMuted = true)
        val soundfontName = snap.soundfontName.ifEmpty { track.soundfontName }
        val patchName = if (snap.soundfontName.isNotEmpty()) snap.patchName else track.patchName
        return track.copy(
            volume = snap.volume,
            pan = snap.pan,
            isMuted = snap.isMuted,
            isSolo = snap.isSolo,
            isEnabled = snap.isEnabled,
            soundfontName = soundfontName,
            patchName = patchName,
            bank = snap.bank,
            program = snap.program,
            splitNoteMin = snap.splitNoteMin,
            splitNoteMax = snap.splitNoteMax,
            velocityCurve = snap.velocityCurve,
            transpose = snap.transpose,
            octave = snap.octave,
            reverbMix = snap.reverbSend,
            sustain = snap.sustain,
            aftertouch = snap.aftertouch,
            programChange = snap.programChange,
            pitchBendRange = snap.pitchBendRange,
            midiChannel = snap.midiChannel,
            customColorArgb = snap.customColorArgb
        )
    }

    /** Highest slot id that belongs to a patch. Slots 8 (Drum) and 9 (Pad) outlive a patch switch. */
    const val LAST_TRACK_SLOT = 7

    /**
     * Decides which loaded soundfonts must be freed when the active patch changes.
     *
     * A slot is released when it holds a live soundfont whose path the incoming patch
     * does not need. The Drum and Pad slots are never touched: they belong to the
     * instrument surfaces, not to a patch, and unloading them would silence the drum
     * kit on every patch switch.
     *
     * The "still needed" set must include the incoming patch's own slot list as well
     * as its tracks: a patch can carry a loaded slot whose track entry is empty.
     *
     * @return the slot ids to free, in ascending order.
     */
    fun slotsToRelease(
        currentSlots: List<AudioSlot>,
        targetSoundfontPaths: Set<String>,
    ): List<Int> = currentSlots
        .filter { it.slotId in 0..LAST_TRACK_SLOT }
        .filter { it.soundFontId > 0 && !it.soundFontPath.isNullOrEmpty() }
        .filter { it.soundFontPath !in targetSoundfontPaths }
        .map { it.slotId }
        .sorted()

    /**
     * Soundfont paths the incoming patch still needs. Reads both the patch's track
     * names and its stored slot list, and keeps the Drum/Pad surfaces out of the
     * comparison so they are never candidates for release.
     */
    fun retainedSoundfontPaths(
        targetTracks: List<TrackChannel>,
        targetSlots: List<AudioSlot>,
        currentSlots: List<AudioSlot>,
        resolvePath: (String) -> String?,
    ): Set<String> = buildSet {
        targetTracks.forEach { track -> resolvePath(track.soundfontName)?.let(::add) }
        targetSlots.forEach { slot -> slot.soundFontPath?.let(::add) }
        currentSlots.filter { it.slotId > LAST_TRACK_SLOT }
            .forEach { slot -> slot.soundFontPath?.let(::add) }
    }
}
