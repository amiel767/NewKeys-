package com.soundstage.mixer

import com.aetherkeys.fx.ArpMapping
import com.aetherkeys.fx.FxDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LA CORRESPONDANCE page <-> moteur de l'arpégiateur.
 *
 * Deux erreurs mesurées sont verrouillées ici :
 *  - `RATE` envoyé `sub / 3f` : « 1/8 » (index 1) tombait sur 0.333 -> taux 2 =
 *    Sixteenth, soit une croche doublée ; et « 1/32 » saturait sur 1/16.
 *  - `MODE` envoyé `mode / 5f` : « random » (index 3) tombait sur 0.6 -> mode 3 =
 *    DownUp, pas Random.
 *
 * Aucune dépendance Android : test JUnit nu.
 */
class ArpMappingTest {

    @Test
    fun chaque_subdivision_tombe_sur_le_bon_taux() {
        // L'ordre de FxDesign.SUBS EST celui d'aetherfx::ArpRate.
        val want = mapOf("1/4" to 0, "1/8" to 1, "1/16" to 2, "1/32" to 3, "1/8T" to 4, "1/16T" to 5)
        FxDesign.SUBS.forEachIndexed { i, label ->
            assertEquals("subdivision $label", want[label], i)
            // et l'aller-retour rend le meme index.
            assertEquals(i, ArpMapping.rateFromNorm(ArpMapping.rateNorm(i)))
        }
    }

    @Test
    fun chaque_sens_tombe_sur_le_bon_mode_moteur() {
        // FxDesign.STYLES = up,down,updown,random -> ArpMode = Up,Down,UpDown,Random.
        val want = mapOf("up" to 0, "down" to 1, "updown" to 2, "random" to 4)
        FxDesign.STYLES.forEachIndexed { i, label ->
            // modeNorm doit viser l'index MOTEUR...
            assertEquals("sens $label", want[label], (ArpMapping.modeNorm(i) * 5f + 0.5f).toInt())
            // ...et la relecture doit rendre l'index de PAGE.
            assertEquals("relecture $label", i, ArpMapping.modeFromNorm(ArpMapping.modeNorm(i)))
        }
        // Le bug historique : « random » (index 3) ne doit PAS viser DownUp (3).
        assertEquals(4, (ArpMapping.modeNorm(3) * 5f + 0.5f).toInt())
    }

    @Test
    fun les_reglages_continus_font_l_aller_retour() {
        for (oct in 1..4) assertEquals(oct, ArpMapping.octavesFromNorm(ArpMapping.octavesNorm(oct)))
        for (sw in listOf(0, 12, 50, 100)) assertEquals(sw, ArpMapping.swingFromNorm(ArpMapping.swingNorm(sw)))
        for (st in listOf(1, 4, 8, 12, 16)) assertEquals(st, ArpMapping.stepsFromNorm(ArpMapping.stepsNorm(st)))
        // Le gate et la velocite sont continus : on tolere le pas de quantification.
        assertEquals(0.5f, ArpMapping.gateFromNorm(ArpMapping.gateNorm(0.5f)), 0.01f)
        assertEquals(0.85f, ArpMapping.velocityFromNorm(ArpMapping.velocityNorm(0.85f)), 0.01f)
    }

    @Test
    fun le_latch_est_un_booleen_franc() {
        assertFalse(ArpMapping.holdFromNorm(0f))
        assertTrue(ArpMapping.holdFromNorm(1f))
        assertTrue(ArpMapping.holdNorm(true) > 0.5f)
        assertFalse(ArpMapping.holdNorm(false) > 0.5f)
    }

    @Test
    fun les_bornes_hors_plage_sont_ramenees() {
        assertEquals(0f, ArpMapping.rateNorm(-5))
        assertEquals(1f, ArpMapping.rateNorm(99))
        assertEquals(1, ArpMapping.octavesFromNorm(-1f))
        assertEquals(4, ArpMapping.octavesFromNorm(2f))
    }
}
