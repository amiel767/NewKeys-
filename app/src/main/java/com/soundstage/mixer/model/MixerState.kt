package com.soundstage.mixer.model

import androidx.compose.ui.graphics.Color
import com.soundstage.mixer.ui.theme.NeonCyan
import com.soundstage.mixer.ui.theme.NeonMagenta
import com.soundstage.mixer.ui.theme.MuteRed
import com.soundstage.mixer.ui.theme.SoloAmber

enum class ActivePopup {
    NONE, DRUM_PAD, TONIC_PAD, NOTES, SCENE, EFFECTS, SOUNDFONT, STYLE, MIDI, LOOPS
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español")
}

enum class SoundGoodizerMode(val label: String, val description: String) {
    A("A - Warm Tube", "Analog warmth & round harmonic saturation"),
    B("B - Crisp Air", "Brilliant high exciter & punchy dynamics"),
    C("C - Deep Crunch", "Heavy multiband compression & bass crunch"),
    D("D - Hard Limiter", "Maximum impact, brickwall & punchy loudness")
}

enum class AppTheme(val displayName: String, val description: String) {
    CYBER_NEON("Cyber Neon (Default)", "Dark canvas with cyan & violet neon accents"),
    OBSIDIAN_GOLD("Obsidian Gold", "Deep black with luxury amber & gold accents"),
    TOKYO_NIGHT("Tokyo Night", "Deep night blue with magenta & indigo touches"),
    STUDIO_SLATE("Studio Slate", "Clean professional studio slate gray"),
    OLED_BLACK("OLED Pure Black", "Absolute pitch black for maximum contrast")
}

enum class DrumSoundType {
    SAMPLE, SF2_NOTE
}

enum class DrumPadCategory(val title: String) {
    GRADIENT("GRADIENT"),
    LED("LED"),
    NEON("NEON"),
    MATERIAL_YOU("MATERIAL YOU")
}

enum class DrumPadStyle(
    val displayName: String,
    val category: DrumPadCategory,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    // GRADIENT
    GRADIENT_CYAN("Cyan Profond", DrumPadCategory.GRADIENT, Color(0xFF00E5FF), Color(0xFF0052CC)),
    GRADIENT_SUNSET("Sunset Lave", DrumPadCategory.GRADIENT, Color(0xFFFF3D00), Color(0xFFD50000)),
    GRADIENT_PURPLE("Violet Galaxie", DrumPadCategory.GRADIENT, Color(0xFFD500F9), Color(0xFF651FFF)),
    GRADIENT_GOLD("Or Impérial", DrumPadCategory.GRADIENT, Color(0xFFFFD700), Color(0xFFB8860B)),
    GRADIENT_EMERALD("Émeraude Lux", DrumPadCategory.GRADIENT, Color(0xFF00E676), Color(0xFF00796B)),
    GRADIENT_CRIMSON("Crimson Dark", DrumPadCategory.GRADIENT, Color(0xFFFF1744), Color(0xFF880E4F)),

    // LED
    LED_AMBER("LED Ambre 808", DrumPadCategory.LED, Color(0xFFFFB300), Color(0xFFFF6F00)),
    LED_ICE_BLUE("LED Ice Blue", DrumPadCategory.LED, Color(0xFF38BDF8), Color(0xFF0284C7)),
    LED_MATRIX_GREEN("LED Matrix", DrumPadCategory.LED, Color(0xFF22C55E), Color(0xFF15803D)),
    LED_HOT_PINK("LED Hot Pink", DrumPadCategory.LED, Color(0xFFF43F5E), Color(0xFF9F1239)),
    LED_UV_VIOLET("LED UV", DrumPadCategory.LED, Color(0xFFA855F7), Color(0xFF6B21A8)),

    // NEON
    NEON_CYAN("Néon Laser Cyan", DrumPadCategory.NEON, Color(0xFF06B6D4), Color(0xFF0891B2)),
    NEON_LIME("Néon Acid Lime", DrumPadCategory.NEON, Color(0xFF84CC16), Color(0xFF4D7C0F)),
    NEON_MAGENTA("Néon Tokyo Rose", DrumPadCategory.NEON, Color(0xFFEC4899), Color(0xFFBE185D)),
    NEON_PURPLE("Néon Electric", DrumPadCategory.NEON, Color(0xFF8B5CF6), Color(0xFF5B21B6)),

    // MATERIAL YOU
    MY_CORAL("Tonal Coral", DrumPadCategory.MATERIAL_YOU, Color(0xFFFF8A80), Color(0xFFC51162)),
    MY_TURQUOISE("Dynamic Teal", DrumPadCategory.MATERIAL_YOU, Color(0xFF80DEEA), Color(0xFF00695C)),
    MY_LAVENDER("Pastel Lavender", DrumPadCategory.MATERIAL_YOU, Color(0xFFD1C4E9), Color(0xFF512DA8)),
    MY_MINT("Mint Pistache", DrumPadCategory.MATERIAL_YOU, Color(0xFFA7F3D0), Color(0xFF047857)),
    MY_PEACH("Peach Blossom", DrumPadCategory.MATERIAL_YOU, Color(0xFFFED7AA), Color(0xFFC2410C)),

    // DUBSTEP CLUB / LIVE DRUMPAD
    DUBSTEP_CORAL("Coral Pink", DrumPadCategory.NEON, Color(0xFFFF3366), Color(0xFFFF6B97)),
    DUBSTEP_PURPLE("Neon Purple", DrumPadCategory.NEON, Color(0xFFB829D6), Color(0xFFE879F9)),
    DUBSTEP_BLUE("Vibrant Blue", DrumPadCategory.NEON, Color(0xFF1E88E5), Color(0xFF60A5FA)),
    DUBSTEP_GREEN("Acid Green", DrumPadCategory.NEON, Color(0xFF5CD626), Color(0xFF86EFAC)),
    DUBSTEP_YELLOW("Golden Sun", DrumPadCategory.NEON, Color(0xFFFBC02D), Color(0xFFFEF08A))
}

