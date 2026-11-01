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
import kotlinx.coroutines.flow.collectLatest
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
 * CE QUI N'EST PAS BRANCHÉ, ET QUI EST ASSUMÉ. Le catalogue de design dessine
 * Space et l'Arpeggiator ; le moteur ne connaît que Space (id 0, sept réglages).
 * L'Arpeggiator n'a pas de contrepartie native : il s'installe dans l'emplacement
 * MIDI et vit purement à l'écran, sans son. Les trois derniers réglages de Space
 * (Low Cut, Hi Shelf, Mod) sont de la maquette : ils se règlent et se mémorisent
 * ici, mais ne partent pas au moteur, qui ne les a pas.
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

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xCC05070C)),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // On garde la taille de la maquette : le facteur ne dépasse jamais 1.
            val scale = rememberFitScale(maxWidth - 16.dp, maxHeight - 16.dp)
            FxWindow(state = host.state(), actions = host.actions(onClose), scale = scale)
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

    private var openId by mutableStateOf<Int?>(null)
    private var catalog by mutableStateOf<FxKindD?>(null)
    private var custom by mutableStateOf(false)

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
            }
            catalog = null
            touch()
        }

        override fun remove(slot: Int) {
            val id = slotIds.getOrNull(slot) ?: -1
            if (id < 0) return
            val engine = engineSlot(slot)
            if (engine >= 0) controller.setEffect(engine, -1)
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
            val engine = engineSlot(slot)
            if (engine >= 0) controller.setBypass(engine, !now)
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

        override fun pickPreset(name: String) {
            val id = openId ?: return
            val info = infoOf(id) ?: return
            val table = info.presets[name]
            if (table != null) {
                val v = vals(id)
                table.forEachIndexed { i, x -> if (i < v.size) v[i] = x }
                val slot = slotIds.indexOf(id)
                val engine = if (slot >= 0) engineSlot(slot) else -1
                if (engine >= 0) {
                    val index = info.presets.keys.indexOf(name)
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
