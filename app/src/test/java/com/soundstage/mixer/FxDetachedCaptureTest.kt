package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * LES SIX VUES SONT CELLES DE LA MAQUETTE : le rack, le rack avec un effet
 * contourné, le catalogue, la page de Space (avec l'orbe), un preset à deux
 * teintes, et la page de l'Arpeggiator (sa grille de pas).
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
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/$name.png")
    }

    /** Le rack : Space et l'Arpeggiator, deux emplacements libres. */
    @Test
    fun fx_rack() = capture(
        "fx_rack",
        FxWindowState(slots = listOf(space(), null, null, arp())),
    )

    /** Le rack plein, Space contourné : on voit l'orbe grisé et le voile. */
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

    /** La page de Space : l'orbe à gauche, presets et réglages à droite. */
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

    /** La page de l'Arpeggiator : la barre de commandes et la grille de 16 pas. */
    @Test
    fun fx_arp() = capture(
        "fx_arp",
        FxWindowState(slots = listOf(space(), null, null, arp()), openEffect = FxDesign.ARP),
    )
}
