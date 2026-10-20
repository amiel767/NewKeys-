package com.soundstage.mixer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Exercises the real [LooperEngine] (pure Kotlin, no Android, no mocks).
 *
 * The clock is driven by `System.nanoTime()`, so the scheduler is not run here;
 * instead the case machine is driven through its public API and the internal
 * quantization helper is invoked directly to check the swing math.
 */
class LooperEngineTest {

    private fun engine() = LooperEngine(TransportClock())

    private fun quantize(engine: LooperEngine, beatOffset: Double, swing: Int): Double {
        val m: Method = LooperEngine::class.java.getDeclaredMethod(
            "quantizeAndSwing", Double::class.java, Boolean::class.java, Int::class.java, Int::class.java
        )
        m.isAccessible = true
        return m.invoke(engine, beatOffset, true, 4, swing) as Double
    }

    @Test
    fun swingDelaysOddSubdivisionsOnly() {
        val e = engine()

        // Straight (50 %): a 1/16 sits exactly on its grid line.
        assertEquals(0.25, quantize(e, 0.26, 50), 1e-9)
        // Even subdivisions never move, whatever the swing.
        assertEquals(0.0, quantize(e, 0.02, 66), 1e-9)
        assertEquals(0.5, quantize(e, 0.51, 66), 1e-9)

        // Swung (66 %): the odd 1/16 is pushed later, by half of the 0.25 step.
        val swung = quantize(e, 0.26, 66)
        assertTrue("expected swing to delay the odd subdivision, got $swung", swung > 0.25)
        assertEquals(0.33, swung, 1e-9)

        // The next odd subdivision is pushed by the same amount.
        assertEquals(0.75 + 0.08, quantize(e, 0.76, 66), 1e-9)
    }

    @Test
    fun swingIsANoOpWhenQuantizeIsOff() {
        val e = engine()
        val m: Method = LooperEngine::class.java.getDeclaredMethod(
            "quantizeAndSwing", Double::class.java, Boolean::class.java, Int::class.java, Int::class.java
        )
        m.isAccessible = true
        val raw = 0.31234
        assertEquals(raw, m.invoke(e, raw, false, 4, 66) as Double, 1e-12)
    }

    @Test
    fun isPlayingAnyFollowsTheCases() {
        val e = engine()
        assertFalse("nothing should be playing at start", e.state.value.isPlayingAny)

        // Arming a case queues it, which is not playback yet.
        e.tapCase(0)
        assertFalse(e.state.value.isPlayingAny)

        // Clearing every case must leave the flag false.
        e.longPressCaseAction(0, "clearAll")
        assertFalse(e.state.value.isPlayingAny)
        assertTrue(e.state.value.cases.all { it.layers.isEmpty() })
    }

    @Test
    fun stopAllAndPlayAllKeepTheFlagConsistent() {
        val e = engine()
        // Nothing recorded: stop/play all must not flip the flag.
        e.stopAll()
        assertFalse(e.state.value.isPlayingAny)
        e.playAll()
        assertFalse(e.state.value.isPlayingAny)
    }
}
