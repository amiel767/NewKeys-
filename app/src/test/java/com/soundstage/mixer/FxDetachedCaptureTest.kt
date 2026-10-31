package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.aetherkeys.fx.FxSlotState
import com.aetherkeys.fx.FxWindow
import com.aetherkeys.fx.FxWindowActions
import com.aetherkeys.fx.FxWindowState
import com.aetherkeys.fx.FxRegistry
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
        override fun openCatalog() {}
        override fun add(effectId: Int) {}
        override fun remove(slot: Int) {}
        override fun toggle(slot: Int) {}
        override fun setValue(index: Int, value: Float) {}
        override fun beginSave() {}
    }

    private fun slot(id: Int, on: Boolean = true, preset: String? = null): FxSlotState {
        val info = FxRegistry.info(id)
        return FxSlotState(info, on, preset, FxRegistry.accent(id))
    }

    private fun capture(name: String, state: FxWindowState) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(891.dp, 411.dp).background(Color(0xFF0B0F17))) {
                FxWindow(state = state, actions = noop)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/$name.png")
    }

    /** Le rack : quatre emplacements, dont deux occupés. */
    @Test
    fun fx_detached_rack() = capture(
        "fx_detached_rack",
        FxWindowState(
            slots = listOf(slot(0, preset = "Hall"), null, slot(4, preset = "Punch"), null),
            accentOf = { FxRegistry.accent(it) },
        ),
    )

    /** Le rack plein, un effet contourné : on voit l'état éteint. */
    @Test
    fun fx_detached_full() = capture(
        "fx_detached_full",
        FxWindowState(
            slots = listOf(
                slot(0, preset = "Hall"), slot(1, preset = "Ping Pong"),
                slot(2, on = false), slot(4, preset = "Punch"),
            ),
            accentOf = { FxRegistry.accent(it) },
        ),
    )

    /** Le catalogue ouvert. */
    @Test
    fun fx_detached_catalog() = capture(
        "fx_detached_catalog",
        FxWindowState(
            slots = listOf(slot(0, preset = "Hall"), null, null, null),
            catalog = FxRegistry.all.filter { it.kind == com.aetherkeys.fx.FxKind.EFFECT },
            accentOf = { FxRegistry.accent(it) },
        ),
    )

    /** La page d'un effet, avec ses réglages. */
    @Test
    fun fx_detached_effect() = capture(
        "fx_detached_effect",
        FxWindowState(
            slots = listOf(slot(0, preset = "Hall"), null, null, null),
            openEffect = FxRegistry.info(0),
            values = FxRegistry.info(0).params.map { it.default },
            custom = true,
            accentOf = { FxRegistry.accent(it) },
        ),
    )
}
