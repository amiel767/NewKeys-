package com.soundstage.mixer.instruments

import androidx.compose.runtime.*
import com.soundstage.mixer.model.AetherTrackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

/** The Æther Instruments. [fxId] = id of the native Source in FxRegistry (6 organ, 14 synth, 15 leads, 16 sax). */
enum class AeInstrument(val fxId: Int, val title: String, val paramCount: Int) {
    ORGAN(6, "Æther Organ", 31), SYNTH(14, "Æther Synth", 33), LEADS(15, "Æther Leads", 33), SAX(16, "Æther Sax", 23),
    // 17 = contrat du moteur (fx_piano.h, kParams).  L'index 12 (ex-DRIVE) est
    // recycle en bruit de touche, 13 (ex-PUNCH) est libre, 16 est le bruit de
    // marteau.  AMP et PUNCH sont retires du moteur : voir l'en-tete de fx_piano.h.
    // 17/18/19 = EQ 3 BANDES (medium, frequence du medium, aigus) : l'ancien EQ
    // tenait sur un seul potard qui liait les trois bandes.
    PIANO(17, "Æther Piano", 20),
    /**
     * Meme moteur que le piano (`fx_piano.h`, id 17), mais sa propre SECTION : les
     * quatre modeles electriques y vivent seuls, sous leur propre carte.  Le piano
     * acoustique et l'electrique ne partagent plus une meme liste — un Steinway et
     * un tine ne se cherchent pas au meme endroit.
     *
     * L'index de preset est LOCAL a la section (0..3) ; c'est [AeBanks.modelIndex]
     * qui le ramene a l'index global de `AePianoModels.all` (8..11), seul connu du
     * moteur et des banques.
     */
    KEYBOARDS(17, "Æther Keyboards", 20);
    /** Parameters that switch instantly on a preset change (the others glide). */
    val discrete: IntArray get() = when (this) {
        ORGAN -> intArrayOf(18, 19, 20, 21, 22, 23, 24, 25)
        SYNTH -> intArrayOf(11, 18, 19, 26)
        LEADS -> intArrayOf(5, 6, 7, 8, 9, 17, 28)
        SAX -> intArrayOf(0, 1, 2, 3)
        // 15 = MODEL : le timbre doit basculer d'un coup, pas glisser.
        // 18 = frequence du medium : un filtre qu'on regle en glissant siffle, donc
        // on le bascule d'un coup comme le modele.
        PIANO, KEYBOARDS -> intArrayOf(8, 15, 18)
    }
}

/**
 * Un preset. [slot] est la memoire A ou B ou l'utilisateur l'a range : le preset
 * s'en souvient (il est ecrit dans son propre enregistrement), donc recharger une
 * liste rend le rangement tel qu'il a ete fait. Les presets d'usine sont tous en A.
 */
class AePreset(val name: String, val category: String, val values: FloatArray, val slot: String = SLOT_A) {
    companion object { const val SLOT_A = "A"; const val SLOT_B = "B" }
}

/** Knobs, preset index and page of an unloaded instrument, kept so reloading it is not a reset. */
class SavedAeState(val values: FloatArray, val preset: Int, val page: Int)

