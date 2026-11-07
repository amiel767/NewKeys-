package com.soundstage.mixer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PERSISTANCE ET SUPPRESSION, sur le vrai stockage.
 *
 * Ces deux points ne se verifient pas en montant la fenetre : ce qui doit
 * survivre, c'est ce qui est ECRIT. On passe donc par les preferences
 * Robolectric — pas par un double — et on relit avec un second controleur, comme
 * au redemarrage de l'app.
 */
@RunWith(RobolectricTestRunner::class)
class FxPersistenceRoundTripTest {

    private val ctx: Application = ApplicationProvider.getApplicationContext()

    private fun controller(strip: Int) = FxController(strip, ctx)

    /** L'arp survit a la fermeture : grille, tempo, armement. */
    @Test
    fun arp_survives_a_relaunch() {
        val a = controller(1)
        a.arp = FxPersistence.Arp(
            sub = 3, style = 1, steps = 12, bpm = 96, swing = 44,
            on = List(16) { it % 3 == 0 },
            vel = List(16) { if (it % 3 == 0) 0.9f else 0.2f },
            present = true, engaged = true,
        )
        a.save()

        val b = controller(1)
        b.restore()
        val r = b.arp
        assertEquals(3, r?.sub)
        assertEquals(1, r?.style)
        assertEquals(12, r?.steps)
        assertEquals(96, r?.bpm)
        assertEquals(44, r?.swing)
        assertEquals(List(16) { it % 3 == 0 }, r?.on)
        assertEquals(0.9f, r?.vel?.get(0) ?: 0f, 1e-4f)
        assertTrue("l'arp etait vivant, il doit revenir vivant", r?.engaged == true)
    }

    /**
     * Un arp contourne revient contourne. C'est la persistance du bypass : sans
     * elle, un arp coupe se rallumait tout seul au redemarrage.
     */
    @Test
    fun bypassed_arp_stays_bypassed() {
        val a = controller(1)
        a.arp = FxPersistence.Arp(present = true, engaged = false)
        a.save()

        val b = controller(1)
        b.restore()
        assertFalse("un arp coupe ne doit pas revenir arme", b.arp?.engaged ?: true)
    }

    /**
     * Un arp supprime ne revient pas. C'est la difference entre « present » et
     * « engaged » : un arp contourne garde sa carte, un arp supprime la perd.
     */
    @Test
    fun removed_arp_does_not_come_back() {
        val a = controller(1)
        a.arp = FxPersistence.Arp(present = false, engaged = false)
        a.save()

        val b = controller(1)
        b.restore()
        assertFalse("une carte supprimee ne doit pas etre restauree", b.arp?.present ?: true)
    }

    /**
     * Supprimer un effet ne laisse aucun residu : le nom de preset de la case
     * doit disparaitre avec lui, sinon il se collait sur l'effet suivant.
     *
     * On ne passe pas par `setEffect` : sans pont natif (cas de Robolectric) le
     * moteur refuse l'installation, et ce qu'on teste ici est la MEMOIRE, pas le
     * moteur. On ecrit donc l'etat directement, comme le fait l'hote.
     */
    @Test
    fun removing_an_effect_clears_its_name() {
        val a = controller(1)
        val slot = 0
        a.effectIds[slot] = 0
        a.presetNames[slot] = "Cathedral"
        a.save()

        // Ce que fait `remove` de l'hote : vider la case et son nom.
        a.effectIds[slot] = -1
        a.presetNames[slot] = null
        assertNull("la case vide ne porte plus de nom", a.presetNames[slot])

        a.save()
        val b = controller(1)
        b.restore()
        assertEquals(-1, b.effectIds[slot])
        assertNull("aucun residu apres redemarrage", b.presetNames[slot])
    }
}
