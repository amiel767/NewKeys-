package com.soundstage.mixer.instruments

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.soundstage.mixer.R
import com.soundstage.mixer.audio.NativeAudioBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Assigns the bundled fonts to the instrument UI once, at startup. */
fun initAetherInstruments(context: Context) {
    AeFonts.plate = FontFamily(Font(R.font.cinzel))
    AeFonts.mono = FontFamily(Font(R.font.share_tech_mono))
    AeFonts.ui = FontFamily(Font(R.font.plus_jakarta_sans))
    AeFonts.display = FontFamily(Font(R.font.sora))
    AeBanks.app = context.applicationContext
    AeUserStore.app = context.applicationContext
}

/**
 * Reads the sampled banks shipped in `assets/saxpack/`. They are loaded on demand and kept
 * decoded as a ByteArray, because the native side copies what it needs: a 2 MB array held
 * once is cheaper than re-reading the asset on every instrument load.
 */
object AeBanks {
    /** Set by [initAetherInstruments]; null in unit tests, where the load simply fails. */
    @Volatile var app: Context? = null
    private val cache = HashMap<String, ByteArray>()
    /** Bank file per instrument type (0 soprano, 1 alto, 2 tenor, 3 baritone), empty = none. */
    private val saxFiles = arrayOf("saxpack_sop.bin", "saxpack_alt.bin", "saxpack_ten.bin", "saxpack_bar.bin")
    /**
     * L'alto peut jouer deux banques : MTG Solo Sax (CC-BY, la banque par defaut) et
     * Bulldog Sax 2 (licence + accord du proprietaire, voir ATTRIBUTION.md), une seule
     * anche au profil alto.  Bulldog est plus proche d'un vrai saxo (attaque plus nette,
     * tenue plus riche) ; on en fait la source de l'alto et on garde MTG comme repli si
     * l'asset manque.
     */
    private val bulldogAltoFile = "saxpack/saxpack_bulldog.bin"
    /** Piano acoustique (queue) : Royal Grand 3D, 11,6 Mo, 160 regions, 48 racines, 8 couches. */
    private val pianoFile = "pianopack/piano.bin"
    /**
     * Piano acoustique (droit) : Grand Upright, 14,8 Mo, 201 regions, 30 racines,
     * 7 couches.  Un droit n'est pas un queue filtre : la table d'harmonie courte,
     * les cordes croisees et la proximite du meuble donnent un autre timbre.  C'est
     * donc une banque, comme l'electrique.
     */
    private val uprightFile = "pianopack/upright.bin"
    /**
     * Piano electrique : banque distincte, pas un reglage du precedent.  Le moteur ne
     * peut pas transformer un Steinway en tine : l'inharmonicite d'une corde et celle
     * d'une barre frappee n'ont pas la meme loi.  Banque GregSullivan Wurlitzer EP200
     * (CC-BY 3.0), pack 1,58 Mo / 48 regions, construite par tools/pianopack/build_electric.py.
     */
    private val electricFile = "pianopack/electric.bin"
    /** Index du modele « Electric » dans AePianoModels.all : il change de banque. */
    private const val ELECTRIC_MODEL = 8

    /**
     * Banques electriques : une par modele, pas un reglage du piano acoustique.
     *
     * Mesure sur les 9 modeles : 7 sur 9 rendaient un son bit-identique.  Un filtre
     * ne transforme pas un Steinway en tine — l'inharmonicite d'une corde (B ~ 1e-4)
     * et celle d'une barre frappee (B ~ 1e-2) n'ont pas la meme loi.  Ces quatre
     * banques viennent du SF2 « E.Piano » (80s Layer / BalladKey / DX Legend /
     * E.Piano), chacune construite par `tools/pianopack/build_sf2.py --preset`.
     *
     * Cle = index du modele, valeur = (fichier, nom affiche).  L'ordre suit
     * AePianoModels.all : 8 = Tine Keys, puis les nouveaux.
     */
    private val electricModels = mapOf(
        8 to "pianopack/ep_tine.bin",
        9 to "pianopack/ep_ballad.bin",
        10 to "pianopack/ep_dx.bin",
        11 to "pianopack/ep_piano.bin",
    )

