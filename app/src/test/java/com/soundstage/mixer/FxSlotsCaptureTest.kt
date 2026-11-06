package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import com.aetherkeys.fx.AeArpScreen
import com.aetherkeys.fx.ArpState
import com.aetherkeys.fx.FxChain
import com.aetherkeys.fx.FxKind
import com.aetherkeys.fx.FxSlot
import com.aetherkeys.fx.FxSlotsController
import com.aetherkeys.fx.FxSlotsWindow
import com.aetherkeys.fx.SlotRef
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capture la NOUVELLE fenêtre FX (FxSlotsWindow) : les emplacements, le
 * catalogue, et l'éditeur de l'arpeggiator. Aucun moteur n'est requis — la
 * coque reçoit un contrôleur nu, ce qui est justement l'intérêt du détachement.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-440dpi", sdk = [36])
class FxSlotsCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun shot(name: String, chain: FxChain, act: (FxSlotsController) -> Unit = {}) {
        lateinit var c: FxSlotsController
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            c = remember0(chain)
            val st by c.state.collectAsState()
            Box(Modifier.fillMaxSize().background(Color(0xFF05070C))) {
                FxSlotsWindow(
                    state = st,
                    controller = c,
                    trackName = "Track 1",
                    trackColor = Color(0xFF8FB2E8),
                    onClose = {},
                    editor = { kind, _ -> if (kind == FxKind.Arp) AeArpScreen(ArpState(), Modifier.fillMaxSize()) },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.runOnIdle { act(c) }
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/$name.png")
    }

    @Composable
    private fun remember0(chain: FxChain): FxSlotsController =
        androidx.compose.runtime.remember { FxSlotsController(chain) }

    @Test
    fun slotsVides() = shot("fxslots_empty", FxChain())

    @Test
    fun slotsAvecSpaceEtArp() = shot(
        "fxslots_filled",
        FxChain(midi = FxSlot(1, FxKind.Arp, true, "1/16 · up"), audio = listOf(FxSlot(2, FxKind.Reverb, true, "Hall"))),
    )

    @Test
    fun catalogue() = shot("fxslots_catalog", FxChain()) { it.openCatalog(SlotRef(false, 0)) }

    @Test
    fun editeurArp() = shot("fxslots_arp", FxChain(midi = FxSlot(1, FxKind.Arp, true, "1/16 · up"))) {
        it.openEditor(SlotRef(true))
    }
}
