package com.soundstage.mixer.looper

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives LooperLogic / LooperController through the action sequences the page can
 * produce, at the limits (4 layers, 8 bars, every case busy). Any index escape shows
 * up here instead of on the device.
 */
class LooperStressTest {

    private fun controller(): LooperController {
        var t = 0.0
        return LooperController(clock = { t })
    }

    @Test
    fun everyCaseActionOnEveryCaseAtEveryMode() {
        val c = controller()
        val a = c.actions()
        for (id in 0 until 8) {
            a.onRec(id); a.onRec(id); a.onPlay(id); a.onUndo(id); a.onRedo(id)
            a.onDouble(id); a.onHalf(id); a.onDuplicate(id); a.onClear(id)
            a.onToggleMute(id); a.onVolume(id, 50)
            for (l in 0 until 5) { a.onToggleLayerMute(id, l); a.onRemoveLayer(id, l) }
            a.onSheet(id); a.onFocus(id); a.onRename(id, "x")
            a.onStopAt(id, StopAt.Now); a.onAfterRecording(id, AfterRecording.Overdub)
            a.onOneShot(id, true); a.onMidiLearn(id)
            RecordSource.values().forEach { a.onToggleSource(id, it) }
        }
        a.onStopAll(); a.onPlayAll(); a.onRequestClearAll(); a.onConfirmClearAll()
    }

    @Test
    fun buildingFourLayersThenRedoDoesNotEscapeTheLayerMuteList() {
        // 4 layers is the maximum. Redo pushes layerMute back up; if it writes
        // layerMute[c.layers] on a list already at size 4 it throws.
        var c = LoopCase(0, bars = 1, layers = 4, events = emptyList())
        val removed = RemovedLayer(events = emptyList(), muted = false, bars = 1)
        c = c.copy(redo = listOf(removed))
        val back = LooperLogic.redo(c, nowBar = 4.0)
        assertTrue("redo must not throw and must keep layerMute at 4 slots", back.layerMute.size >= 4)
    }

    @Test
    fun everyActionWithAnOutOfRangeIdIsIgnoredInsteadOfThrowing() {
        // A stale focusId/sheetCaseId restored from an old patch, or a legacy id, reaches
        // the controller as an action argument. Indexing cases[id] there threw straight out
        // of the Compose callback and took the page down on the device.
        val c = controller()
        val a = c.actions()
        for (bad in intArrayOf(-1, -50, 8, 99, Int.MIN_VALUE, Int.MAX_VALUE)) {
            a.onRec(bad); a.onPlay(bad); a.onHoldPlay(bad)
            a.onUndo(bad); a.onHoldUndo(bad); a.onRedo(bad)
            a.onRemoveLayer(bad, 0); a.onToggleLayerMute(bad, 0)
            a.onVolume(bad, 50); a.onToggleMute(bad); a.onDouble(bad); a.onHalf(bad)
            a.onDuplicate(bad); a.onClear(bad); a.onMidiLearn(bad); a.onRename(bad, "x")
        }
        // The eight real cases must still be intact and usable afterwards.
        a.onRec(0)
        assertTrue(c.state.value.cases.size == 8)
    }

    @Test
    fun removingEveryLayerKeepsTheMuteListUsable() {
        var c = LoopCase(0, bars = 2, layers = 4)
        for (i in 3 downTo 0) c = LooperLogic.removeLayer(c, i)
        assertTrue(c.layers == 0)
        // A stale index after the last removal must not throw either.
        LooperLogic.removeLayer(c, 3)
        LooperLogic.toggleLayerMute(c, 3)
    }
}
