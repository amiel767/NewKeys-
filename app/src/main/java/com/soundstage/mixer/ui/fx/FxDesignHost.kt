package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest
import com.aetherkeys.fx.ArpState
import com.aetherkeys.fx.FxDesign
import com.aetherkeys.fx.FxInfoD
import com.aetherkeys.fx.FxKindD
import com.aetherkeys.fx.FxSlotState
import com.aetherkeys.fx.FxWindow
import com.aetherkeys.fx.FxWindowActions
import com.aetherkeys.fx.FxWindowState
import com.aetherkeys.fx.rememberFitScale
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxRegistry

/** FX strip for a mixer track: tracks 1..8 map to strips 0..7, track 0 is master. */
fun stripForTrack(trackId: Int): Int =
    if (trackId == 0) FxRegistry.STRIP_MASTER else (trackId - 1).coerceIn(0, 7)

/**
 * L'ADAPTATEUR mixer -> contrat, celui que `FxWindowState.kt` annonçait.
 *
 * POURQUOI CE FICHIER EXISTE. Le commit qui a détaché la fenêtre a supprimé le
 * montage dans `MixerScreen` — `ActivePopup.EFFECTS` ne monte plus rien — mais
 * l'hôte n'a jamais été écrit. La fenêtre n'existait donc que dans le harnais de
 * capture : sur l'appareil, appuyer sur FX n'ouvrait rien. C'est ce trou que ce
 * fichier comble, et rien de plus.
 *
 * LA TRADUCTION, DANS UN SEUL SENS. Le mixer parle ids de moteur et tableaux de
 * 32 flottants ; la fenêtre parle `FxInfoD` et valeurs de maquette. Cet
 * adaptateur est le seul endroit qui connaît les deux vocabulaires — la fenêtre
 * ne change pas d'une ligne, exactement comme le contrat le promettait.
 *
 * CE QUI EST BRANCHÉ. Le catalogue de design dessine Space et l'Arpeggiator. Space
 * est un effet moteur (id 0, sept réglages) ; l'Arpeggiator n'est PAS un effet — il
 * ne fait pas de son, il transforme les notes tenues en flux de notes. Il s'installe
 * dans l'emplacement MIDI, arme le canal de la piste (`setArpChannel`) et pousse sa
 * grille de seize pas. Les trois derniers réglages de Space (Low Cut, Hi Shelf, Mod)
 * sont de la maquette : ils se règlent et se mémorisent ici, mais ne partent pas au
 * moteur, qui ne les a pas.
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
    val host = remember(strip) { FxDesignModel(controller) }

    // L'état natif est restauré une fois, puis adopté : le moteur est l'autorité
    // à ce moment-là, le ré-adopter ensuite ne ferait que le rejouer.
    LaunchedEffect(strip) {
        controller.restore()
        host.adoptFromEngine()
    }

    // Un drag de potard émet une valeur par frame. Écrire dans les préférences à
    // chaque frame bloquait le thread UI ; on n'écrit qu'une fois le doigt levé.
    LaunchedEffect(strip) {
        androidx.compose.runtime.snapshotFlow { host.revision }
            .collectLatest {
                kotlinx.coroutines.delay(500)
                controller.save()
            }
    }

    // La page de l'arp écrit son état à chaque geste (une case, un pas, le BPM).
    // Le moteur doit suivre : on pousse dès que l'arp est vivant. `collectLatest`
    // coalesce les rafales d'un drag, et le push est bon marché (des atomics).
    LaunchedEffect(strip) {
        androidx.compose.runtime.snapshotFlow { host.arpRevision() }
            .collectLatest {
                if (host.arpIsLive()) host.pushArp()
            }
    }

    // Fermer la fenêtre DÉSARME l'arp. Le rack n'est pas encore persisté : sans
    // ça, le canal resterait intercepté par un arp que plus aucune interface ne
    // montre, et le joueur ne pourrait plus le couper.
    androidx.compose.runtime.DisposableEffect(strip) {
        onDispose { controller.setArpArmed(false) }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xCC05070C)),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // La hauteur de la fenêtre est réglable : la poignée du bas la fait
            // glisser, et on la garde d'une ouverture à l'autre. Réduire la
            // fenêtre réduit le corps, donc les cartes, qui deviennent carrées à
            // la hauteur basse (FxGeo.CARD_SQUARE_H).
            val ctx = LocalContext.current
            val store = remember { FxWindowSizeStore(ctx) }
            var heightUnits by remember { mutableFloatStateOf(store.load()) }
            // On garde la taille de la maquette : le facteur ne dépasse jamais 1.
            val scale = rememberFitScale(maxWidth - 16.dp, maxHeight - 16.dp, heightUnits)
            FxWindow(
                state = host.state(), actions = host.actions(onClose), scale = scale,
                arpState = host.arp, heightUnits = heightUnits,
                onHeightChange = { heightUnits = it; store.save(it) },
            )
        }
    }
}

/**
 * L'état de l'hôte : quatre emplacements de maquette, et les valeurs qui vont
 * avec. Tout est en état Compose — une liste ordinaire ne ferait pas recomposer
 * le rack quand un effet s'ajoute.
 */