/** Value text shown under the knobs. Same formulas as the validated preview. */
object AeFmt {
    private val wn = arrayOf("SIN", "TRI", "SAW", "SQR")
    private fun pct(v: Float) = "${(v * 100).toInt()}%"
    private fun hz(f: Float) = if (f >= 1000f) "${"%.1f".format(f / 1000f)}kHz" else "${f.toInt()}Hz"
    fun format(inst: AeInstrument, i: Int, v: Float): String = when (inst) {
        AeInstrument.LEADS -> leads(i, v)
        AeInstrument.SAX -> sax(i, v)
        AeInstrument.PIANO, AeInstrument.KEYBOARDS -> piano(i, v)
        else -> classic(inst, i, v)
    }
    private fun leads(i: Int, v: Float): String = when (i) {
        11 -> "${(v * 50).toInt()}ct"; 12 -> "%.2fs".format(0.002f + v * v * 2.5f); 13 -> "%.2fs".format(v * v * 1.5f)
        14 -> "%.2fs".format(0.01f + v * v * 3f); 16 -> "%.2fs".format(0.01f + v * v * 4f); 18 -> hz(40f * 500f.pow(v))
        21 -> "${(60 + v * v * 840).toInt()}ms"; 27 -> "${((v - 0.5f) * 18).toInt()}dB"; 29 -> "${(v * v * 1200).toInt()}ms"; 31 -> "${(-40 + v * 46).toInt()}dB"
        else -> pct(v)
    }
    private fun sax(i: Int, v: Float): String = when (i) {
        13 -> "%.1fHz".format(3.5f + v * 4f); 14 -> "${(v * 800).toInt()}ms"; 15 -> "${(v * v * 400).toInt()}ms"; 20 -> "${(-40 + v * 46).toInt()}dB"
        21 -> "${(v * 100).toInt()}%"
        else -> pct(v)
    }
    private fun piano(i: Int, v: Float): String = when (i) {
        7 -> "${(-40 + v * 46).toInt()}dB"                       // LEVEL
        8 -> "${(80 + v * 550).toInt()}ms"                       // RELEASE
        9 -> if (v < 0.4f) "Hard" else if (v < 0.6f) "Linear" else "Soft"
        3 -> if (v < 0.33f) "Closed" else if (v < 0.66f) "Half" else "Open"
        4 -> if (v < 0.34f) "Under" else if (v < 0.67f) "Front" else "Over"
        11 -> if (v < 0.34f) "Near" else if (v < 0.67f) "Mid" else "Far"
        14, 17, 19 -> "${((v - 0.5f) * 24f).toInt()}dB"          // EQ LOW / MID / HIGH
        18 -> hz(300f * 9f.pow(v))                               // EQ FREQ (medium)
        else -> pct(v)
    }
    private fun classic(inst: AeInstrument, i: Int, v: Float): String = if (inst == AeInstrument.ORGAN) {
        when (i) { 26, 27, 28, 29 -> pct(v); 30 -> "${(-30 + v * 36).toInt()}dB"; else -> "" }
    } else when (i) {
        0, 1 -> wn[(v * 3).roundToInt().coerceIn(0, 3)]
        2 -> "${(v * 50).toInt()}ct"
        3, 4, 6, 8, 9, 10, 17, 21, 22, 23, 24, 25 -> pct(v)
        5 -> "×${1 + (v * 2).roundToInt()}"
        7 -> { val f = 20f * 1000f.pow(v); if (f >= 1000f) "${"%.1f".format(f / 1000f)}kHz" else "${f.toInt()}Hz" }
        16, 20 -> "${"%.1f".format(0.1f * 100f.pow(v))}Hz"
        27 -> "${(v * v * 1500).toInt()}ms"
        28 -> "${1 + (v * 7).roundToInt()}"
        29 -> "${(v * 12).roundToInt()}st"
        30 -> "${((v - 0.5f) * 200).toInt()}ct"
        31 -> "${(-40 + v * 46).toInt()}dB"
        else -> ""
    }
}

/**
 * What the app must provide. Maps 1:1 onto the native FxMixer facade (fx_mixer.h):
 *   loadInstrument -> setInstrument(strip, fxId)   unloadInstrument -> setInstrument(strip, -1)
 *   setParam -> setInstrumentParam(strip, index, norm)   noteOn/noteOff/allNotesOff -> same names on FxMixer
 * Everything must be safe to call from the UI thread (post to the audio thread inside the JNI layer).
 */
interface AeHost {
    fun loadInstrument(strip: Int, fxId: Int): Boolean
    fun unloadInstrument(strip: Int)
    fun setParam(strip: Int, index: Int, value: Float)
    fun noteOn(strip: Int, note: Int, velocity: Int)
    fun noteOff(strip: Int, note: Int)
    fun allNotesOff(strip: Int)
    /** Output level 0..1 of the strip's instrument, for the animations. Return 0f if unavailable. */
    fun level(strip: Int): Float = 0f
    /** Optional: copy the last output samples (mono, -1..1) for the Leads oscilloscope. Return false if unavailable (a synthetic trace is drawn). */
    fun scope(strip: Int, out: FloatArray): Boolean = false