fun midiChannelForSlot(slotId: Int): Int {
    return when (slotId) {
        in 0..7 -> slotId
        8 -> 8
        9 -> 9
        else -> slotId.coerceIn(0, 15)
    }
}

data class AudioSlot(
    val slotId: Int,           // 0-7 = pistes 1-8, 8 = DrumPad, 9 = Pad
    val midiChannel: Int = midiChannelForSlot(slotId),
    val soundFontId: Int = -1,
    val soundFontPath: String? = null,
    val bank: Int = 0,
    val preset: Int = 0,
    val patchName: String? = null,
    val volume: Float = 0.8f,
    val pan: Float = 0f,
    val presets: List<SoundfontPreset> = emptyList()
) {
    companion object {
        fun midiChannelForSlot(slotId: Int): Int = com.soundstage.mixer.model.midiChannelForSlot(slotId)
    }
}

data class TrackChannel(
    val id: Int,
    val name: String,
    val isMaster: Boolean = false,
    val isEnabled: Boolean = true, // Power On/Off
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volume: Float = 0.78f,
    val pan: Float = 0.0f, // -1.0f (Left) to +1.0f (Right)
    val fxSummary: String = "Fx, EQ...",
    val soundfontName: String = "",
    val patchName: String = "",
    val bank: Int = 0,
    val program: Int = 0,
    // FX Tabs per track
    val reverbPreset: String = "Concert Hall",
    val reverbMix: Float = 0.25f,
    val reverbSize: Float = 0.60f,
    val reverbDecay: Float = 0.45f,
    val velocityCurve: Float = 0.5f, // 0 = Soft, 0.5 = Linear, 1 = Hard
    val splitNoteMin: Int = 24, // C1
    val splitNoteMax: Int = 108, // C7
    // Peak meters (0f..1f)
    val peakMeterL: Float = 0.0f,
    val peakMeterR: Float = 0.0f
)

data class DrumPadItem(
    val id: Int,
    val label: String,
    val soundType: DrumSoundType = DrumSoundType.SAMPLE,
    val sampleFileName: String = "kick_808.wav",
    val sampleFilePath: String = "",
    val sf2Note: String = "C2",
    val sf2NoteOctave: Int = 2,
    val sf2NoteKey: String = "C",
    val isPressed: Boolean = false,
    val colorStyle: DrumPadStyle = DrumPadStyle.GRADIENT_CYAN,
    val isLoopMode: Boolean = false,
    val isLoopPlaying: Boolean = false
)

data class LoopFile(
    val name: String,
    val duration: String,
    val folder: String,
    val path: String = "",
    val bpm: Int = 120,
    val musicalKey: String = "",
    val timeSignature: String = "4/4",
    val startMs: Int = 0,
    val endMs: Int = 0,
    val beats: Int = 0,
    val startStep: Int = 1,
    val endStep: Int = 16
)

data class LoopFolder(
    val name: String,
    val icon: String,
    val files: List<LoopFile>,
    val isOpen: Boolean = false
)

data class TrackSnapshot(
    val id: Int,
    val volume: Float,
    val pan: Float,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isEnabled: Boolean = true,
    val soundfontName: String = "",
    val patchName: String = "",
    val bank: Int = 0,
    val program: Int = 0,
    val transpose: Int = 0,
    val octave: Int = 0,
    val reverbSend: Float = 0f
)

data class SubSceneSnapshot(
    val slotName: String, // "INTRO", "S2", "S3", "S4", "END"
    val tracks: List<TrackSnapshot>,
    val globalTranspose: Int = 0,
    val globalOctaveShift: Int = 0,
    val masterVolume: Float = 0.85f,
    val fxParameters: FxParameters? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ScenePreset(
    val id: String,
    val name: String,
    val timestamp: String,
    val color: Color,
    val snapshots: Map<String, SubSceneSnapshot> = emptyMap()
)

data class SoundfontPreset(
    val id: Int,
    val name: String,
    val bankNumber: Int
)

data class SoundfontBankFile(
    val name: String,
    val path: String,
    val size: String
)

data class MidiDeviceItem(
    val id: String,
    val name: String,
    val type: String = "USB MIDI",
    val isConnected: Boolean = true,
    val isEnabled: Boolean = true
)

data class FxParameters(
    // EQ
    val eqLow: Float = 0.5f,
    val eqMid: Float = 0.5f,
    val eqHigh: Float = 0.5f,
    val eqGain: Float = 0.5f,
    // Reverb
    val isReverbEnabled: Boolean = false,
    // SoundGoodizer
    val isSgEnabled: Boolean = false,
    val sgAmount: Float = 0f,
    val sgMode: Int = 0,
    val reverbPreset: String = "Concert Hall",
    val reverbMix: Float = 0.24f,
    val reverbSize: Float = 0.6f,
    val reverbDecay: Float = 0.45f,
    val reverbDamp: Float = 0.3f,
    // Compressor
    val compThresh: Float = 0.4f,
    val compRatio: Float = 0.5f,
    val compAttack: Float = 0.2f,
    val compRelease: Float = 0.35f,
    // Delay
    val isDelayEnabled: Boolean = false,
    val delayTime: Float = 0.35f,
    val delayFeedback: Float = 0.4f,
    val delayMix: Float = 0.0f,
    val delayPingPong: Float = 0.0f
)
