package com.soundstage.mixer.model

enum class MidiControlType(val displayName: String) {
    CC("Control Change (CC)"),
    NOTE("MIDI Note"),
    PROGRAM_CHANGE("Program Change (PC)")
}

enum class MidiControlMode(val displayName: String) {
    MOMENTARY("Momentary"),
    TOGGLE("Toggle"),
    CONTINUOUS_VALUE("Continuous / Fader")
}

enum class MidiActionCategory(val displayName: String, val subtitle: String) {
    ALL("All", "All mappable actions"),
    SNAPSHOTS("Snapshots", "Snapshot slots 1-4, Default & switching"),
    PATCHES("Patches & Setlists", "Previous/Next patch navigation"),
    TRACKS("Tracks & Channels", "Faders, Mute & Solo for tracks 1-8"),
    DRUMPAD("Drum Pads", "Pad triggers 1-8 & loop stop"),
    GLOBAL("Master & Live", "Master volume, Panic, Tap tempo & Metronome")
}

enum class MidiActionType(
    val id: String,
    val category: MidiActionCategory,
    val title: String,
    val subtitle: String,
    val defaultType: MidiControlType,
    val isContinuous: Boolean = false
) {
    // Snapshots
    SNAPSHOT_DEFAULT("snap_def", MidiActionCategory.SNAPSHOTS, "Snapshot Default", "Recall Default snapshot state", MidiControlType.CC),
    SNAPSHOT_1("snap_1", MidiActionCategory.SNAPSHOTS, "Snapshot Slot 1", "Recall Snapshot 1 mix & soundfonts", MidiControlType.CC),
    SNAPSHOT_2("snap_2", MidiActionCategory.SNAPSHOTS, "Snapshot Slot 2", "Recall Snapshot 2", MidiControlType.CC),
    SNAPSHOT_3("snap_3", MidiActionCategory.SNAPSHOTS, "Snapshot Slot 3", "Recall Snapshot 3", MidiControlType.CC),
    SNAPSHOT_4("snap_4", MidiActionCategory.SNAPSHOTS, "Snapshot Slot 4", "Recall Snapshot 4", MidiControlType.CC),
    SNAPSHOT_PREV("snap_prev", MidiActionCategory.SNAPSHOTS, "Previous Snapshot", "Switch to previous active snapshot", MidiControlType.CC),
    SNAPSHOT_NEXT("snap_next", MidiActionCategory.SNAPSHOTS, "Next Snapshot", "Switch to next active snapshot", MidiControlType.CC),

    // Patches
    PATCH_PREV("patch_prev", MidiActionCategory.PATCHES, "Previous Patch", "Select previous patch in setlist", MidiControlType.PROGRAM_CHANGE),
    PATCH_NEXT("patch_next", MidiActionCategory.PATCHES, "Next Patch", "Select next patch in setlist", MidiControlType.PROGRAM_CHANGE),

    // Tracks Mute / Solo / Volume
    TRACK_1_MUTE("tr_1_mute", MidiActionCategory.TRACKS, "Track 1 - Mute / Unmute", "Toggle mute on track 1", MidiControlType.CC),
    TRACK_1_SOLO("tr_1_solo", MidiActionCategory.TRACKS, "Track 1 - Solo", "Toggle solo on track 1", MidiControlType.CC),
    TRACK_1_VOL("tr_1_vol", MidiActionCategory.TRACKS, "Track 1 - Fader Volume", "Control track 1 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_2_MUTE("tr_2_mute", MidiActionCategory.TRACKS, "Track 2 - Mute / Unmute", "Toggle mute on track 2", MidiControlType.CC),
    TRACK_2_SOLO("tr_2_solo", MidiActionCategory.TRACKS, "Track 2 - Solo", "Toggle solo on track 2", MidiControlType.CC),
    TRACK_2_VOL("tr_2_vol", MidiActionCategory.TRACKS, "Track 2 - Fader Volume", "Control track 2 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_3_MUTE("tr_3_mute", MidiActionCategory.TRACKS, "Track 3 - Mute / Unmute", "Toggle mute on track 3", MidiControlType.CC),
    TRACK_3_SOLO("tr_3_solo", MidiActionCategory.TRACKS, "Track 3 - Solo", "Toggle solo on track 3", MidiControlType.CC),
    TRACK_3_VOL("tr_3_vol", MidiActionCategory.TRACKS, "Track 3 - Fader Volume", "Control track 3 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_4_MUTE("tr_4_mute", MidiActionCategory.TRACKS, "Track 4 - Mute / Unmute", "Toggle mute on track 4", MidiControlType.CC),
    TRACK_4_SOLO("tr_4_solo", MidiActionCategory.TRACKS, "Track 4 - Solo", "Toggle solo on track 4", MidiControlType.CC),
    TRACK_4_VOL("tr_4_vol", MidiActionCategory.TRACKS, "Track 4 - Fader Volume", "Control track 4 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_5_MUTE("tr_5_mute", MidiActionCategory.TRACKS, "Track 5 - Mute / Unmute", "Toggle mute on track 5", MidiControlType.CC),
    TRACK_5_SOLO("tr_5_solo", MidiActionCategory.TRACKS, "Track 5 - Solo", "Toggle solo on track 5", MidiControlType.CC),
    TRACK_5_VOL("tr_5_vol", MidiActionCategory.TRACKS, "Track 5 - Fader Volume", "Control track 5 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_6_MUTE("tr_6_mute", MidiActionCategory.TRACKS, "Track 6 - Mute / Unmute", "Toggle mute on track 6", MidiControlType.CC),
    TRACK_6_SOLO("tr_6_solo", MidiActionCategory.TRACKS, "Track 6 - Solo", "Toggle solo on track 6", MidiControlType.CC),
    TRACK_6_VOL("tr_6_vol", MidiActionCategory.TRACKS, "Track 6 - Fader Volume", "Control track 6 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_7_MUTE("tr_7_mute", MidiActionCategory.TRACKS, "Track 7 - Mute / Unmute", "Toggle mute on track 7", MidiControlType.CC),
    TRACK_7_SOLO("tr_7_solo", MidiActionCategory.TRACKS, "Track 7 - Solo", "Toggle solo on track 7", MidiControlType.CC),
    TRACK_7_VOL("tr_7_vol", MidiActionCategory.TRACKS, "Track 7 - Fader Volume", "Control track 7 fader (0..127)", MidiControlType.CC, isContinuous = true),

    TRACK_8_MUTE("tr_8_mute", MidiActionCategory.TRACKS, "Track 8 - Mute / Unmute", "Toggle mute on track 8", MidiControlType.CC),
    TRACK_8_SOLO("tr_8_solo", MidiActionCategory.TRACKS, "Track 8 - Solo", "Toggle solo on track 8", MidiControlType.CC),
    TRACK_8_VOL("tr_8_vol", MidiActionCategory.TRACKS, "Track 8 - Fader Volume", "Control track 8 fader (0..127)", MidiControlType.CC, isContinuous = true),

    // Drum Pads
    DRUMPAD_PAD_1("dp_1", MidiActionCategory.DRUMPAD, "Drum Pad 1 (Trigger)", "Trigger sample / loop on Pad 1", MidiControlType.NOTE),
    DRUMPAD_PAD_2("dp_2", MidiActionCategory.DRUMPAD, "Drum Pad 2 (Trigger)", "Trigger sample / loop on Pad 2", MidiControlType.NOTE),
    DRUMPAD_PAD_3("dp_3", MidiActionCategory.DRUMPAD, "Drum Pad 3 (Trigger)", "Trigger sample / loop on Pad 3", MidiControlType.NOTE),
    DRUMPAD_PAD_4("dp_4", MidiActionCategory.DRUMPAD, "Drum Pad 4 (Trigger)", "Trigger sample / loop on Pad 4", MidiControlType.NOTE),
    DRUMPAD_PAD_5("dp_5", MidiActionCategory.DRUMPAD, "Drum Pad 5 (Trigger)", "Trigger sample / loop on Pad 5", MidiControlType.NOTE),
    DRUMPAD_PAD_6("dp_6", MidiActionCategory.DRUMPAD, "Drum Pad 6 (Trigger)", "Trigger sample / loop on Pad 6", MidiControlType.NOTE),
    DRUMPAD_PAD_7("dp_7", MidiActionCategory.DRUMPAD, "Drum Pad 7 (Trigger)", "Trigger sample / loop on Pad 7", MidiControlType.NOTE),
    DRUMPAD_PAD_8("dp_8", MidiActionCategory.DRUMPAD, "Drum Pad 8 (Trigger)", "Trigger sample / loop on Pad 8", MidiControlType.NOTE),
    DRUMPAD_STOP_ALL("dp_stop", MidiActionCategory.DRUMPAD, "Drum Pads - Stop Loops", "Stop all playing drum loops", MidiControlType.CC),

    // Master & Live
    MASTER_VOLUME("master_vol", MidiActionCategory.GLOBAL, "Master Volume", "Control global master output volume", MidiControlType.CC, isContinuous = true),
    PANIC_ALL_NOTES_OFF("panic", MidiActionCategory.GLOBAL, "Panic / All Notes Off", "Immediately stop all playing voices", MidiControlType.CC),
    TAP_TEMPO("tap_tempo", MidiActionCategory.GLOBAL, "Tap Tempo", "Tap continuously to calculate BPM", MidiControlType.CC),
    METRONOME_TOGGLE("metro_toggle", MidiActionCategory.GLOBAL, "Metronome On / Off", "Toggle metronome click sound", MidiControlType.CC),
    TRANSPOSE_UP("transpose_up", MidiActionCategory.GLOBAL, "Transpose +1 Semitone", "Shift master pitch up by 1 semitone", MidiControlType.CC),
    TRANSPOSE_DOWN("transpose_down", MidiActionCategory.GLOBAL, "Transpose -1 Semitone", "Shift master pitch down by 1 semitone", MidiControlType.CC),
    OCTAVE_UP("octave_up", MidiActionCategory.GLOBAL, "Keyboard Octave +1", "Shift physical keyboard range up 1 octave", MidiControlType.CC),
    OCTAVE_DOWN("octave_down", MidiActionCategory.GLOBAL, "Keyboard Octave -1", "Shift physical keyboard range down 1 octave", MidiControlType.CC),
    OPEN_PATCHES("open_patches", MidiActionCategory.GLOBAL, "Open Patches Drawer", "Open patches side drawer", MidiControlType.CC),
    OPEN_SETTINGS("open_settings", MidiActionCategory.GLOBAL, "Open Settings Drawer", "Open settings side drawer", MidiControlType.CC)
}

data class MidiMappingItem(
    val actionType: MidiActionType,
    val midiType: MidiControlType = MidiControlType.CC,
    val channel: Int = -1, // -1: Omni (All channels), 0..15 for Channel 1..16
    val controlNumber: Int = -1, // CC#, Note# or Program# (0..127). -1 if unassigned
    val isEnabled: Boolean = true,
    val mode: MidiControlMode = MidiControlMode.MOMENTARY
) {
    val isAssigned: Boolean get() = controlNumber >= 0

    fun formatDescription(): String {
        if (!isAssigned) return "Unassigned"
        val chStr = if (channel < 0) "Omni (All Ch)" else "Ch. ${channel + 1}"
        return when (midiType) {
            MidiControlType.CC -> "CC #$controlNumber · $chStr"
            MidiControlType.NOTE -> {
                val octave = (controlNumber / 12) - 1
                val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val name = noteNames[controlNumber % 12] + octave
                "Note $name (#$controlNumber) · $chStr"
            }
            MidiControlType.PROGRAM_CHANGE -> "PC #$controlNumber · $chStr"
        }
    }
}
