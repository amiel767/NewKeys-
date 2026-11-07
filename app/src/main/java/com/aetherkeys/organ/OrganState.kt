package com.aetherkeys.organ

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class OrganPercussion(val on: Boolean, val soft: Boolean, val fast: Boolean, val third: Boolean)
data class OrganVibrato(val type: Int, val upper: Boolean, val lower: Boolean)
data class OrganPreset(
    val name: String, val upper: List<Int>, val lower: List<Int>,
    val perc: OrganPercussion, val vib: OrganVibrato, val rotary: Float,
    val click: Float, val tone: Float, val drive: Float, val space: Float, val level: Float, val cabinet: Boolean,
)

enum class OrganKnobId { RotaryRise, RotaryFall, EqTreble, EqMid, EqBass, Drive, ReverbAmount }
enum class OrganFaderId { RotaryDepth, RotaryMic, RotaryHornDrum }

/** Branche ici ton moteur audio (JNI/Oboe). Toutes les méthodes sont optionnelles. */
interface OrganListener {
    fun onDrawbar(index: Int, value: Int) {}                       // 0..8, index 0 = 16'
    fun onPercussion(on: Boolean, soft: Boolean, fast: Boolean, third: Boolean) {}
    fun onVibrato(type: Int) {}                                    // 0=V1 1=C1 2=V2 3=C2 4=V3 5=C3, -1 = aucun
    fun onRotarySpeed(fast: Boolean) {}
    fun onCabinet(on: Boolean) {}
    fun onKnob(id: OrganKnobId, value01: Float) {}
    fun onFader(id: OrganFaderId, value01: Float) {}
    fun onOctave(octave: Int) {}                                   // -2..2
    fun onReverbMode(mode: Float) {}                               // Room 0, Stage .5, Hall 1
    fun onModel(index: Int) {}                                     // 0=B3 1=Pipe 2=Farf
    fun onModelDial(value01: Float) {}
    fun onPresetApplied(preset: OrganPreset) {}

    companion object { val None = object : OrganListener {} }
}

private fun db(s: String) = s.map { it.digitToInt() }
private fun pr(
    name: String, up: String, lo: String, perc: OrganPercussion, vib: OrganVibrato, rotary: Float,
    click: Float, tone: Float, drive: Float, space: Float, cabinet: Boolean = true,
) = OrganPreset(name, db(up), db(lo), perc, vib, rotary, click, tone, drive, space, 0.8f, cabinet)

private val NoPerc = OrganPercussion(false, false, false, false)
private val AllPerc = OrganPercussion(true, true, true, true)
private val VibC = OrganVibrato(3, true, true)
private val VibOff = OrganVibrato(0, false, false)
private val VibV2 = OrganVibrato(1, true, true)

internal val FACTORY_PRESETS = listOf(
    pr("Jazz Organ", "888000000", "808000000", AllPerc, VibC, 0.5f, 0.3f, 0.55f, 0.4f, 0.25f),
    pr("Rock B3", "888888888", "888888888", NoPerc, VibC, 1f, 0.4f, 0.6f, 0.7f, 0.15f),
    pr("Gospel", "808808008", "888800000", OrganPercussion(true, false, true, true), VibC, 1f, 0.35f, 0.6f, 0.4f, 0.3f),
    pr("Blues", "888000000", "808000000", AllPerc, VibC, 0.5f, 0.3f, 0.5f, 0.35f, 0.2f),
    pr("Full Drawbars", "888888888", "888888888", NoPerc, VibOff, 1f, 0.3f, 0.5f, 0.3f, 0.2f),
    pr("Perc Organ", "888000000", "808000000", OrganPercussion(true, false, false, true), VibC, 0.5f, 0.4f, 0.55f, 0.4f, 0.25f),
    pr("Pipe Church", "008800005", "008000000", NoPerc, VibOff, 0f, 0.1f, 0.4f, 0.1f, 0.6f, false),
    pr("Pipe Cathedral", "808605004", "808000000", NoPerc, VibOff, 0f, 0.1f, 0.35f, 0.1f, 0.8f, false),
    pr("Farf Jazz", "888000000", "808000000", NoPerc, VibV2, 0.5f, 0.2f, 0.65f, 0.3f, 0.2f),
    pr("Farf Rock", "888888888", "888888888", NoPerc, VibV2, 1f, 0.3f, 0.7f, 0.6f, 0.15f),
    pr("Full Organ", "888880000", "888880000", NoPerc, VibOff, 0.5f, 0.25f, 0.5f, 0.25f, 0.3f),
    pr("Soft Strings", "008880050", "008800000", NoPerc, VibOff, 0f, 0.1f, 0.4f, 0.15f, 0.5f, false),
    pr("Jazz Variation", "888000006", "808000000", AllPerc, VibC, 0.5f, 0.3f, 0.55f, 0.35f, 0.25f),
    pr("Church", "808808008", "888800000", NoPerc, VibOff, 0f, 0.15f, 0.45f, 0.15f, 0.7f, false),
    pr("Gospel Shout", "888808008", "888888888", OrganPercussion(true, false, true, true), VibC, 1f, 0.4f, 0.6f, 0.5f, 0.25f),
    pr("Mellow Pad", "008800000", "008800000", NoPerc, VibOff, 0.5f, 0.1f, 0.4f, 0.1f, 0.5f),
)

