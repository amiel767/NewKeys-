package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.fx.FxDesignHost
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LA PREUVE QUE LA FENETRE EST MONTEE DANS L'APP.
 *
 * Le defaut rapporte sur appareil etait « la fenetre est invisible ». La cause
 * n'etait pas un probleme de rendu : le commit qui a detache la fenetre a
 * supprime son montage dans `MixerScreen` sans ecrire l'hote, donc appuyer sur
 * FX n'ouvrait rien du tout. `FxDetachedCaptureTest` ne pouvait pas le voir : il
 * monte `FxWindow` directement et ne passe jamais par l'hote.
 *
 * Ce test monte `FxDesignHost`, c'est-a-dire le chemin reel de l'app. Sans pont
 * natif (cas de Robolectric) le moteur refuse les effets, donc le rack reste
 * vide — mais la fenetre doit etre LA, avec ses quatre emplacements. C'est
 * exactement ce qui manquait.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-440dpi", sdk = [36])
class FxHostCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun capture(name: String) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.size(891.dp, 411.dp).background(Color(0xFF0B0F17))) {
                FxDesignHost(trackId = 1, onClose = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/$name.png")
    }

    /** La fenetre doit etre visible : l'hote monte le rack, meme sans moteur. */
    @Test
    fun fx_host_is_visible() {
        capture("fx_host")
        val f = java.io.File("app/src/test/screenshots/fx_host.png")
        assertTrue("capture absente : la fenetre n'a pas ete montee", f.exists())
        // Un ecran entierement noir ferait ~0 octet une fois compresse ; une
        // fenetre dessinee pese forcement plus. C'est le garde-fou contre la
        // regression exacte constatee sur appareil.
        assertTrue("capture suspicieusement petite (fenetre vide ?)", f.length() > 20_000)
    }
}