private class FxDesignModel(private val controller: FxController) {

    /**
     * Un id de design par emplacement, -1 quand il est libre.
     *
     * Le rack part VIDE, comme le design valide : les slots sont libres par
     * defaut, c'est l'utilisateur qui ajoute un effet par le catalogue. Ne pas
     * pre-remplir — une version anterieure amorcait Space et l'Arpeggiator et
     * affichait un rack que la maquette ne montre jamais a l'ouverture.
     */
    private val slotIds = mutableStateListOf(-1, -1, -1, -1)

    /** Les valeurs de maquette, par id de design (dix pour Space, sept au moteur). */
    private val values = mutableStateMapOf<Int, SnapshotStateList<Float>>()

    private val engaged = mutableStateMapOf<Int, Boolean>()
    private val presets = mutableStateMapOf<Int, String?>()
    private val user = mutableStateMapOf<Int, SnapshotStateList<String>>()

    /** Les valeurs enregistrées d'un preset utilisateur, par effet puis par nom. */
    private val userValues = mutableStateMapOf<Int, Map<String, List<Float>>>()

    private var openId by mutableStateOf<Int?>(null)
    private var catalog by mutableStateOf<FxKindD?>(null)
    private var custom by mutableStateOf(false)

    /**
     * L'état de l'arpégiateur, HÉBERGÉ ICI et passé à la fenêtre.
     *
     * Il vivait dans la fenêtre par `remember`, donc l'hôte ne pouvait pas le
     * lire : impossible de le pousser au moteur. Le contrat de la fenêtre est
     * resté intact — c'est l'hôte qui l'approvisionne.
     */
    val arp = ArpState()

    /**
     * Compteur d'écritures. La persistance l'observe au lieu d'être appelée à
     * chaque geste : un drag de potard émet une valeur par frame, et écrire dans
     * les préférences à cette cadence bloque le thread UI.
     */
    var revision by mutableStateOf(0)
        private set

    private fun touch() {
        revision++
    }

    private fun vals(id: Int): SnapshotStateList<Float> = values.getOrPut(id) {
        mutableStateListOf<Float>().apply {
            addAll(infoOf(id)?.params?.map { it.def } ?: emptyList())
        }
    }

    /** Les valeurs de maquette d'un effet, ou ses défauts. */
    private fun current(id: Int): List<Float> = vals(id).toList()

    /** L'emplacement moteur d'un emplacement de maquette, -1 si l'effet est purement visuel. */
    private fun engineSlot(designSlot: Int): Int {
        val id = slotIds.getOrNull(designSlot) ?: -1
        return if (id == FxDesign.SPACE.id) designSlot else -1
    }