    /**
     * Modeles qui jouent une AUTRE banque acoustique (le droit), pas un reglage du
     * queue.  Cle = index du modele dans AePianoModels.all.
     */
    private val uprightModels = setOf(5, 6)

    /** Nombre de modeles de piano, pour l'assertion de coherence cote tests. */
    const val PIANO_MODEL_COUNT = 12

    /** Le modele porte-t-il une de nos banques electriques ? */
    fun isElectricModel(model: Int): Boolean = electricModels.containsKey(model)

    /** Le modele porte-t-il la banque du piano droit ? */
    fun isUprightModel(model: Int): Boolean = model in uprightModels

    /**
     * Identite de la banque d'un modele de piano, pour le manager.
     *
     * Doit etre DISTINCTE par banque : le manager compare cette valeur avant de
     * recharger.  Deux modeles qui partagent la meme valeur ne declenchent aucun
     * rechargement, donc passer du queue (Studio) au droit (Cottage) garderait le
     * mauvais echantillon.  0 = queue, 1 = droit, 8..11 = une valeur par electrique.
     */
    fun pianoBankId(model: Int): Int = when {
        electricModels.containsKey(model) -> model
        model in uprightModels -> UPRIGHT_BANK_ID
        else -> GRAND_BANK_ID
    }
    private const val GRAND_BANK_ID = 0
    private const val UPRIGHT_BANK_ID = 1

    /**
     * The bank belonging to an instrument, or null when it has none (organ, synth, leads).
     *
     * `model` selectionne la banque du piano : queue par defaut, droit pour Cottage et
     * Parlor, electrique pour les quatre derniers.  Les autres modeles sont des reglages
     * sur la banque du queue.
     */
    fun fileFor(instrument: AeInstrument, saxType: Int, model: Int = -1): String? = when (instrument) {
        // L'alto (type 1) joue Bulldog ; soprano/tenor/baryton gardent MTG.
        AeInstrument.SAX -> if (saxType == 1) bulldogAltoFile else saxFiles[saxType.coerceIn(0, 3)]
        AeInstrument.PIANO -> electricModels[model]
            ?: if (model == ELECTRIC_MODEL) electricFile
            else if (model in uprightModels) uprightFile
            else pianoFile
        else -> null
    }

    /**
     * Libere une banque du cache Kotlin.  La banque est DEJA copiee cote natif au
     * chargement, donc le tableau Kotlin ne sert plus : le garder faisait tenir
     * piano.bin (12 Mo) et les 4 packs sax (7 Mo) en memoire pour toute la session,
     * en plus de la copie native.  Appele quand un instrument est decharge.
     */
    fun release(name: String) { cache.remove(name) }

    /** Purge tout le cache (changement de setlist, memoire basse). */
    fun releaseAll() { cache.clear() }

    fun read(name: String): ByteArray? {
        cache[name]?.let { return it }
        val ctx = app ?: return null
        return try {
            // Les banques sax vivent dans saxpack/, le piano dans pianopack/ : le chemin
            // vient de fileFor(), pas d'un prefixe code en dur.
            val path = if (name.contains("/")) name else "saxpack/$name"
            ctx.assets.open(path).use { it.readBytes() }.also { cache[name] = it }
        } catch (e: Throwable) {
            Log.e("AeBanks", "Cannot read asset bank $name: ${e.message}")
            // Repli : si la banque Bulldog n'est pas dans l'APK, l'alto garde MTG.
            fallback[name]?.let { return read(it) }
            null
        }
    }
    private val fallback = mapOf(bulldogAltoFile to "saxpack_alt.bin")
}

/**
 * Bridges [AeHost] onto the native FxMixer facade. An Æther instrument occupies a mixer
 * strip exactly like a soundfont: the same `setInstrument` / `setInstrumentParam` entry
 * points the FX window uses, so the engine needs no new native call.
 *
 * The strip index is the mixer channel (0..7 tracks, 8 drum, 9 tonic pad). Notes are NOT
 * pushed from here in normal use: the app already routes every note through the native
 * event queue (`AudioEngine.playMidiNote` -> `noteOn`), and `routeEventToSource` hands it
 * to the Source when the strip carries one. These note methods exist for the instrument
 * window's own on-screen keyboard.
 */
object AeInstrumentHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int): Boolean =
        NativeAudioBridge.safeSetTrackInstrument(strip, fxId)

    override fun unloadInstrument(strip: Int) {
        NativeAudioBridge.safeSetTrackInstrument(strip, -1)
    }

    override fun setParam(strip: Int, index: Int, value: Float) {
        NativeAudioBridge.safeSetTrackInstrumentParam(strip, index, value)
    }

    override fun noteOn(strip: Int, note: Int, velocity: Int) {
        NativeAudioBridge.safeNoteOn(strip, note, velocity)
    }

    override fun noteOff(strip: Int, note: Int) {
        NativeAudioBridge.safeNoteOff(strip, note)
    }

    override fun allNotesOff(strip: Int) {
        NativeAudioBridge.safeAllNotesOff(strip)
    }

    override fun level(strip: Int): Float = NativeAudioBridge.safeFxStripPeak(strip)

    override fun loadBank(strip: Int, instrument: AeInstrument, type: Int, model: Int): Boolean {
        // Sax and piano ship a bank. `type` is the sax's first parameter, the same value the
        // engine reads to pick a formant set, so the two stay in step; the piano has a single
        // bank and ignores it.
        if (instrument != AeInstrument.SAX && instrument != AeInstrument.PIANO) return false
        val name = AeBanks.fileFor(instrument, type, model) ?: return false
        val bytes = AeBanks.read(name) ?: return false
        val ok = NativeAudioBridge.safeLoadInstrumentBank(strip, bytes)
        // The mix is parameter 22 now (the Blend knob), pushed by the manager with the rest of
        // the preset. Forcing it here as well would overwrite that value on every load, in
        // whichever order the two happened to run.
        Log.d("AeInstrumentHost", "loadBank(strip=$strip, $name, ${bytes.size} o) -> $ok")
        return ok
    }

    override fun bankMix(strip: Int): Float = NativeAudioBridge.safeInstrumentSampleMix(strip)

    override fun releaseBank(instrument: AeInstrument, type: Int, model: Int) {
        AeBanks.fileFor(instrument, type, model)?.let { AeBanks.release(it) }
    }
}

/**
 * Application-scoped instrument manager. Created once so a loaded instrument survives the
 * soundfont window being closed — the zip requires that (closing the window must not unload).
 */
object AeInstruments {
    /** Supervisor so one failed preset tween never cancels the others. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val manager: AeManager by lazy { AeManager(AeInstrumentHost, scope) }

    /**
     * Set by the ViewModel. Called before an instrument loads on a strip so the app can
     * release whatever else occupies it — an Æther instrument replaces the soundfont,
     * exactly like loading a soundfont replaces the Æther instrument.
     */
    var onBeforeLoad: ((Int) -> Unit)? = null

    /**
     * Called after an instrument has been installed on a strip, so the app can push the
     * track's own fader/pan onto the strip: a source renders outside FluidSynth, so from
     * that moment the strip is the only place its volume lives.
     */
    var onLoaded: ((Int) -> Unit)? = null

    fun session(strip: Int): AeSession? = manager.sessions[strip]

    fun isLoaded(strip: Int): Boolean = manager.sessions.containsKey(strip)

    /** Display name of the instrument on a strip, or null when the strip has no Æther instrument. */
    fun nameOn(strip: Int): String? = manager.sessions[strip]?.instrument?.title

    /** Preset name the strip plays, so the mixer's name plate can show it. */
    fun presetNameOn(strip: Int): String? = manager.presetNameOn(strip)

    /** Loads an instrument on a strip and returns true when the engine accepted it. */
    fun load(strip: Int, instrument: AeInstrument): Boolean {
        if (manager.sessions[strip]?.instrument == instrument) return true
        onBeforeLoad?.invoke(strip)
        val ok = manager.load(strip, instrument) != null
        if (ok) onLoaded?.invoke(strip)
        return ok
    }

    fun unload(strip: Int) = manager.unload(strip)
}
