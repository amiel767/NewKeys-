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
import com.aetherkeys.fx.AeSpaceScreen
import com.aetherkeys.fx.FxDesign
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

    /**
     * Le répertoire de travail du test dépend de la façon dont Gradle est lancé :
     * un chemin relatif « app/src/... » atterrissait dans « app/app/src/... ».
     * On remonte donc jusqu'à la racine du projet (celle qui porte settings.gradle).
     */
    private fun shotsDir(): java.io.File {
        var d = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d.parentFile != null && !java.io.File(d, "settings.gradle.kts").exists() &&
            !java.io.File(d, "settings.gradle").exists()
        ) d = d.parentFile
        return java.io.File(d, "app/src/test/screenshots").also { it.mkdirs() }
    }

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
                    editor = { kind, _ ->
                        when (kind) {
                            FxKind.Arp -> AeArpScreen(ArpState(), Modifier.fillMaxSize())
                            FxKind.Reverb -> AeSpaceScreen(
                                fx = FxDesign.SPACE,
                                values = FxDesign.SPACE.params.map { it.def },
                                presetName = "Hall",
                                userNames = emptyList(),
                                custom = false,
                                onValue = { _, _ -> },
                                onPreset = {},
                                onSave = {},
                                onDeleteUser = {},
                                onBack = {},
                                onClose = {},
                            )
                            else -> {}
                        }
                    },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.runOnIdle { act(c) }
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.onRoot().captureRoboImage(java.io.File(shotsDir(), "$name.png").absolutePath)
    }

    @Composable
    private fun remember0(chain: FxChain): FxSlotsController =
        androidx.compose.runtime.remember { FxSlotsController(chain) }

    @Test
    fun slotsVides() = shot("fxslots_empty", FxChain())

    @Test
    fun slotsAvecSpaceEtArp() = shot(
        "fxslots_filled",
        FxChain(
            midi = FxSlot(1, FxKind.Arp, true, "1/16 · up"),
            // TROIS emplacements audio, comme l'app : une seule carte laissait la
            // coque dessiner une carte pleine largeur, ce qui ne montre pas la
            // taille réelle d'une case (et fait croire à un rack démesuré).
            audio = listOf(FxSlot(2, FxKind.Reverb, true, "Hall"), null, null),
        ),
    )

    @Test
    fun catalogue() = shot("fxslots_catalog", FxChain()) { it.openCatalog(SlotRef(false, 0)) }

    @Test
    fun editeurArp() = shot("fxslots_arp", FxChain(midi = FxSlot(1, FxKind.Arp, true, "1/16 · up"))) {
        it.openEditor(SlotRef(true))
    }

    /**
     * La fenêtre DOIT se remettre à la taille de l'éditeur quand on entre dans
     * Space (860x392) et revenir à la taille des cases en sortant. Ce cas
     * capture l'état agrandi : la coque est la même, seul le cadre change.
     */
    @Test
    fun editeurSpace() = shot(
        "fxslots_space",
        FxChain(audio = listOf(FxSlot(2, FxKind.Reverb, true, "Hall"))),
    ) { it.openEditor(SlotRef(false, 0)) }
}
