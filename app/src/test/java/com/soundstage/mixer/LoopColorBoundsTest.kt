package com.soundstage.mixer

import com.soundstage.mixer.looper.LoopPatchJson
import com.soundstage.mixer.looper.loopCaseColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the negative/out-of-range id crash that only a persisted patch can trigger.
 * Robolectric, because the real `org.json` implementation is needed to parse the file
 * (plain JUnit returns stubs, which would make the assertion vacuous).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoopColorBoundsTest {

    @Test
    fun loopCaseColorSurvivesNegativeAndHugeIds() {
        // Must not throw: `LL.Pal[id % size]` used to index Pal[-1] for a negative id.
        loopCaseColor(-1)
        loopCaseColor(-100)
        loopCaseColor(1000)
        loopCaseColor(Int.MIN_VALUE)
    }

    @Test
    fun aPatchWithNegativeCaseIdKeepsTheIdBounded() {
        // A legacy or hand-edited patches.json can carry any integer as a case id.
        val json = """{"v":1,"active":"p1","patches":[{"id":"p1","name":"Bad","cases":[{"id":-3,"bars":1,"layers":1}]}]}"""
        val (patches, active) = LoopPatchJson.fromJson(json)
        assertEquals(1, patches.size)
        assertEquals("p1", active)
        assertTrue("a loaded case id must be normalised into 0..7", patches[0].cases[0].id in 0..7)
        // And colouring it must not throw.
        patches[0].cases.forEach { loopCaseColor(it.id) }
    }
}
