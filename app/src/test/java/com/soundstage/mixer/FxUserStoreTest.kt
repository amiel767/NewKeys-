package com.soundstage.mixer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aetherkeys.fx.FxDesign
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxUserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * LES PRESETS UTILISATEUR SURVIVENT AU REDEMARRAGE.
 *
 * Le defaut corrige : `userValues` vivait dans une map de l'hote, donc un preset
 * enregistre disparaissait a la fermeture. Ce qu'on verifie ici est ce qui est
 * ECRIT — on passe par les preferences Robolectric, et on relit avec un cache
 * vide, comme le ferait un nouveau process.
 */
@RunWith(RobolectricTestRunner::class)
class FxUserStoreTest {

    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun a_user_preset_survives_a_restart() {
        FxUserStore.app = ctx
        FxUserStore.clearCache()
        FxUserStore.delete(FxDesign.SPACE.id, "Mon Hall")

        FxUserStore.put(FxDesign.SPACE.id, "Mon Hall", List(15) { it / 15f })

        // Nouveau process : le miroir memoire est vide, tout vient du disque.
        FxUserStore.clearCache()
        val back = FxUserStore.load(FxDesign.SPACE.id).firstOrNull { it.name == "Mon Hall" }
        assertTrue("le preset enregistre doit revenir du disque", back != null)
        assertEquals(15, back!!.values.size)
        assertEquals(14f / 15f, back.values.last(), 1e-4f)
    }

    @Test
    fun saving_the_same_name_replaces_it() {
        FxUserStore.app = ctx
        FxUserStore.clearCache()
        FxUserStore.delete(FxDesign.SPACE.id, "Doublon")

        FxUserStore.put(FxDesign.SPACE.id, "Doublon", List(15) { 0.1f })
        FxUserStore.put(FxDesign.SPACE.id, "Doublon", List(15) { 0.9f })

        FxUserStore.clearCache()
        val all = FxUserStore.load(FxDesign.SPACE.id).filter { it.name == "Doublon" }
        assertEquals("un meme nom ne doit pas se dupliquer", 1, all.size)
        assertEquals(0.9f, all.first().values.first(), 1e-4f)
    }

    @Test
    fun deleting_a_user_preset_removes_it_for_good() {
        FxUserStore.app = ctx
        FxUserStore.clearCache()
        FxUserStore.put(FxDesign.SPACE.id, "Ephémère", List(15) { 0.5f })
        FxUserStore.delete(FxDesign.SPACE.id, "Ephémère")

        FxUserStore.clearCache()
        assertTrue(FxUserStore.load(FxDesign.SPACE.id).none { it.name == "Ephémère" })
    }

    /**
     * Le nom du preset fait partie de l'etat de la case. Sans cette relecture, un
     * redemarrage laissait la case vide alors que le son etait le bon.
     */
    @Test
    fun the_preset_name_of_a_slot_survives() {
        val a = FxController(1, ctx)
        a.effectIds[0] = FxDesign.SPACE.id
        a.presetNames[0] = "Cathedral"
        a.save()

        val b = FxController(1, ctx)
        b.restore()
        assertEquals("Cathedral", b.presetNames[0])
    }
}
