package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.MuteRed
import com.example.ui.theme.SoloAmber

enum class ActivePopup {
    NONE, DRUM_PAD, TONIC_PAD, SCENE, EFFECTS, SOUNDFONT
}

enum class DrumSoundType {
    SAMPLE, SF2_NOTE
}

data class TrackChannel(
    val id: Int,
    val name: String,
    val isMaster: Boolean = false,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volume: Float = 0.65f,
    val fxSummary: String = "Fx, EQ...",
    val soundfontName: String = "FluidR3-Mono.sf2",
    val patchName: String = "Grand Piano",
    val bank: Int = 0,
    val program: Int = 0
)

data class DrumPadItem(
    val id: Int,
    val label: String,
    val soundType: DrumSoundType = DrumSoundType.SAMPLE,
    val sampleFileName: String = "kick_808.wav",
    val sf2Note: String = "C2", // e.g. C1 to C8
    val sf2NoteOctave: Int = 2,
    val sf2NoteKey: String = "C",
    val isPressed: Boolean = false
)

data class LoopFile(
    val name: String,
    val duration: String,
    val folder: String
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

data class FxParameters(
    // EQ
    val eqLow: Float = 0.5f,
    val eqMid: Float = 0.5f,
    val eqHigh: Float = 0.5f,
    val eqGain: Float = 0.5f,
    // Reverb
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
