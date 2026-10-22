package com.aetherkeys.fx.ui

/**
 * Everything the UI asks of the audio engine. The UI never calls native code directly.
 * Implement once in the app (JNI bridge). Indices are the engine's parameter indices (0..31).
 * Strips: 0..7 tracks, 8 drum channel, 9 tonic pad, 10 drum-pad sampler, 15 master.
 * Every method returns false (or does nothing) when the engine refuses; the store then shows a notice.
 */
interface FxEngine {
    fun setEffect(strip: Int, slot: Int, id: Int): Boolean
    fun setInstrument(strip: Int, id: Int): Boolean
    fun setParam(strip: Int, slot: Int, index: Int, value: Float)
    fun setInstrumentParam(strip: Int, index: Int, value: Float)
    fun setBypass(strip: Int, slot: Int, bypassed: Boolean)
    fun applyPreset(strip: Int, slot: Int, presetIndex: Int): Boolean
    fun meter(strip: Int, slot: Int, index: Int): Float
}