    /**
     * Load the sampled bank that goes with an instrument, if it has one. Only the sax does
     * today; every other instrument returns false and stays purely synthetic.
     *
     * How much of the output the samples carry is parameter 22 (the Blend knob), pushed by the
     * manager with the rest of the preset, not a separate argument here.
     */
    fun loadBank(strip: Int, instrument: AeInstrument, type: Int, model: Int = -1): Boolean = false

    /** Mix of samples in the instrument output, 0 = pure synthesis. For the UI readback. */
    fun bankMix(strip: Int): Float = 0f

    /**
     * Drop the Kotlin-side copy of the bank. The native side already copied it at load time,
     * so keeping the ByteArray holds the whole pack (12 MB piano, 7 MB of sax packs) for the
     * rest of the session for nothing.
     */
    fun releaseBank(instrument: AeInstrument, type: Int, model: Int = -1) {}

    /**
     * Reclaim the native memory the unloaded source held. Detaching a source only *retires*
     * it — the audio thread deletes it on a later block — so the host has to run the
     * collector and return the freed pages, otherwise the sample bank stays resident.
     */
    fun releaseNative() {}
}

/** One loaded instrument on one strip. Lives in [AeManager], so it survives the window being closed. */
class AeSession(val strip: Int, val instrument: AeInstrument, init: FloatArray) {
    val values = mutableStateListOf<Float>().also { l -> init.forEach { l.add(it) } }
    var preset by mutableIntStateOf(0)
    var page by mutableIntStateOf(0)
    var level by mutableFloatStateOf(0f)
    var tweening by mutableStateOf(false)
    var lastNote = 60
    /** True when a sampled bank was loaded on top of the synthesis (sax). */
    var bankLoaded by mutableStateOf(false)
    /** Sampled bank currently loaded (-1 = none). Each sax type has its own. */
    var bankType = -1
    val held = mutableStateMapOf<Int, Boolean>()
    /**
     * Bumped on every note the engine routed here. It is Compose state, not a plain
     * counter, because the window's animation loop sleeps on it while idle: a plain
     * field would never wake that wait and the first key press would wait for the
     * timeout, which is exactly the lag the interface showed.
     */
    val noteEvents = mutableIntStateOf(0)
}

/**
 * App-scoped (create once, e.g. in the Application or a singleton ViewModel). Selecting an instrument loads it
 * immediately; closing the window does not unload it; [unload] does. MIDI enters through the onMidi* functions.
 */
class AeManager(private val host: AeHost, private val scope: CoroutineScope) {
    val sessions = mutableStateMapOf<Int, AeSession>()
    private val tweens = HashMap<Int, Job>()
    private val sustain = HashMap<Int, Boolean>()
    private val pending = HashMap<Int, MutableSet<Int>>()
    /** Knobs/preset/page of an instrument that was unloaded, so reloading it is not a reset. */
    private val savedState = HashMap<Pair<Int, AeInstrument>, SavedAeState>()