    /** Adopte ce que le moteur porte déjà sur cette bande. */
    fun adoptFromEngine() {
        controller.effectIds.forEachIndexed { slot, id ->
            if (slot >= slotIds.size || id < 0) return@forEachIndexed
            val design = designForEngine(id) ?: return@forEachIndexed
            slotIds[slot] = design.id
            // `engaged` est indexe par id de design, pas par slot : c'est lui que
            // le rack lit pour allumer ou griser la carte.
            engaged[design.id] = !controller.bypassed.getOrElse(slot) { false }
            presets[design.id] = controller.presetNames.getOrNull(slot)
                ?: design.presets.keys.firstOrNull()
            val v = vals(design.id)
            controller.params.getOrNull(slot)?.forEachIndexed { i, x -> if (i < v.size) v[i] = x }
        }
    }

    fun state(): FxWindowState {
        val slots = List(FxDesign.SLOTS) { i ->
            val id = slotIds.getOrNull(i) ?: -1
            val info = infoOf(id) ?: return@List null
            val name = presets[id] ?: info.presets.keys.firstOrNull()
            FxSlotState(
                info = info,
                on = engaged[id] ?: true,
                presetName = name,
                values = current(id),
                palette = name?.let { info.palettes[it] },
            )
        }
        return FxWindowState(
            slots = slots,
            catalog = catalog?.let { FxDesign.list(it) },
            openEffect = openId?.let { infoOf(it) },
            custom = custom,
            presetName = openId?.let { presets[it] },
            userPresets = user.mapValues { it.value.toList() },
        )
    }

    fun actions(onClose: () -> Unit): FxWindowActions = object : FxWindowActions {
        override fun onClose() = onClose()

        override fun closePage() {
            openId = null
            catalog = null
        }

        override fun open(effectId: Int) {
            openId = effectId
            catalog = null
            custom = false
        }

        override fun openCatalog(kind: FxKindD) {
            catalog = kind
            openId = null
        }

        override fun add(effectId: Int) {
            val info = infoOf(effectId) ?: return
            val slot = freeSlotFor(info) ?: return
            slotIds[slot] = effectId
            engaged[effectId] = true
            presets[effectId] = info.presets.keys.firstOrNull()
            custom = false
            // Un effet du catalogue de design sans contrepartie moteur (l'ARP)
            // s'installe à l'écran seulement : il n'y a rien à faire sonner.
            if (effectId == FxDesign.SPACE.id) {
                if (controller.setEffect(slot, FxDesign.SPACE.id)) {
                    pushAll(effectId, slot)
                } else {
                    slotIds[slot] = -1
                }
            } else if (effectId == FxDesign.ARP.id) {
                // L'Arpeggiator n'est pas un effet : il ne fait pas de son, il
                // transforme les notes tenues en flux de notes. On arme le canal
                // de la piste et on pousse la grille ; le moteur fait le reste.
                armArp()
            }
            catalog = null
            touch()
        }

        override fun remove(slot: Int) {
            val id = slotIds.getOrNull(slot) ?: -1
            if (id < 0) return
            val engine = engineSlot(slot)
            if (engine >= 0) controller.setEffect(engine, -1)
            if (id == FxDesign.ARP.id) disarmArp()
            slotIds[slot] = -1
            engaged.remove(id)
            if (openId == id) openId = null
            touch()
        }

        override fun toggle(slot: Int) {
            val id = slotIds.getOrNull(slot) ?: -1
            if (id < 0) return
            val now = !(engaged[id] ?: true)
            engaged[id] = now
            if (id == FxDesign.ARP.id) {
                // Contourner l'arp, c'est le désarmer : les notes repartent
                // directement au synthé au lieu d'être avalées par le moteur.
                if (now) armArp() else controller.setArpArmed(false)
            } else {
                val engine = engineSlot(slot)
                if (engine >= 0) controller.setBypass(engine, !now)
            }
            touch()
        }

        override fun setValue(index: Int, value: Float) {
            val id = openId ?: return
            val v = vals(id)
            if (index !in v.indices) return
            v[index] = value.coerceIn(0f, 1f)
            custom = true
            val slot = slotIds.indexOf(id)
            val engine = if (slot >= 0) engineSlot(slot) else -1
            // Seuls les réglages que le moteur possède y sont poussés : les
            // suivants sont ceux que la maquette ajoute.
            if (engine >= 0 && index < engineParamCount()) {
                controller.setEffectParam(engine, index, v[index])
            }
            touch()
        }

        override fun setSlotValue(slot: Int, index: Int, value: Float) {
            val id = slotIds.getOrNull(slot) ?: return
            if (id < 0) return
            val v = vals(id)
            if (index !in v.indices) return
            v[index] = value.coerceIn(0f, 1f)
            custom = true
            // La vignette de la carte règle le MÊME tableau que la page : régler
            // depuis la carte puis ouvrir l'effet ne doit pas montrer deux états.
            val engine = engineSlot(slot)
            if (engine >= 0 && index < engineParamCount()) {
                controller.setEffectParam(engine, index, v[index])
            }
            touch()
        }

        override fun pickPreset(name: String) {
            val id = openId ?: return
            val info = infoOf(id) ?: return
            val table = info.presets[name] ?: userValues[id]?.get(name)
            if (table != null) {
                val v = vals(id)
                table.forEachIndexed { i, x -> if (i < v.size) v[i] = x }
                val slot = slotIds.indexOf(id)
                val engine = if (slot >= 0) engineSlot(slot) else -1
                val index = info.presets.keys.indexOf(name)
                if (engine >= 0) {
                    if (index >= 0 && index < enginePresetCount()) controller.applyPreset(engine, index)
                    else pushAll(id, slot)
                }
            }
            presets[id] = name
            custom = false
            touch()
        }

        // La feuille de nommage n'existe pas encore dans la fenêtre détachée :
        // le bouton Save est rendu et cliquable, mais n'ouvre rien. À brancher
        // quand le composant de saisie sera porté.
        override fun beginSave() {}

        override fun saveUserPreset(name: String) {
            val id = openId ?: return
            val clean = name.trim().ifEmpty { "User ${(user[id]?.size ?: 0) + 1}" }
            // Un preset utilisateur est une copie des réglages courants. Le nom
            // est unique : réenregistrer sous le même nom met à jour au lieu de
            // créer un doublon, sinon la liste se remplit de copies identiques.
            user.getOrPut(id) { mutableStateListOf() }.let { if (clean !in it) it.add(clean) }
            userValues[id] = (userValues[id] ?: emptyMap()) + (clean to vals(id).toList())
            presets[id] = clean
            custom = false
            touch()
        }

        override fun deleteUserPreset(name: String) {
            val id = openId ?: return
            user[id]?.remove(name)
            userValues[id] = (userValues[id] ?: emptyMap()) - name
            if (presets[id] == name) presets[id] = infoOf(id)?.presets?.keys?.firstOrNull()
            touch()
        }
    }