private val INITIAL_USER = listOf(
    pr("My Setup", "888000000", "808000000", NoPerc, VibC, 0.5f, 0.3f, 0.5f, 0.3f, 0.2f),
    pr("Custom 1", "888800000", "888000000", AllPerc, VibC, 1f, 0.3f, 0.6f, 0.4f, 0.3f),
)

class OrganState {
    var listener: OrganListener = OrganListener.None

    var octave by mutableIntStateOf(0)
    var vibMasterOn by mutableStateOf(true)
    var percMasterOn by mutableStateOf(true)
    var vibGroupDisabled by mutableStateOf(false)
    var percGroupDisabled by mutableStateOf(false)
    var vibActive by mutableIntStateOf(0)                 // -1 = aucun
    var percSoft by mutableStateOf(false)
    var percFast by mutableStateOf(false)
    var percThird by mutableStateOf(false)
    var cabinetOn by mutableStateOf(true)
    var speedFast by mutableStateOf(true)

    val drawbars = mutableStateListOf(0, 0, 0, 0, 0, 0, 0, 0, 0)
    var drawbarsEpoch by mutableIntStateOf(0)             // incrémenté quand un preset repositionne les thumbs

    var rise by mutableFloatStateOf(30f)
    var fall by mutableFloatStateOf(40f)
    var treble by mutableFloatStateOf(50f)
    var mid by mutableFloatStateOf(50f)
    var bass by mutableFloatStateOf(50f)
    var drive by mutableFloatStateOf(50f)
    var reverbAmount by mutableFloatStateOf(30f)
    var depth by mutableFloatStateOf(80f)
    var mic by mutableFloatStateOf(50f)
    var balance by mutableFloatStateOf(50f)
    var modelDial by mutableFloatStateOf(50f)
    var modelIndex by mutableIntStateOf(0)
    var reverbMode by mutableIntStateOf(0)

    // ---- presets ----
    var presetUserMode by mutableStateOf(false)
    var factoryIndex by mutableIntStateOf(0)
    var userIndex by mutableIntStateOf(0)
    val userPresets = mutableStateListOf<OrganPreset>().apply { addAll(INITIAL_USER) }
    var dropdownOpen by mutableStateOf(false)
    var editingName by mutableStateOf(false)
    var flashTick by mutableIntStateOf(0)
    private var customCounter = 2
    private var last: OrganPreset = FACTORY_PRESETS[0]
    private var engineTone = 0.55f
    private var engineDrive = 0.4f

    val currentList get() = if (presetUserMode) userPresets.toList() else FACTORY_PRESETS
    val currentIndex get() = if (presetUserMode) userIndex else factoryIndex
    val currentPreset get() = currentList[currentIndex.coerceIn(0, currentList.size - 1)]

    init { applyCurrent() }

    fun applyCurrent() = applyPreset(currentPreset)

