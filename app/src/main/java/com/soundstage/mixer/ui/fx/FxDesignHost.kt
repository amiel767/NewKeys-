package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.collectLatest
import com.aetherkeys.fx.AeArpScreen
import com.aetherkeys.fx.AeSpaceScreen
import com.aetherkeys.fx.ArpState
import com.aetherkeys.fx.FxChain
import com.aetherkeys.fx.FxDesign
import com.aetherkeys.fx.FxKind
import com.aetherkeys.fx.FxSlot
import com.aetherkeys.fx.FxSlotsController
import com.aetherkeys.fx.FxSlotsWindow
import com.aetherkeys.fx.SlotRef
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxRegistry

/** FX strip for a mixer track: tracks 1..8 map to strips 0..7, track 0 is master. */
fun stripForTrack(trackId: Int): Int =
    if (trackId == 0) FxRegistry.STRIP_MASTER else (trackId - 1).coerceIn(0, 7)

/**
 * L'HÔTE DE LA FENÊTRE FX. Elle est détachée : `FxSlotsWindow` est sa propre
 * surface, et ce fichier est le seul endroit qui connaît à la fois le vocabulaire
 * du moteur (ids d'effet, tableaux de paramètres, armement du canal d'arp) et
 * celui de la fenêtre (un slot MIDI, trois slots audio, un catalogue).
 *
 * CE QUI SONNE. Deux effets existent : Æther Space (effet moteur id 0) et
 * l'Arpeggiator — qui n'est pas un effet mais un canal intercepté. Le catalogue
 * ne propose que ces deux-là.
 *
 * LES ÉDITEURS SONT AILLEURS. La coque donne tout le corps à `editor` : Space a
 * son écran, l'arp a le sien. La coque ne les connaît pas.
 */
@Composable
fun FxDesignHost(
    trackId: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strip = stripForTrack(trackId)
    val appContext = LocalContext.current.applicationContext
    val controller = remember(strip) { FxController(strip, appContext) }
    val model = remember(strip) { FxHostModel(controller, trackId) }

    // Le moteur est l'autorité à l'ouverture : on lit ce qu'il porte déjà, puis
    // on pose la chaîne SANS la lui rejouer.
    LaunchedEffect(strip) {
        controller.restore()
        model.adoptFromEngine()
    }

    // Un drag de potard émet une valeur par frame ; on n'écrit dans les
    // préférences qu'une fois le doigt levé.
    LaunchedEffect(strip) {
        androidx.compose.runtime.snapshotFlow { model.revision }
            .collectLatest {
                kotlinx.coroutines.delay(500)
                controller.save()
            }
    }

    // L'arp pousse son état au moteur dès qu'il est vivant.
    LaunchedEffect(strip) {
        androidx.compose.runtime.snapshotFlow { model.arpRevision() }
            .collectLatest { if (model.arpIsLive()) model.pushArp() }
    }

    // Fermer la fenêtre DÉSARME l'arp : sinon le canal resterait intercepté sans
    // qu'aucune interface ne le montre.
    DisposableEffect(strip) {
        onDispose { controller.setArpArmed(false) }
    }

    // Le contrôleur publie un StateFlow : le lire en composition ne recompose
    // PAS. Il faut le collecter en état Compose, sinon ni le catalogue ni
    // l'éditeur ne s'affichent.
    val fxState by model.fx.state.collectAsState()

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xCC05070C)),
        contentAlignment = Alignment.Center,
    ) {
        FxSlotsWindow(
            state = fxState,
            controller = model.fx,
            trackName = model.trackName,
            trackColor = Color(0xFF8FB2E8),
            onClose = onClose,
            editor = { kind, ref ->
                when (kind) {
                    FxKind.Arp -> AeArpScreen(model.arp, Modifier.fillMaxSize())
                    FxKind.Reverb -> model.SpaceEditor(ref)
                    else -> {}
                }
            },
        )
    }
}

/**
 * L'état de l'hôte : la chaîne (1 MIDI + 3 audio), les valeurs de maquette par
 * effet, et l'arpégiateur. Tout est en état Compose — une liste ordinaire ne
 * ferait pas recomposer la fenêtre quand un effet s'ajoute.
 */
private class FxHostModel(private val engine: FxController, trackId: Int) {

    /** L'état de l'arpégiateur, HÉBERGÉ ICI : l'hôte doit pouvoir le pousser. */
    val arp = ArpState()