    /** Default MIDI CC map (editable). Value = parameter index. CC1 on the organ drives the Leslie speed. */
    val ccOrgan = mutableMapOf(74 to 27, 91 to 29, 7 to 30, 71 to 28)
    val ccSynth = mutableMapOf(74 to 7, 71 to 8, 73 to 12, 72 to 15, 1 to 23, 91 to 25, 7 to 31, 5 to 27)
    val ccLeads = mutableMapOf(74 to 18, 71 to 19, 73 to 12, 72 to 16, 91 to 25, 1 to 30, 7 to 31, 5 to 29)
    /** Sax: CC2 = breath controller, CC11 = expression (level), CC1 = vibrato depth. */
    val ccSax = mutableMapOf(2 to 4, 11 to 20, 7 to 20, 1 to 12, 74 to 19, 91 to 16)
    // Piano : les CC tombent sur le VRAI parametre du contrat.
    //   74 brillance -> TONE (1)      1 roue de modulation -> TONE (1)
    //   7  volume    -> LEVEL (7)     91 reverb -> SPACE (5)
    //   5  portamento-> MIC POS (4)   72 release -> RELEASE (8)
    // Le sustain (64) est traite avant cette table, dans `onMidiCc`.
    val ccPiano = mutableMapOf(74 to 1, 1 to 1, 7 to 7, 91 to 5, 5 to 4, 72 to 8)

    fun factoryPresets(i: AeInstrument): List<AePreset> = when (i) { AeInstrument.ORGAN -> AePresets.organ; AeInstrument.SYNTH -> AePresets.synth; AeInstrument.LEADS -> AePresets.leads; AeInstrument.SAX -> AePresets.sax; AeInstrument.PIANO -> AePresets.piano; AeInstrument.KEYBOARDS -> AePresets.keyboards }

    /**
     * Index GLOBAL du modele a charger pour une session : c'est lui, et lui seul, que
     * connaissent le moteur et les banques.  Pour le piano et les claviers, l'index
     * de preset est celui de la SECTION (0..7, 0..3) ; les autres instruments n'ont
     * qu'une banque et passent leur index tel quel.
     */
    private fun modelOf(s: AeSession): Int =
        if (AeSections.isKeyboardLike(s.instrument)) AeBanks.modelIndex(s.instrument, s.preset, s.values) else s.preset

    /**
     * Factory presets followed by the user's own, so a saved preset indexes like any other.
     *
     * [AeUserStore] holds them in Compose snapshot state, so saving one recomposes the
     * open list without any explicit invalidation.
     */
    fun presets(i: AeInstrument): List<AePreset> = factoryPresets(i) + AeUserStore.load(i)

    /**
     * Name of the preset a strip currently plays, or null when no Æther instrument is loaded.
     * Read by the mixer's name plate: the preset is the strip's identity once an Æther
     * instrument occupies it, exactly as a soundfont's patch name is for a FluidSynth track.
     *
     * The sax is the exception: it has no preset UI (its window hides the preset chip), so
     * showing one of its factory preset names would name something the user cannot change.
     * It reads just "Sax".
     */
    fun presetNameOn(strip: Int): String? {
        val s = sessions[strip] ?: return null
        if (s.instrument == AeInstrument.SAX) return "Sax"
        return presets(s.instrument).getOrNull(s.preset)?.name
    }

    /** Value the session's current preset gives to [index], for the double-tap reset on a knob. */
    fun presetValue(strip: Int, index: Int): Float? {
        val s = sessions[strip] ?: return null
        return presets(s.instrument).getOrNull(s.preset)?.values?.getOrNull(index)
    }

    /**
     * Grows a preset to the instrument's current parameter count, filling the new
     * tail with 0.5. Presets were authored against an earlier count (32) and the
     * voice-mode intensity is parameter 32: without this the extra knob would be
     * missing from the session and every write to it would be silently dropped.
     * 0.5 is the neutral value, so an old preset still sounds exactly as before.
     */
    /**
     * Complete un tableau de valeurs a la taille du contrat de l'instrument.
     *
     * Les indices manquants prennent la valeur du **premier preset d'usine**, pas
     * 0,5.  Un parametre ajoute en fin de contrat a un sens par defaut qui depend
     * de l'instrument : le Blend du sax vaut 1,0 (les samples portent la voix).
     * Le remplir a 0,5 coupait le pack de moitie sur tout preset enregistre avant
     * l'ajout du parametre — la regression « les samples sont passes ou ? ».
     */
    private fun padded(inst: AeInstrument, v: FloatArray): FloatArray {
        if (v.size == inst.paramCount) return v
        val ref = factoryPresets(inst).firstOrNull()?.values
        return FloatArray(inst.paramCount) { i ->
            when {
                i < v.size -> v[i]
                ref != null && i < ref.size -> ref[i]
                else -> 0.5f
            }
        }
    }

