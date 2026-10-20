package com.soundstage.mixer.fx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soundstage.mixer.audio.NativeAudioBridge

/**
 * Mirrors the native FxMixer state for the Compose UI and pushes every change to
 * the JNI bridge. Strips follow FxMixer: 0..7 tracks, 8 drum channel, 9 tonic pad,
 * 10 drum-pad sampler, 15 master (see [FxRegistry.STRIP_MASTER]).
 *
 * All setters are control-thread calls and cheap enough for a slider drag.
 */
class FxController(private val strip: Int) {
    /** Instrument id on this strip, -1 when the soundfont plays as it is. */
    var instrumentId by mutableStateOf(-1)
        private set

    /** Slot -> effect id, -1 when empty. Sized for the strip's slot count. */
    val effectIds = mutableStateListOf<Int>()

    /** Slot -> bypass. */
    val bypassed = mutableStateListOf<Boolean>()

    /** Slot -> parameter values, normalized 0..1. */
    val params = mutableStateListOf<MutableList<Float>>()

    val slotCount: Int
        get() = if (strip == FxRegistry.STRIP_MASTER) FxRegistry.SLOTS_MASTER else FxRegistry.SLOTS_PER_TRACK

    init {
        repeat(slotCount) {
            effectIds.add(-1)
            bypassed.add(false)
            params.add(MutableList(FxRegistry.MAX_PARAMS) { 0f })
        }
    }

    private fun bridge() = if (NativeAudioBridge.isLibraryLoaded) NativeAudioBridge else null

    // ---- instrument ----

    fun setInstrument(id: Int) {
        val b = bridge() ?: return
        if (!b.setTrackInstrument(strip, id)) return
        instrumentId = id
    }

    fun setInstrumentParam(param: Int, norm: Float) {
        if (instrumentId < 0) return
        paramsOfInstrument(param)[param] = norm
        bridge()?.setTrackInstrumentParam(strip, param, norm)
    }

    private val instrumentParams = MutableList(FxRegistry.MAX_PARAMS) { 0f }
    private fun paramsOfInstrument(param: Int) = instrumentParams

    fun applyInstrumentPreset(preset: Int) {
        val b = bridge() ?: return
        if (!b.fxApplyInstrumentPreset(strip, preset)) return
        FxRegistry.info(instrumentId).presets.getOrNull(preset)?.values?.forEachIndexed { i, v ->
            if (i < instrumentParams.size) instrumentParams[i] = v
        }
    }

    // ---- effects ----

    /** Installs [id] in [slot]. Returns false when the id is not valid for the slot. */
    fun setEffect(slot: Int, id: Int): Boolean {
        val b = bridge() ?: return false
        if (!b.setFxEffect(strip, slot, id)) return false
        effectIds[slot] = id
        bypassed[slot] = false
        params[slot] = MutableList(FxRegistry.MAX_PARAMS) { i ->
            if (id >= 0) FxRegistry.info(id).params.getOrNull(i)?.default ?: 0f else 0f
        }
        b.fxCollectGarbage()
        return true
    }

    fun setEffectParam(slot: Int, param: Int, norm: Float) {
        if (slot !in effectIds.indices || effectIds[slot] < 0) return
        params[slot][param] = norm
        bridge()?.setFxEffectParam(strip, slot, param, norm)
    }

    fun setBypass(slot: Int, bypass: Boolean) {
        if (slot !in bypassed.indices) return
        bypassed[slot] = bypass
        bridge()?.setFxEffectBypass(strip, slot, bypass)
    }

    fun applyPreset(slot: Int, preset: Int): Boolean {
        val b = bridge() ?: return false
        if (!b.applyFxPreset(strip, slot, preset)) return false
        val id = effectIds.getOrNull(slot) ?: return true
        FxRegistry.info(id).presets.getOrNull(preset)?.values?.forEachIndexed { i, v ->
            if (i < params[slot].size) params[slot][i] = v
        }
        return true
    }

    // ---- strip ----

    fun setGain(lin: Float) = bridge()?.setFxStripGain(strip, lin)
    fun setPan(pan: Float) = bridge()?.setFxStripPan(strip, pan)
    fun setMute(mute: Boolean) = bridge()?.setFxStripMute(strip, mute)

    /** Peak level 0..1 for [channel] (0 = left, 1 = right). Safe to poll at 30 Hz. */
    fun peak(channel: Int): Float = bridge()?.fxStripPeak(strip, channel) ?: 0f

    /** Effect meter for the vu-meters; safe to poll at 30 Hz. */
    fun meter(slot: Int, index: Int): Float = bridge()?.fxGetMeter(strip, slot, index) ?: 0f

    /** Soundfont is silenced while a source instrument is selected. */
    fun soundfontPaused(): Boolean = bridge()?.fxSoundfontPaused(strip) ?: false
}