    val trackName: String = if (trackId == 0) "Master" else "Track $trackId"

    var revision by mutableStateOf(0)
        private set

    /** Les valeurs de maquette de Space (quinze, dont sept au moteur). */
    private val spaceValues = mutableStateListOf<Float>().apply {
        addAll(FxDesign.SPACE.params.map { it.def })
    }

    private val presets = mutableStateMapOf<Int, String?>()
    private val user = mutableStateMapOf<Int, SnapshotStateList<String>>()
    private val userValues = mutableStateMapOf<Int, Map<String, List<Float>>>()
    private var custom by mutableStateOf(false)

    /** L'instantané pris à l'ouverture d'un éditeur : « Discard » y revient. */
    private var snapshot: List<Float>? = null

    /** Vrai pendant l'adoption : la chaîne posée ne doit pas être rejouée au moteur. */
    private var adopting = false

    private fun touch() { revision++ }

    val fx: FxSlotsController = FxSlotsController(
        onChain = { if (!adopting) applyChain(it) },
        onEditorOpen = { _, _ -> snapshot = spaceValues.toList() },
        onEditorConfirm = { _, _ -> snapshot = null },
        onEditorDiscard = { _, _ -> snapshot?.let { restore(it) }; snapshot = null },
        summaryOf = { kind -> if (kind == FxKind.Reverb) (presets[FxDesign.SPACE.id] ?: "Hall") else kind.desc },
    )

    private val spaceSlot: Int?
        get() = fx.state.value.chain.audio.indexOfFirst { it?.kind == FxKind.Reverb }.takeIf { it in 0..2 }

    // ---- adoption ----------------------------------------------------------

    /**
     * Lit ce que le moteur porte et pose la chaîne correspondante, sans la lui
     * rejouer : réinstaller l'effet écraserait les réglages qu'on vient de lire.
     */
    fun adoptFromEngine() {
        var midi: FxSlot? = null
        val audio = MutableList<FxSlot?>(3) { null }
        engine.effectIds.forEachIndexed { slot, id ->
            if (slot > 2 || id != FxDesign.SPACE.id) return@forEachIndexed
            val on = !engine.bypassed.getOrElse(slot) { false }
            audio[slot] = FxSlot(System.nanoTime(), FxKind.Reverb, on, summaryOf())
            engine.params.getOrNull(slot)?.forEachIndexed { i, x -> if (i < spaceValues.size) spaceValues[i] = x }
            engine.presetNames.getOrNull(slot)?.let { presets[FxDesign.SPACE.id] = it }
                ?: run { presets[FxDesign.SPACE.id] = FxDesign.SPACE.presets.keys.firstOrNull() }
        }
        adopting = true
        fx.adopt(FxChain(midi = midi, audio = audio))
        adopting = false
        touch()
    }

    private fun summaryOf(): String = presets[FxDesign.SPACE.id] ?: "Hall"

    // ---- chaîne -> moteur --------------------------------------------------

    /**
     * La chaîne a changé : on la répercute au moteur. Le slot MIDI arme ou
     * désarme le canal ; les slots audio installent ou retirent l'effet Space.
     */
    private fun applyChain(chain: FxChain) {
        // MIDI : armer, c'est pousser toute la config puis choisir le canal.
        val wantArp = chain.midi?.kind == FxKind.Arp && chain.midi.on
        if (wantArp) { pushArp(); engine.setArpArmed(true) }
        else { engine.setArpArmed(false); engine.arpAllNotesOff() }

        // AUDIO
        chain.audio.forEachIndexed { slot, s ->
            if (slot > 2) return@forEachIndexed
            if (s != null && s.kind == FxKind.Reverb) {
                if (engine.effectIds.getOrNull(slot) != FxDesign.SPACE.id) {
                    if (engine.setEffect(slot, FxDesign.SPACE.id)) pushSpace(slot) else return@forEachIndexed
                }
                engine.setBypass(slot, !s.on)
            } else if (engine.effectIds.getOrNull(slot) == FxDesign.SPACE.id) {
                engine.setEffect(slot, -1)
            }
        }
        touch()
    }

    private fun pushSpace(slot: Int) {
        val count = FxRegistry.info(FxDesign.SPACE.id).params.size
        spaceValues.forEachIndexed { i, x -> if (i < count) engine.setEffectParam(slot, i, x) }
    }