    /** Saves the session's CURRENT knob values as a named user preset and selects it. */
    fun saveUserPreset(strip: Int, name: String, slot: String = AePreset.SLOT_A) {
        val s = sessions[strip] ?: return
        val clean = name.trim().ifBlank { "My ${s.instrument.title.removePrefix("Æther ")}" }
        val userIndex = AeUserStore.append(s.instrument, clean, s.values.toFloatArray(), slot)
        s.preset = factoryPresets(s.instrument).size + userIndex
    }

    /** Range un preset utilisateur dans la memoire A ou B. Le choix est persiste avec lui. */
    fun setSlot(strip: Int, index: Int, slot: String) {
        val s = sessions[strip] ?: return
        val ui = index - factoryPresets(s.instrument).size
        if (ui < 0) return
        AeUserStore.setSlot(s.instrument, ui, slot)
    }

    /** Memoire A ou B d'un preset, pour l'affichage. */
    fun slotOf(strip: Int, index: Int): String {
        val s = sessions[strip] ?: return AePreset.SLOT_A
        return presets(s.instrument).getOrNull(index)?.slot ?: AePreset.SLOT_A
    }

    /** Deletes a user preset. Factory presets are never touched. */
    fun deleteUserPreset(inst: AeInstrument, index: Int) {
        val base = factoryPresets(inst).size
        val ui = index - base
        if (ui < 0) return
        AeUserStore.delete(inst, ui)
    }

    /** Starts the instrument now (audio is live as soon as this returns true). Re-selecting the same one keeps its state. */
    fun load(strip: Int, inst: AeInstrument): AeSession? {
        sessions[strip]?.let { if (it.instrument == inst) return it else unload(strip) }
        if (!host.loadInstrument(strip, inst.fxId)) return null
        // Reopening an instrument restores what the user left: unloading a strip is not a reset,
        // so the knobs, the preset and the page come back exactly as they were.
        val saved = savedState.remove(strip to inst)
        val init = padded(inst, saved?.values ?: presets(inst)[0].values)
        val s = AeSession(strip, inst, init)
        saved?.let { s.preset = it.preset; s.page = it.page }
        sessions[strip] = s
        init.forEachIndexed { i, v -> host.setParam(strip, i, v) }
        // Hybrid layer, when the instrument ships one (only the sax today). It is a layer,
        // not a replacement: the synthesis stays underneath and covers the sample seams.
        // The bank is optional, so a failure here must not abort the load.
        val type = (init.firstOrNull() ?: 0f).let { (it * 3f).roundToInt() }
        s.bankLoaded = host.loadBank(strip, inst, type, modelOf(s))
        // bankType identifie la BANQUE chargee : le type de sax, ou l'identite de la
        // banque du piano (0 queue, 1 droit, 8..11 electriques).  Confondre deux
        // banques ferait croire au piano qu'il a deja la bonne.
        if (s.bankLoaded) s.bankType = if (AeSections.isKeyboardLike(inst)) AeBanks.pianoBankId(modelOf(s)) else type
        return s
    }

    fun unload(strip: Int) {
        tweens.remove(strip)?.cancel()
        sessions.remove(strip)?.let { s ->
            savedState[strip to s.instrument] = SavedAeState(s.values.toFloatArray(), s.preset, s.page)
            host.allNotesOff(strip); host.unloadInstrument(strip)
            // Meme identifiants que loadBank() : le type de sax (param 0) et, pour le
            // piano, l'index de preset qui designe la banque (8 = electrique).
            host.releaseBank(s.instrument, s.bankType.coerceAtLeast(0), s.preset)
        }
        sustain.remove(strip); pending.remove(strip)
        // Only after the source is detached and its Kotlin copy dropped. The delete itself
        // happens on the audio thread a few blocks later, hence the delayed collector run.
        host.releaseNative()
    }