    /** Le premier emplacement libre du bon genre : 0..2 pour l'audio, 3 pour le MIDI. */
    private fun freeSlotFor(info: FxInfoD): Int? {
        val range = if (info.kind == FxKindD.MIDI) FxDesign.MAX_AUDIO until FxDesign.SLOTS
        else 0 until FxDesign.MAX_AUDIO
        return range.firstOrNull { slotIds[it] < 0 }
    }

    private fun pushAll(id: Int, slot: Int) {
        val engine = if (slot >= 0) engineSlot(slot) else -1
        if (engine < 0) return
        vals(id).forEachIndexed { i, x ->
            if (i < engineParamCount()) controller.setEffectParam(engine, i, x)
        }
    }

    // ---- arpégiateur -------------------------------------------------------
    // Le moteur ne connaît pas d'« effet ARP » : il connaît un canal intercepté.
    // Armer, c'est donc pousser toute la configuration puis choisir le canal.
    // Tout ce que la page peut régler est envoyé ici — sinon un réglage serait
    // un dessin, pas un contrôle.

    /** L'arp est-il dans le rack ET engagé ? */
    private fun arpLive(): Boolean {
        val slot = slotIds.indexOf(FxDesign.ARP.id)
        if (slot < 0) return false
        return engaged[FxDesign.ARP.id] ?: true
    }

    /** Pour l'observateur : la page n'a pas de compteur, on en dérive un. */
    fun arpIsLive(): Boolean = arpLive()

