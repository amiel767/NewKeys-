package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.MuteRed
import com.example.ui.theme.SoloAmber

enum class ActivePopup {
    NONE, DRUM_PAD, TONIC_PAD, SCENE, EFFECTS, SOUNDFONT, STYLE, MIDI
}

enum class SoundGoodizerMode(val label: String, val description: String) {
    A("A - Warm Tube", "Chaleur analogique & saturation harmonique ronde"),
    B("B - Crisp Air", "Exciter d'aigus brillant et dynamique punchy"),
    C("C - Deep Crunch", "Compression multibande lourde & crunch basse"),
    D("D - Hard Limiter", "Impact maximal, mur du son & punch puissant")
}

enum class AppTheme(val displayName: String, val description: String) {
    CYBER_NEON("Cyber Neon (Défaut)", "Teintes sombres avec accents cyan & violet néon"),
    OBSIDIAN_GOLD("Obsidian Gold", "Noir profond avec accents dorés et ambrés luxueux"),
    TOKYO_NIGHT("Tokyo Night", "Bleu nuit profond avec touches rose magenta et indigo"),
    STUDIO_SLATE("Studio Slate", "Gris studio professionnel épuré et minimaliste"),
    OLED_BLACK("OLED Pure Black", "Noir absolu pour économie d'énergie et contraste max")
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
    MY_PEACH("Peach Blossom", DrumPadCategory.MATERIAL_YOU, Color(0xFFFED7AA), Color(0xFFC2410C))
}

data class TrackChannel(
    val id: Int,
    val name: String,
    val isMaster: Boolean = false,
    val isEnabled: Boolean = true, // Power On/Off
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volume: Float = 0.65f,
    val pan: Float = 0.0f, // -1.0f (Left) to +1.0f (Right)
    val fxSummary: String = "Fx, EQ...",
    val soundfontName: String = "", // Empty -> "-"
    val patchName: String = "",
    val bank: Int = 0,
    val program: Int = 0,
    // FX Tabs per track
    val reverbPreset: String = "Concert Hall",
    val reverbMix: Float = 0.25f,
    val reverbSize: Float = 0.60f,
    val reverbDecay: Float = 0.45f,
    val velocityCurve: Float = 0.5f, // 0 = Soft, 0.5 = Linear, 1 = Hard
    val splitNoteMin: Int = 36, // C2
    val splitNoteMax: Int = 84, // C6
    // Peak meters (0f..1f)
    val peakMeterL: Float = 0.0f,
    val peakMeterR: Float = 0.0f
)

data class DrumPadItem(
    val id: Int,
    val label: String,
    val soundType: DrumSoundType = DrumSoundType.SAMPLE,
    val sampleFileName: String = "kick_808.wav",
    val sf2Note: String = "C2",
    val sf2NoteOctave: Int = 2,
    val sf2NoteKey: String = "C",
    val isPressed: Boolean = false,
    val colorStyle: DrumPadStyle = DrumPadStyle.GRADIENT_CYAN
)

data class LoopFile(
    val name: String,
    val duration: String,
    val folder: String,
    val bpm: Int = 120
)

data class LoopFolder(
    val name: String,
    val icon: String,
    val files: List<LoopFile>,
    val isOpen: Boolean = false
)

data class ScenePreset(
    val id: String,
    val name: String,
    val timestamp: String,
    val color: Color
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
    val delayTime: Float = 0.35f,
    val delayFeedback: Float = 0.4f,
    val delayMix: Float = 0.2f,
    val delayPingPong: Float = 0.0f
)