    fun setParam(strip: Int, index: Int, value: Float) {
        val s = sessions[strip] ?: return
        if (index !in s.values.indices) return
        val v = value.coerceIn(0f, 1f)
        s.values[index] = v
        host.setParam(strip, index, v)
        // Parameter 0 of the sax picks the instrument type, and every type has its own
        // bank. Without a reload the samples would keep playing the previous type while
        // the synthesis moved to the new one.
        if (s.instrument == AeInstrument.SAX && index == 0) {
            val t = (v * 3f).roundToInt().coerceIn(0, 3)
            if (t != s.bankType) {
                s.bankLoaded = host.loadBank(strip, s.instrument, t)
                s.bankType = if (s.bankLoaded) t else -1
            }
        }
        // Piano : chaque modele acoustique peut porter une AUTRE banque (queue ou
        // droit), et chaque modele electrique la sienne.  La comparaison porte donc
        // sur `want` (l'identite de la banque) : sans ca, passer de Studio a Cottage
        // ne rechargeait rien et les deux jouaient le meme echantillon.
        if (AeSections.isKeyboardLike(s.instrument)) {
            val want = AeBanks.pianoBankId(modelOf(s))
            if (want != s.bankType) {
                s.bankLoaded = host.loadBank(strip, s.instrument, 0, modelOf(s))
                s.bankType = if (s.bankLoaded) want else -1
            }
        }
    }

    /**
     * True when a knob differs from the preset that is selected.
     *
     * The save button is shown only then: a floppy that is always there invites a save that
     * would duplicate the preset it came from, and it never told the user whether anything
     * had actually been touched. Reading the preset back is what makes the button honest —
     * it lights up on a real edit and goes out once the edit is saved or the preset reloaded.
     */
    fun isModified(strip: Int): Boolean {
        val s = sessions[strip] ?: return false
        // Mid-tween the values are in flight towards the preset, so "modified" has no
        // meaning and the button would blink for the 420 ms of the slide.
        if (s.tweening) return false
        val p = presets(s.instrument).getOrNull(s.preset) ?: return false
        val ref = padded(s.instrument, p.values)
        return s.values.indices.any { kotlin.math.abs(s.values[it] - ref[it]) > 1e-4f }
    }

    fun loadPreset(strip: Int, index: Int) {
        val s = sessions[strip] ?: return
        val list = presets(s.instrument)
        val n = ((index % list.size) + list.size) % list.size
        s.preset = n
        val to = padded(s.instrument, list[n].values)
        val from = s.values.toFloatArray()
        val disc = s.instrument.discrete
        disc.forEach { setParam(strip, it, to[it]) }
        tweens.remove(strip)?.cancel()
        s.tweening = true
        tweens[strip] = scope.launch {
            val t0 = System.nanoTime()
            while (true) {
                val u = ((System.nanoTime() - t0) / 1e6f / 420f).coerceIn(0f, 1f)
                val e = 1f - (1f - u) * (1f - u) * (1f - u)
                for (k in to.indices) if (k !in disc) setParam(strip, k, from[k] + (to[k] - from[k]) * e)
                if (u >= 1f) break
                delay(16)
            }
            s.tweening = false
        }
    }

    fun noteOn(strip: Int, note: Int, velocity: Int = 100) {
        val s = sessions[strip] ?: return
        s.held[note] = true; s.lastNote = note; s.noteEvents.intValue++
        host.noteOn(strip, note, velocity.coerceIn(1, 127))
    }

    fun noteOff(strip: Int, note: Int) {
        val s = sessions[strip] ?: return
        if (sustain[strip] == true) { pending.getOrPut(strip) { mutableSetOf() }.add(note); return }
        s.held.remove(note); host.noteOff(strip, note)
    }

    fun setSustain(strip: Int, down: Boolean) {
        sustain[strip] = down
        if (!down) pending.remove(strip)?.forEach { sessions[strip]?.held?.remove(it); host.noteOff(strip, it) }
    }