    private fun restore(v: List<Float>) {
        v.forEachIndexed { i, x -> if (i < spaceValues.size) spaceValues[i] = x }
        spaceSlot?.let { pushSpace(it) }
        touch()
    }

    // ---- arpégiateur -------------------------------------------------------

    fun arpIsLive(): Boolean = fx.state.value.chain.midi?.let { it.kind == FxKind.Arp && it.on } == true

    fun arpRevision(): Int = arp.sub * 1_000_003 + arp.style * 100_003 + arp.steps * 10_007 +
        arp.bpm * 101 + arp.swing + arp.on.count { it } * 31 + arp.vel.sumOf { (it * 1000).toInt() }

    fun pushArp() {
        // Indices [ArpParam] natif : RATE, MODE, OCTAVES, GATE, SWING, HOLD, VELOCITY, STEPS.
        engine.setArpParam(1, arp.sub / 3f)
        val mode = when (arp.style) { 1 -> 1f; 2 -> 2f; 3 -> 4f; else -> 0f }
        engine.setArpParam(2, mode / 5f)
        engine.setArpParam(3, 0f)
        engine.setArpParam(4, 0.5f)
        engine.setArpParam(5, arp.swing / 100f)
        engine.setArpParam(6, 0f)
        engine.setArpParam(7, 0.85f)
        engine.setArpParam(8, (arp.steps - 1).coerceIn(0, 15) / 15f)
        engine.setArpTempo(arp.bpm.toDouble())
        for (i in 0 until 16) {
            val on = i < arp.steps && arp.on.getOrElse(i) { true }
            val vel = if (i < arp.steps) arp.vel.getOrElse(i) { 1f } else 0f
            engine.setArpStep(i, on, vel)
        }
    }

    // ---- éditeur Space -----------------------------------------------------

    @Composable
    fun SpaceEditor(ref: SlotRef) {
        val info = FxDesign.SPACE
        AeSpaceScreen(
            fx = info,
            values = spaceValues.toList(),
            presetName = presets[info.id],
            userNames = user[info.id]?.toList() ?: emptyList(),
            custom = custom,
            onValue = { i, v -> setValue(ref, i, v) },
            onPreset = { pickPreset(it) },
            onSave = { savePreset(it) },
            onDeleteUser = { deletePreset(it) },
            onBack = { fx.back() },
            onClose = { fx.back() },
        )
    }

    private fun setValue(ref: SlotRef, index: Int, value: Float) {
        if (index !in spaceValues.indices) return
        if (snapshot == null) snapshot = spaceValues.toList()
        spaceValues[index] = value.coerceIn(0f, 1f)
        custom = true
        val slot = ref.index
        if (!ref.midi && index < FxRegistry.info(FxDesign.SPACE.id).params.size &&
            engine.effectIds.getOrNull(slot) == FxDesign.SPACE.id
        ) {
            engine.setEffectParam(slot, index, spaceValues[index])
        }
        touch()
    }

    private fun pickPreset(name: String) {
        val info = FxDesign.SPACE
        val table = info.presets[name] ?: userValues[info.id]?.get(name)
        if (table != null) {
            table.forEachIndexed { i, x -> if (i < spaceValues.size) spaceValues[i] = x }
            val index = info.presets.keys.indexOf(name)
            val slot = spaceSlot
            if (slot != null) {
                if (index >= 0 && index < FxRegistry.info(info.id).presets.size) engine.applyPreset(slot, index)
                else pushSpace(slot)
            }
        }
        presets[info.id] = name
        custom = false
        snapshot = null
        touch()
    }

    private fun savePreset(name: String) {
        val id = FxDesign.SPACE.id
        val clean = name.trim().ifEmpty { "User ${(user[id]?.size ?: 0) + 1}" }
        user.getOrPut(id) { mutableStateListOf() }.let { if (clean !in it) it.add(clean) }
        userValues[id] = (userValues[id] ?: emptyMap()) + (clean to spaceValues.toList())
        presets[id] = clean
        custom = false
        touch()
    }

    private fun deletePreset(name: String) {
        val id = FxDesign.SPACE.id
        user[id]?.remove(name)
        userValues[id] = (userValues[id] ?: emptyMap()) - name
        if (presets[id] == name) presets[id] = FxDesign.SPACE.presets.keys.firstOrNull()
        touch()
    }
}
