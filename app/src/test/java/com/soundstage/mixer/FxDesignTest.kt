package com.soundstage.mixer

import com.aetherkeys.fx.FxDesign
import com.aetherkeys.fx.FxGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les invariants du design FX, verifies sans rendre une image.
 *
 * Ces tests ne remplacent pas les captures : ils verrouillent les CONTRATS dont
 * une capture ne dit rien — le style du titre suit le preset, chaque preset a sa
 * palette, et la geometrie des cellules est celle qu'on a dessinee. Une
 * regression sur ces points est silencieuse a l'oeil (deux presets qui se
 * ressemblent) et c'est exactement ce qu'on veut empecher.
 *
 * Robolectric est requis : `FxDesign` construit ses couleurs par
 * `android.graphics.Color.parseColor`, qui n'existe pas sur une JVM nue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FxDesignTest {

    private val space = FxDesign.SPACE

    @Test
    fun chaquePresetDeSpaceAPorteSaPalette() {
        space.presets.keys.forEach { name ->
            assertNotNull("preset $name sans palette", space.palettes[name])
        }
    }

    /** Le fond et le coeur du potard viennent du preset : ils doivent differer. */
    @Test
    fun leStyleDuTitreSuitLePreset() {
        val sizes = space.palettes.values.map { it.titleSize }.distinct()
        val spacings = space.palettes.values.map { it.titleSpacing }.distinct()
        val weights = space.palettes.values.map { it.titleWeight }.distinct()
        assertTrue("toutes les tailles de titre identiques : $sizes", sizes.size >= 3)
        assertTrue("toutes les interlettres identiques : $spacings", spacings.size >= 3)
        assertTrue("toutes les graisses identiques : $weights", weights.size >= 2)
    }

    @Test
    fun leFondDeuxTeintesDiffertDunPresetALautre() {
        val bgs = space.palettes.values.map { it.bgA }
        assertEquals("deux presets partagent le meme fond", bgs.size, bgs.distinct().size)
    }

    /** Un seul reglage porte le gros potard, sinon l'index affiche serait ambigu. */
    @Test
    fun unSeulReglagePrincipal() {
        assertEquals(1, space.params.count { it.main })
        val i = space.params.indexOfFirst { it.main }
        assertTrue("l'index principal doit etre un reglage de la grille", i in space.params.indices)
    }

    /**
     * La geometrie : trois colonnes de cellules. A deux colonnes les rangees
     * ecrasaient le potard (52 unites mesurees au lieu de 96) sans que rien ne le
     * signale — d'ou ce test.
     */
    @Test
    fun laGeometrieDesCellulesEstCelleDessinee() {
        assertEquals(3, FxGeo.CELLS)
        assertTrue("le potard doit rester plus grand que la cible de texte", FxGeo.KNOB > FxGeo.KNOB_TEXT)
        assertTrue("le gros potard doit dominer les petits", FxGeo.KNOB_MAIN > FxGeo.KNOB * 2)
        assertTrue("la colonne de gauche doit tenir dans la fenetre", FxGeo.COL_L < FxGeo.W)
    }

    /** Les sept premiers reglages de Space sont un contrat avec le moteur. */
    @Test
    fun lordreDesReglagesMoteurNeChangePas() {
        val first7 = space.params.take(7).map { it.key }
        assertEquals(
            listOf("size", "decay", "pre", "damp", "width", "mix", "diff"),
            first7,
        )
    }
}