    fun pollLevel(strip: Int) { sessions[strip]?.level = host.level(strip).coerceIn(0f, 1f) }

    // ---- Snapshots ---------------------------------------------------------------------
    /**
     * The Æther instrument this strip is running, in the form a snapshot stores.
     *
     * The enum name is written, not its ordinal: the ordinal is an implementation detail of
     * [AeInstrument] and would silently mean a different instrument if the enum were reordered.
     */
    fun snapshotState(strip: Int): AetherTrackState? {
        val s = sessions[strip] ?: return null
        return AetherTrackState(s.instrument.name, s.preset, s.values.toList())
    }

    /**
     * Puts the strip back where a snapshot left it: loads the instrument if the strip no
     * longer carries one, then writes the preset and every knob. A snapshot with no Æther
     * block leaves the strip alone, which is what lets a snapshot taken before an instrument
     * was added coexist with one taken after.
     */
    fun restoreFromSnapshot(strip: Int, st: AetherTrackState?) {
        if (st == null) return
        val inst = AeInstrument.entries.firstOrNull { it.name == st.instrument } ?: return
        val s = sessions[strip]?.takeIf { it.instrument == inst } ?: (load(strip, inst) ?: return)
        s.preset = st.preset.coerceAtLeast(0)
        st.values.forEachIndexed { i, v -> if (i < s.values.size) setParam(strip, i, v) }
    }

    /** True when the strip currently runs an Æther instrument. */
    fun holds(strip: Int): Boolean = sessions.containsKey(strip)

    // ---- MIDI entry points: call these from the app's MIDI dispatcher. Channel n (0-based) = strip n.
    /** @return true if an Æther instrument consumed the event (so the app can skip FluidSynth for it). */
    fun onMidiNoteOn(channel: Int, note: Int, velocity: Int): Boolean {
        if (sessions[channel] == null) return false
        if (velocity == 0) noteOff(channel, note) else noteOn(channel, note, velocity)
        return true
    }
    fun onMidiNoteOff(channel: Int, note: Int): Boolean { if (sessions[channel] == null) return false; noteOff(channel, note); return true }
    fun onMidiCc(channel: Int, cc: Int, value: Int): Boolean {
        val s = sessions[channel] ?: return false
        if (cc == 64) { setSustain(channel, value >= 64); return true }
        if (cc == 1 && s.instrument == AeInstrument.ORGAN) { setParam(channel, 25, if (value > 63) 1f else 0.5f); return true }
        val map = when (s.instrument) { AeInstrument.ORGAN -> ccOrgan; AeInstrument.SYNTH -> ccSynth; AeInstrument.LEADS -> ccLeads; AeInstrument.SAX -> ccSax; AeInstrument.PIANO, AeInstrument.KEYBOARDS -> ccPiano }
        val idx = map[cc] ?: return false
        setParam(channel, idx, value / 127f); return true
    }
    fun onMidiAllNotesOff(channel: Int) { sessions[channel]?.let { it.held.clear(); host.allNotesOff(channel) } }

    /**
     * UI mirror of a note the engine already routed to this strip's source.
     *
     * The app plays every note through the native event queue, which hands it to the
     * Source when the strip carries one; [noteOn]/[noteOff] would push a *second* copy.
     * These only update [AeSession.held] and [AeSession.lastNote], which is what the
     * instrument window draws (key highlight, oscilloscope, note readout). They never
     * touch [host]: purely observational.
     */
    fun mirrorNoteOn(channel: Int, note: Int, velocity: Int) {
        val s = sessions[channel] ?: return
        s.held[note] = true
        s.lastNote = note
        s.noteEvents.intValue++
    }

    fun mirrorNoteOff(channel: Int, note: Int) {
        // A held pedal needs no special case here: while it is down the app does not emit
        // the NoteOff at all (the note waits in its own release list), so no mirror arrives
        // and the key stays lit. When the pedal lifts the engine emits it and we clear here.
        sessions[channel]?.held?.remove(note)
    }
}
