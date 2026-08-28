package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ActivePopup
import com.example.model.DrumSoundType
import com.example.viewmodel.MixerViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Soundfont Live Mixer", appName)
  }

  @Test
  fun `mixer view model handles track controls and volume`() {
    val vm = MixerViewModel()
    val initialState = vm.uiState.value
    assertEquals(8, initialState.tracks.size)

    // Adjust volume
    vm.setTrackVolume(1, 0.9f)
    assertEquals(0.9f, vm.uiState.value.tracks.first { it.id == 1 }.volume, 0.001f)

    // Toggle mute
    vm.toggleMute(1)
    assertTrue(vm.uiState.value.tracks.first { it.id == 1 }.isMuted)

    // Toggle solo
    vm.toggleSolo(2)
    assertTrue(vm.uiState.value.tracks.first { it.id == 2 }.isSolo)
  }

  @Test
  fun `mixer view model handles pad triggers and sound assignment`() {
    val vm = MixerViewModel()

    // Multi-pad selection for tonics
    vm.onTonicNoteClick("C")
    vm.onTonicNoteClick("G")
    assertTrue(vm.uiState.value.activeTonicNotes.contains("C"))
    assertTrue(vm.uiState.value.activeTonicNotes.contains("G"))

    // Drum sound assignment to SF2 Note C1..C8
    vm.assignDrumSf2Note(1, "D#", 4)
    val pad1 = vm.uiState.value.drumPads.first { it.id == 1 }
    assertEquals(DrumSoundType.SF2_NOTE, pad1.soundType)
    assertEquals("D#", pad1.sf2NoteKey)
    assertEquals(4, pad1.sf2NoteOctave)
    assertEquals("D#4", pad1.sf2Note)

    // Steppers and BPM
    vm.updateTranspose(1)
    assertEquals(1, vm.uiState.value.transpose)
    vm.updateBpm(5)
    assertEquals(125, vm.uiState.value.bpm)
  }

  @Test
  fun `keyboard controls and panic function`() {
    val vm = MixerViewModel()

    vm.onKeyDown("C4")
    vm.onKeyDown("E4")
    assertEquals(2, vm.uiState.value.pressedKeys.size)

    vm.toggleSustain()
    assertTrue(vm.uiState.value.isSustainActive)

    vm.toggleVelocity()
    assertTrue(vm.uiState.value.isVelocityEnabled)

    // Panic stops all notes & loops
    vm.triggerPanic()
    assertTrue(vm.uiState.value.pressedKeys.isEmpty())
    assertTrue(vm.uiState.value.activeTonicNotes.isEmpty())
    assertFalse(vm.uiState.value.isSustainActive)
  }
}

