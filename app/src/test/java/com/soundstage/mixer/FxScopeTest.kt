package com.soundstage.mixer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.fx.FxPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * L'appartenance des racks FX. Avant, la cle etait `fx_strip_<piste>` seule : tous
 * les patches de tous les setlists partageaient donc un rack unique, et changer de
 * patch laissait les effets du precedent installes. La cle porte desormais le patch
 * proprietaire. Ces tests verrouillent les trois proprietes qui comptent : deux
 * patches ne se voient pas, une piste absente d'un patch reste vide, et un rack
 * existant est repris une seule fois lors de la migration.
 */
@RunWith(RobolectricTestRunner::class)
class FxScopeTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun wipe() = listOf("", "patch_a", "patch_b", "setlist_S1").forEach {
        FxPersistence.clear(ctx, it)
    }

    @Test
    fun twoPatchesDoNotShareTheirRacks() {
        wipe()
        FxPersistence.save(ctx, "patch_a", 2, """{"track":2}""")
        FxPersistence.save(ctx, "patch_b", 2, """{"track":9}""")
        assertEquals("""{"track":2}""", FxPersistence.load(ctx, "patch_a", 2))
        assertEquals("""{"track":9}""", FxPersistence.load(ctx, "patch_b", 2))
        // Le rack non-scope ne doit jamais voir ces deux entrees.
        assertNull(FxPersistence.load(ctx, "", 2))
    }

    @Test
    fun aStripWrittenByOnePatchIsInvisibleToAnother() {
        wipe()
        FxPersistence.save(ctx, "patch_a", 5, """{"track":5}""")
        assertNull(FxPersistence.load(ctx, "patch_b", 5))
        assertEquals(listOf(5), FxPersistence.allStrips(ctx, "patch_a"))
        assertEquals(emptyList<Int>(), FxPersistence.allStrips(ctx, "patch_b"))
    }

    @Test
    fun clearingOnePatchLeavesTheOtherIntact() {
        wipe()
        FxPersistence.save(ctx, "patch_a", 0, "a0")
        FxPersistence.save(ctx, "patch_b", 0, "b0")
        FxPersistence.clear(ctx, "patch_a")
        assertNull(FxPersistence.load(ctx, "patch_a", 0))
        assertEquals("b0", FxPersistence.load(ctx, "patch_b", 0))
    }

    @Test
    fun theLegacyRackIsAdoptedOnceThenDropped() {
        wipe()
        FxPersistence.save(ctx, "", 1, """{"legacy":true}""")
        // Le premier patch a ouvrir recupere le rack herite.
        assertEquals("""{"legacy":true}""", FxPersistence.loadMigrating(ctx, "patch_a", 1))
        // ... et la cle heritee disparait : le patch suivant demarre vide, il n'herite
        // pas du rack d'un autre.
        assertNull(FxPersistence.load(ctx, "", 1))
        assertNull(FxPersistence.loadMigrating(ctx, "patch_b", 1))
    }

    @Test
    fun aScopedRackIsNeverOverwrittenByTheLegacyOne() {
        wipe()
        FxPersistence.save(ctx, "patch_a", 3, "mine")
        FxPersistence.save(ctx, "", 3, "legacy")
        assertEquals("mine", FxPersistence.loadMigrating(ctx, "patch_a", 3))
        // La cle heritee reste disponible pour le prochain patch qui l'adoptera.
        assertEquals("legacy", FxPersistence.load(ctx, "", 3))
    }

    @Test
    fun scopesWithACommonPrefixDoNotLeakIntoEachOther() {
        wipe()
        FxPersistence.save(ctx, "patch_a", 0, "a")
        FxPersistence.save(ctx, "patch_ab", 0, "ab")
        assertEquals(listOf(0), FxPersistence.allStrips(ctx, "patch_a"))
        assertEquals("a", FxPersistence.load(ctx, "patch_a", 0))
        assertEquals("ab", FxPersistence.load(ctx, "patch_ab", 0))
    }
}
