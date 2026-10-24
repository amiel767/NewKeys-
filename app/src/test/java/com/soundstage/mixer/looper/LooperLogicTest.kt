package com.soundstage.mixer.looper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real [LooperController] with a scripted clock: does it capture a played
 * gesture while a case records, and does it replay that gesture on the next pass?
 *
 * This is the "does the LiveLooper actually make sound" question, answered without
 * Android: the controller is the same object the ViewModel drives.
 */
class LooperLogicTest {

    private class Rig(startBar: Double = 0.0) {
        var bar = startBar
        val replayed = mutableListOf<Triple<Int, LoopEvent, Float>>()
        val ctrl = LooperController(
            clock = { bar },
            onReplay = { id, e, v -> replayed += Triple(id, e, v) }
        )

        /** Advance the clock and let the controller see every intermediate bar. */
        fun advanceTo(target: Double, step: Double = 0.05) {
            var b = bar
            while (b < target) {
                b += step
                bar = b
                ctrl.tick()
            }
        }

        fun case(id: Int) = ctrl.state.value.cases[id]
    }

    @Test
    fun recordsAPlayedPadWhileRecording() {
        val r = Rig()
        r.ctrl.pressRec(0)
        // Quantize Bar by default: the take starts on the next bar line.
        r.advanceTo(1.1)
        assertEquals(CaseMode.Rec, r.case(0).mode)

        r.ctrl.onPlayed(RecordSource.Pads, code = 3, velocity = 110, on = true)
        assertEquals("the played pad must be captured", 1, r.case(0).events.size)
        assertEquals(3, r.case(0).events.first().code)
        assertEquals(RecordSource.Pads, r.case(0).events.first().source)
    }

    @Test
    fun finishesAndReplaysOnTheNextPass() {
        val r = Rig()
        r.ctrl.pressRec(0)
        r.advanceTo(1.05)                    // Rec begins at bar 1
        r.ctrl.onPlayed(RecordSource.Pads, 5, 100, on = true)
        r.advanceTo(1.5)                     // event sits at 0.5 bar inside the take

        r.ctrl.pressRec(0)                   // arm Finish -> next bar (2)
        r.advanceTo(2.05)
        val c = r.case(0)
        assertEquals(CaseMode.Play, c.mode)
        assertEquals("a 1-bar take", 1, c.bars)
        assertEquals(1, c.layers)

        // Rewind the clock and let the loop come round again: the event must fire.
        r.replayed.clear()
        r.bar = 2.0
        r.advanceTo(2.6)
        assertEquals("the recorded pad must replay once per pass", 1, r.replayed.size)
        assertEquals(5, r.replayed.first().second.code)
        assertTrue(r.replayed.first().third > 0f)
    }

    @Test
    fun aSourceTurnedOffIsNotRecorded() {
        val r = Rig()
        r.ctrl.toggleSource(0, RecordSource.Pads)
        r.ctrl.pressRec(0)
        r.advanceTo(1.05)
        r.ctrl.onPlayed(RecordSource.Pads, 2, 100, on = true)
        assertTrue("Pads was disabled for this case", r.case(0).events.isEmpty())

        r.ctrl.onPlayed(RecordSource.Keys, 60, 100, on = true)
        assertEquals(1, r.case(0).events.size)
        assertEquals(RecordSource.Keys, r.case(0).events.first().source)
    }

    @Test
    fun stopFreezesTheLoopAndPlayResumesIt() {
        val r = Rig()
        r.ctrl.pressRec(0)
        r.advanceTo(1.05)
        r.ctrl.onPlayed(RecordSource.Tonic, 48, 90, on = true)
        r.advanceTo(1.5)
        r.ctrl.pressRec(0)
        r.advanceTo(2.05)
        assertEquals(CaseMode.Play, r.case(0).mode)

        r.ctrl.pressPlay(0)                  // Play -> Stop (at the loop boundary)
        r.advanceTo(3.05)
        assertEquals(CaseMode.Stopped, r.case(0).mode)

        r.ctrl.pressPlay(0)                  // Stopped -> Play
        r.advanceTo(4.05)
        assertEquals(CaseMode.Play, r.case(0).mode)
    }

    @Test
    fun overdubAddsASecondLayer() {
        val r = Rig()
        r.ctrl.pressRec(0)
        r.advanceTo(1.05)
        r.ctrl.onPlayed(RecordSource.Pads, 1, 100, on = true)
        r.advanceTo(1.5)
        r.ctrl.pressRec(0)
        r.advanceTo(2.05)
        assertEquals(1, r.case(0).layers)

        r.ctrl.pressRec(0)                   // Play -> arm Overdub
        r.advanceTo(3.05)
        assertEquals(CaseMode.Overdub, r.case(0).mode)
        r.ctrl.onPlayed(RecordSource.Pads, 4, 100, on = true)
        r.ctrl.pressRec(0)                   // Overdub -> arm Commit
        r.advanceTo(4.05)
        assertEquals(CaseMode.Play, r.case(0).mode)
        assertEquals("the overdubbed take becomes layer 2", 2, r.case(0).layers)
        assertTrue(r.case(0).events.any { it.layer == 1 })
    }

    @Test
    fun mutedCaseIsNotReplayed() {
        val r = Rig()
        r.ctrl.pressRec(0)
        r.advanceTo(1.05)
        r.ctrl.onPlayed(RecordSource.Pads, 7, 100, on = true)
        r.advanceTo(1.5)
        r.ctrl.pressRec(0)
        r.advanceTo(2.05)

        r.ctrl.toggleMute(0)
        assertTrue(r.case(0).mute)
        r.replayed.clear()
        r.bar = 2.0
        r.advanceTo(2.6)
        assertTrue("a muted case must stay silent", r.replayed.isEmpty())
    }

    @Test
    fun patchSnapshotRoundTripsThroughTheController() {
        val r = Rig()
        r.ctrl.pressRec(0)
        r.advanceTo(1.05)
        r.ctrl.onPlayed(RecordSource.Pads, 2, 100, on = true)
        r.advanceTo(1.5)
        r.ctrl.pressRec(0)
        r.advanceTo(2.05)

        r.ctrl.saveNewPatch()
        val saved = r.ctrl.state.value
        assertNotNull(saved.activePatchId)
        assertEquals(1, saved.patches.size)
        assertEquals(1, saved.patches.first().cases[0].layers)
        assertFalse("a freshly saved patch is not dirty", saved.isDirty)
    }
}
