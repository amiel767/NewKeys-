package com.soundstage.mixer.instruments

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** No-op engine: nothing here touches audio, but the manager's API requires one. */
private class TestHost : AeHost {
    override fun loadInstrument(strip: Int, fxId: Int) = true
    override fun unloadInstrument(strip: Int) = Unit
    override fun setParam(strip: Int, index: Int, value: Float) = Unit
    override fun noteOn(strip: Int, note: Int, velocity: Int) = Unit
    override fun noteOff(strip: Int, note: Int) = Unit
    override fun allNotesOff(strip: Int) = Unit
    override fun level(strip: Int) = 0f
}

/**
 * The save-to-disk path of the Æther presets, end to end.
 *
 * These go through the REAL [AeUserStore] on a real Context, so they exercise the
 * SharedPreferences read/write and the JSON shape rather than a stub. That matters:
 * the whole point of the feature is that a preset survives leaving the window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AeUserPresetTest {

    private fun freshStore(): android.content.Context {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("aether_instruments", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        AeUserStore.clearCache()
        AeUserStore.app = ctx
        return ctx
    }

    @Test
    fun `saved preset survives a cold reload of the store`() {
        val ctx = freshStore()
        val values = FloatArray(33) { it / 32f }
        AeUserStore.append(AeInstrument.LEADS, "My Lead", values)

        // A fresh process would rebuild the store from disk; dropping the mirror is the
        // closest thing to that here.
        AeUserStore.clearCache()
        AeUserStore.app = ctx
        val reloaded = AeUserStore.load(AeInstrument.LEADS)

        assertEquals(1, reloaded.size)
        assertEquals("My Lead", reloaded[0].name)
        assertEquals(AeUserStore.USER_CATEGORY, reloaded[0].category)
        assertEquals(33, reloaded[0].values.size)
        for (i in values.indices) assertEquals(values[i], reloaded[0].values[i], 1e-6f)
    }

    @Test
    fun `user presets are listed after the factory ones and keep their index`() {
        val ctx = freshStore()
        initAetherInstruments(ctx)
        val manager = AeManager(TestHost(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate))

        val factoryCount = manager.factoryPresets(AeInstrument.LEADS).size
        manager.load(0, AeInstrument.LEADS)
        manager.saveUserPreset(0, "Mine")

        val all = manager.presets(AeInstrument.LEADS)
        assertEquals(factoryCount + 1, all.size)
        assertEquals("Mine", all[factoryCount].name)
        // Saving selects the new preset, so the header chip shows it right away.
        assertEquals(factoryCount, manager.sessions[0]?.preset)

        // Deleting a factory index must be refused: the index arithmetic is what keeps a
        // user preset addressable, and shifting the factory block would break every preset.
        manager.deleteUserPreset(AeInstrument.LEADS, 0)
        assertEquals(factoryCount + 1, manager.presets(AeInstrument.LEADS).size)
        manager.deleteUserPreset(AeInstrument.LEADS, factoryCount)
        assertEquals(factoryCount, manager.presets(AeInstrument.LEADS).size)
    }

    @Test
    fun `old presets are padded to the current parameter count with the neutral value`() {
        val ctx = freshStore()
        initAetherInstruments(ctx)
        val manager = AeManager(TestHost(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate))
        // Every shipped preset predates the intensity parameter, so this is the real case:
        // load one and check the tail is present and neutral, otherwise the slider would
        // write into a slot that does not exist and silently do nothing.
        val s = manager.load(0, AeInstrument.LEADS)
        assertNotNull(s)
        assertEquals(33, s!!.values.size)
        assertEquals(0.5f, s.values[32], 1e-6f)
    }

    @Test
    fun `a corrupt blob is ignored instead of taking the window down`() {
        val ctx = freshStore()
        ctx.getSharedPreferences("aether_instruments", android.content.Context.MODE_PRIVATE)
            .edit().putString("user_${AeInstrument.LEADS.name}", "{not json at all").commit()
        AeUserStore.clearCache()
        AeUserStore.app = ctx

        val loaded = AeUserStore.load(AeInstrument.LEADS)
        assertTrue(loaded.isEmpty())
    }
}
