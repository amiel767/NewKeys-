package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.aetherkeys.fx.FxDesign
import com.aetherkeys.fx.FxKindD
import com.aetherkeys.fx.FxSlotState
import com.aetherkeys.fx.FxWindow
import com.aetherkeys.fx.FxWindowActions
import com.aetherkeys.fx.FxWindowState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capture la fenêtre FX DÉTACHÉE sur son canvas de maquette, 891 × 411.
 *
 * La capture se fait à l'échelle 1 : c'est la seule façon de comparer les cotes
 * avec la référence au pixel. On ne dépend d'aucun moteur — l'état est fabriqué
 * à la main, ce qui est précisément l'intérêt du contrat détaché.
 *
 * LA PAGE DE SPACE EST CELLE DE LA RÉFÉRENCE : le mot Æther/Space/le motif, la
 * figure circulaire du preset au centre, le gros potard DECAY, les anneaux de
 * gauche, les quatre panneaux de réglages et la liste de presets. Les vues
 * couvrent plusieurs presets pour vérifier que la figure ET la rampe changent
 * avec eux.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-440dpi", sdk = [36])
class FxDetachedCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val noop = object : FxWindowActions {
        override fun onClose() {}
        override fun closePage() {}
        override fun open(effectId: Int) {}
        override fun openCatalog(kind: FxKindD) {}
        override fun add(effectId: Int) {}
        override fun remove(slot: Int) {}
        override fun toggle(slot: Int) {}
        override fun setValue(index: Int, value: Float) {}
        override fun pickPreset(name: String) {}
        override fun beginSave() {}
    }

    private fun space(on: Boolean = true, preset: String = "Hall"): FxSlotState {
        val fx = FxDesign.SPACE
        return FxSlotState(
            info = fx, on = on, presetName = preset,
            values = fx.presets.getValue(preset), palette = fx.palettes[preset],
        )
    }

    private fun arp(on: Boolean = true) = FxSlotState(FxDesign.ARP, on, null)

    private fun capture(name: String, state: FxWindowState) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(891.dp, 411.dp).background(Color(0xFF0B0F17))) {
                FxWindow(state = state, actions = noop)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1400)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** Le rack : Space et l'Arpeggiator, deux emplacements libres. */
    @Test
    fun fx_rack() = capture(
        "fx_rack",
        FxWindowState(slots = listOf(space(), null, null, arp())),
    )

    /**
     * LE RACK À LA HAUTEUR BASSE : la fenêtre est réduite jusqu'à ce que le
     * corps égale la largeur d'une carte, donc les cartes sont CARRÉES. C'est le
     * geste demandé — tirer le bord bas de la fenêtre vers le haut.
     */
    @Test
    fun fx_rack_square() {
        val h = com.aetherkeys.fx.FxGeo.CARD_SQUARE_H
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(891.dp, h.dp).background(Color(0xFF0B0F17))) {
                FxWindow(
                    state = FxWindowState(slots = listOf(space(), space(preset = "Plate"), null, arp())),
                    actions = noop, heightUnits = h,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1400)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/fx_rack_square.png")
    }

    /** Le rack plein, Space contourné : on voit les potards grisés et le voile. */
    @Test
    fun fx_full() = capture(
        "fx_full",
        FxWindowState(slots = listOf(space(on = false, preset = "Plate"), space(), null, arp())),
    )

    /** Le catalogue : les deux effets, en deux colonnes. */
    @Test
    fun fx_catalog() = capture(
        "fx_catalog",
        FxWindowState(slots = listOf(space(), null, null, null), catalog = FxDesign.audio),
    )

    /** La page de Space : la figure du preset au centre, les réglages autour. */
    @Test
    fun fx_space() = capture(
        "fx_space",
        FxWindowState(
            slots = listOf(space(preset = "Hall"), null, null, arp()),
            openEffect = FxDesign.SPACE, presetName = "Hall", custom = true,
        ),
    )

    /** La page de Space, un preset à deux teintes : Shimmer, magenta + cyan. */
    @Test
    fun fx_space_shimmer() = capture(
        "fx_space_shimmer",
        FxWindowState(
            slots = listOf(space(preset = "Shimmer"), null, null, arp()),
            openEffect = FxDesign.SPACE, presetName = "Shimmer",
        ),
    )

    /**
     * La case déroulante OUVERTE : les huit presets se déplient sous le bouton.
     * C'est la seule capture qui a besoin d'un clic — le reste est piloté par
     * l'état —, donc elle ne passe pas par [capture].
     */
    @Test
    fun fx_space_dropdown() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(891.dp, 411.dp).background(Color(0xFF0B0F17))) {
                FxWindow(
                    state = FxWindowState(
                        slots = listOf(space(preset = "Hall"), null, null, arp()),
                        openEffect = FxDesign.SPACE, presetName = "Hall",
                    ),
                    actions = noop,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1400)
        // Le titre de la page contient aussi « Hall », et il n'est pas cliquable :
        // on cible le nœud qui PORTE l'action de clic, c'est-à-dire la pastille.
        composeTestRule.onNode(hasText("Hall") and hasClickAction()).performClick()
        composeTestRule.mainClock.advanceTimeBy(220)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/fx_space_dropdown.png")
    }

    /**
     * La même page, le réglage principal POUSSÉ BAS. C'est la capture qui prouve
     * que le magma suit la valeur : comparée à `fx_space`, le noyau doit être
     * visiblement plus petit et moins lumineux.
     */
    @Test
    fun fx_space_knob_low() {
        val fx = FxDesign.SPACE
        // Le réglage principal est DECAY (index 2) : c'est lui que le gros
        // potard affiche, donc c'est lui qu'on pousse bas.
        val vals = fx.presets.getValue("Hall").toMutableList().also { it[2] = .08f }
        capture(
            "fx_space_knob_low",
            FxWindowState(
                slots = listOf(
                    FxSlotState(fx, true, "Hall", vals, fx.palettes["Hall"]), null, null, arp(),
                ),
                openEffect = fx, presetName = "Hall",
            ),
        )
    }

    /** Une figure différente : Cathedral, la rosace. La figure suit le preset. */
    @Test
    fun fx_space_cathedral() = capture(
        "fx_space_cathedral",
        FxWindowState(
            slots = listOf(space(preset = "Cathedral"), null, null, arp()),
            openEffect = FxDesign.SPACE, presetName = "Cathedral",
        ),
    )

    /** La page de l'Arpeggiator : la barre de commandes et la grille de 16 pas. */
    @Test
    fun fx_arp() = capture(
        "fx_arp",
        FxWindowState(slots = listOf(space(), null, null, arp()), openEffect = FxDesign.ARP),
    )
}