    /**
     * Une valeur qui change à chaque réglage de l'arp. `snapshotFlow` n'observe
     * que des lectures d'état Compose : on relit donc ici tout ce que la page
     * peut modifier, et on combine — c'est le changement qui compte, pas la
     * valeur.
     */
    fun arpRevision(): Int = arp.sub * 1_000_003 +
        arp.style * 100_003 +
        arp.steps * 10_007 +
        arp.bpm * 101 +
        arp.swing +
        arp.on.count { it } * 31 +
        arp.vel.sumOf { (it * 1000).toInt() }

    private fun armArp() {
        pushArp()
        controller.setArpArmed(true)
    }

    private fun disarmArp() {
        controller.setArpArmed(false)
        // Les notes déjà allumées par l'arp doivent être relâchées, sinon elles
        // sonnent sans fin : le moteur vide sa liste sur le canal d'émission.
        controller.arpAllNotesOff()
    }

    /**
     * Pousse les réglages de la page au moteur. Les indices suivent [ArpParam]
     * natif : RATE, MODE, OCTAVES, GATE, SWING, HOLD, VELOCITY, STEPS. ON n'est
     * pas poussé : c'est l'armement du canal qui l'allume (source unique).
     */
    fun pushArp() {
        // sub (0..3) = 1/4, 1/8, 1/16, 1/32 -> ArpRate::Quarter..ThirtySecond.
        controller.setArpParam(1, arp.sub / 3f)
        // style (0..3) = up, down, updown, random -> ArpMode::Up, Down, UpDown, Random.
        val mode = when (arp.style) {
            1 -> 1f
            2 -> 2f
            3 -> 4f  // Random (UpDown = 2, DownUp = 3, Random = 4)
            else -> 0f
        }
        controller.setArpParam(2, mode / 5f)
        controller.setArpParam(3, 0f)          // octaves = 1 (la page n'en a pas)
        controller.setArpParam(4, 0.5f)        // gate = ~52 %
        controller.setArpParam(5, arp.swing / 100f)
        controller.setArpParam(6, 0f)          // hold off
        controller.setArpParam(7, 0.85f)       // velocity de base
        // steps (1..16) -> 0..1 sur 16 colonnes.
        controller.setArpParam(8, (arp.steps - 1).coerceIn(0, 15) / 15f)
        controller.setArpTempo(arp.bpm.toDouble())
        for (i in 0 until 16) {
            val on = i < arp.steps && arp.on.getOrElse(i) { true }
            val vel = if (i < arp.steps) arp.vel.getOrElse(i) { 1f } else 0f
            controller.setArpStep(i, on, vel)
        }
    }

    /** Nombre de réglages que le moteur porte réellement pour l'effet de maquette. */
    private fun engineParamCount(): Int =
        com.soundstage.mixer.fx.FxRegistry.info(FxDesign.SPACE.id).params.size

    private fun enginePresetCount(): Int =
        com.soundstage.mixer.fx.FxRegistry.info(FxDesign.SPACE.id).presets.size

    private fun infoOf(id: Int): FxInfoD? = when (id) {
        FxDesign.SPACE.id -> FxDesign.SPACE
        FxDesign.ARP.id -> FxDesign.ARP
        else -> null
    }

    private fun designForEngine(engineId: Int): FxInfoD? =
        if (engineId == FxDesign.SPACE.id) FxDesign.SPACE else null
}


/**
 * OÙ VIT LA HAUTEUR CHOISIE. Une préférence locale : elle n'appartient pas au
 * projet, elle appartient à l'utilisateur et à son écran. On ne la range donc
 * pas dans l'état de l'app (qui part dans le JSON d'un setlist), mais dans les
 * préférences — la même place que les autres réglages d'affichage.
 */
private class FxWindowSizeStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("fx_window", android.content.Context.MODE_PRIVATE)
    fun load(): Float = prefs.getFloat(KEY_H, com.aetherkeys.fx.FxGeo.H)
    fun save(h: Float) = prefs.edit().putFloat(KEY_H, h).apply()
    private companion object { const val KEY_H = "height_units" }
}
