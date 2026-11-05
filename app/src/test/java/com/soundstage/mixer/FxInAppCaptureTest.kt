package com.soundstage.mixer

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.aetherkeys.fx.FxGeo
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.ui.MixerScreen
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LA FENETRE FX VUE PAR L'APP, pas par le harnais.
 *
 * `FxDetachedCaptureTest` monte `FxWindow` directement et `FxHostCaptureTest`
 * monte l'hote : les deux court-circuitent le mixer. Ce test prend le chemin
 * reel — `MixerScreen` complet, puis la popup EFFECTS ouverte par le ViewModel,
 * exactement comme le fait le bouton FX d'une piste. C'est le seul montage qui
 * prouve que le chemin « appuyer sur FX » aboutit a une fenetre visible.
 *
 * Le clic sur le bouton FX est remplace par `openEffectsForTrack`, l'appel que ce
 * bouton fait : cliquer demanderait de localiser le bouton dans un arbre anime,
 * et ne testerait rien de plus — c'est le meme appel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class FxInAppCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun fx_window_is_visible_from_the_real_mixer() {
        // Le mixer porte des animations infinies : une horloge manuelle garde
        // l'arbre immobile, sinon captureRoboImage leve AppNotIdleException.
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)
        viewModel.navigateToPage(AppScreenPage.MIXER)

        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    MixerScreen(viewModel = viewModel, skipSplash = true)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200) // la restauration d'etat est asynchrone
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.runOnIdle {
            viewModel.navigateToPage(AppScreenPage.MIXER)
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)

        // Le geste du bouton FX d'une piste : c'est cet appel qui ouvrait le vide.
        composeTestRule.runOnIdle { viewModel.openEffectsForTrack(1) }
        composeTestRule.mainClock.advanceTimeBy(1_500)

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/fx_in_app.png")
        val f = java.io.File("src/test/screenshots/fx_in_app.png")
        assertTrue("capture absente", f.exists())
        assertTrue("capture suspicieusement petite : popup vide ?", f.length() > 20_000)

        // Un tap reel sur le premier emplacement vide doit ouvrir le catalogue.
        // C'est ce qui distingue « la fenetre est dessinee » de « la fenetre
        // repond » : la capture precedente ne prouverait pas la seconde.
        val s = 2.75f
        // La fenetre est rendue a 70 % : sans ce facteur le tap visait a cote de
        // la case, et le test ne prouvait plus qu'un tap reel ouvre le catalogue.
        val scale = minOf((851f - 16f) / 891f, (393f - 16f) / 411f, 1f) * FxGeo.REDUCTION
        val left = (851f - 891f * scale) / 2f
        val top = (393f - 411f * scale) / 2f
        val slotX = left + (12f + 104f) * scale
        val slotY = top + (46f + 170f) * scale
        composeTestRule.onRoot().performTouchInput {
            click(Offset(slotX * s, slotY * s))
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/fx_in_app_catalog.png")
    }
}