    fun applyPreset(p: OrganPreset) {
        last = p; engineTone = p.tone; engineDrive = p.drive
        for (i in 0 until 9) drawbars[i] = p.upper[i]
        drawbarsEpoch++
        vibActive = p.vib.type
        percSoft = p.perc.soft; percFast = p.perc.fast; percThird = p.perc.third
        speedFast = p.rotary >= 1f
        cabinetOn = p.cabinet
        vibGroupDisabled = false; percGroupDisabled = false
        listener.onPresetApplied(p)
    }

    fun selectPreset(i: Int) { if (presetUserMode) userIndex = i else factoryIndex = i; applyCurrent(); dropdownOpen = false }
    fun nextPreset() { if (editingName) return; val n = currentList.size; selectIdx((currentIndex + 1) % n) }
    fun prevPreset() { if (editingName) return; val n = currentList.size; selectIdx((currentIndex - 1 + n) % n) }
    private fun selectIdx(i: Int) { if (presetUserMode) userIndex = i else factoryIndex = i; applyCurrent() }
    fun switchMode(user: Boolean) { if (editingName) return; presetUserMode = user; dropdownOpen = false; applyCurrent() }

    fun savePreset() {
        if (!presetUserMode) return
        userPresets.add(
            OrganPreset(
                "Custom ${customCounter++}", drawbars.toList(), last.lower,
                OrganPercussion(percMasterOn, percSoft, percFast, percThird),
                OrganVibrato(if (vibActive < 0) 0 else vibActive, last.vib.upper, last.vib.lower),
                if (speedFast) 1f else 0.5f, last.click, engineTone, engineDrive, last.space, last.level, true,
            ),
        )
        userIndex = userPresets.size - 1
        applyCurrent(); flashTick++
    }

    fun renameCurrentUser(name: String) {
        if (!presetUserMode) return
        userPresets[userIndex] = userPresets[userIndex].copy(name = name)
        applyCurrent()
    }

    // ---- actions UI ----
    fun changeOct(d: Int) { octave = (octave + d).coerceIn(-2, 2); listener.onOctave(octave) }
    fun selectVibrato(i: Int) { vibActive = if (vibActive == i) -1 else i; listener.onVibrato(vibActive) }
    fun setDrawbar(i: Int, v: Int) { drawbars[i] = v; listener.onDrawbar(i, v) }
    fun toggleVibMaster() { vibMasterOn = !vibMasterOn; vibGroupDisabled = !vibMasterOn }
    fun togglePercMaster() {
        percMasterOn = !percMasterOn; percGroupDisabled = !percMasterOn
        listener.onPercussion(percMasterOn, percSoft, percFast, percThird)
    }
    fun togglePerc(which: Int) {
        when (which) { 0 -> percSoft = !percSoft; 1 -> percFast = !percFast; else -> percThird = !percThird }
        listener.onPercussion(percMasterOn, percSoft, percFast, percThird)
    }
    fun toggleCabinet() { cabinetOn = !cabinetOn; listener.onCabinet(cabinetOn) }
    fun toggleSpeed() { speedFast = !speedFast; listener.onRotarySpeed(speedFast) }
    fun chooseReverbMode(i: Int) { reverbMode = i; listener.onReverbMode(if (i == 0) 0f else if (i == 1) 0.5f else 1f) }
    fun cycleModel() { modelIndex = (modelIndex + 1) % 3; listener.onModel(modelIndex) }

    fun setKnob(id: OrganKnobId, v: Float) {
        when (id) {
            OrganKnobId.RotaryRise -> rise = v
            OrganKnobId.RotaryFall -> fall = v
            OrganKnobId.EqTreble -> { treble = v; engineTone = v / 100f }
            OrganKnobId.EqMid -> { mid = v; engineTone = v / 100f }
            OrganKnobId.EqBass -> { bass = v; engineTone = v / 100f }
            OrganKnobId.Drive -> { drive = v; engineDrive = v / 100f }
            OrganKnobId.ReverbAmount -> reverbAmount = v
        }
        listener.onKnob(id, v / 100f)
    }
    fun setFader(id: OrganFaderId, v: Float) {
        when (id) { OrganFaderId.RotaryDepth -> depth = v; OrganFaderId.RotaryMic -> mic = v; OrganFaderId.RotaryHornDrum -> balance = v }
        listener.onFader(id, v / 100f)
    }
    fun turnModelDial(v: Float) { modelDial = v; listener.onModelDial(v / 100f) }
}
