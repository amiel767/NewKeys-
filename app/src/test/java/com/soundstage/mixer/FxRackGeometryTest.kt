package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.aetherkeys.fx.FxDesign
import com.aetherkeys.fx.FxGeo
import com.aetherkeys.fx.FxKindD
import com.aetherkeys.fx.FxSlotState
import com.aetherkeys.fx.FxWindow
import com.aetherkeys.fx.FxWindowActions
import com.aetherkeys.fx.FxWindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LA FENÊTRE SE RÉDUIT, ET À LA HAUTEUR BASSE LA CARTE EST UN CARRÉ.
 *
 * La carte remplit toute la hauteur du corps. Sa largeur est fixe — un quart de
 * rangée —, donc c'est la HAUTEUR qui décide de sa forme : tant que la fenêtre
 * est haute, la carte est un rectangle vertical ; quand on la réduit jusqu'à ce
 * que le corps égale la largeur, elle devient carrée.
 *
 * On ne peut pas le mesurer au pixel : la carte est peinte par un SVG sans
 * bordure franche. On interroge donc la mise en page par la sémantique — la
 * seule mesure qui ne dépend ni du thème ni de l'antialiasing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-440dpi", sdk = [36])
class FxRackGeometryTest {

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

    private fun slot(preset: String = "Hall") = FxDesign.SPACE.let { fx ->
        FxSlotState(
            info = fx, on = true, presetName = preset,
            values = fx.presets.getValue(preset), palette = fx.palettes[preset],
        )
    }

    /** Rend le rack à la hauteur demandée et rend le côté mesuré, en dp. */
    private fun cardSide(heightUnits: Float): Float {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            var h by remember { mutableFloatStateOf(heightUnits) }
            Box(Modifier.size(891.dp, heightUnits.dp).background(Color(0xFF0B0F17))) {
                FxWindow(
                    state = FxWindowState(slots = listOf(slot(), slot("Plate"), null, null)),
                    actions = noop,
                    heightUnits = h,
                    onHeightChange = { h = it },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(600)
        val d = composeTestRule.density.density
        val cards = composeTestRule.onAllNodesWithTag("fxcard").fetchSemanticsNodes()
        assertEquals("le rack doit porter deux cartes", 2, cards.size)
        val w0 = cards[0].size.width / d
        assertEquals("les deux cartes doivent avoir la même largeur", w0, cards[1].size.width / d, .5f)
        return cards[0].size.height / d
    }

    @Test
    fun aLaHauteurDeLaMaquetteLaCarteEstUnRectangleVertical() {
        val side = cardSide(FxGeo.H)
        assertTrue(
            "à la hauteur de la maquette la carte doit être plus haute que large ($side)",
            side > FxGeo.CARD_W + 40f,
        )
        assertTrue(
            "et plus haute qu'à la hauteur basse (${FxGeo.CARD_W})",
            side > FxGeo.CARD_W,
        )
    }

    @Test
    fun aLaHauteurBasseLaCarteEstCarree() {
        val h = cardSide(FxGeo.CARD_SQUARE_H)
        assertEquals(
            "le corps vaut la largeur de la carte, donc la hauteur doit l'égaler",
            FxGeo.CARD_W, h, 2f,
        )
    }

}
