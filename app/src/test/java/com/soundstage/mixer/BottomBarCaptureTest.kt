package com.soundstage.mixer

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.components.v2.MixerBottomBarV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class BottomBarCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun bottom_bar_capture() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxWidth().background(Color(0xFF0B0F17)).padding(8.dp)) {
                MixerBottomBarV2(
                    masterTrack = TrackChannel(id = 0, name = "Master", isMaster = true, volume = 0.75f),
                    onMasterVolumeChange = {},
                    onMasterFxClick = {},
                    isSnapshotArmMode = false, onToggleSnapshotArm = {},
                    activeSnapshotSlot = "slot_default",
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default", "slot_1"),
                    onSnapshotSlotClick = {},
                    onAddSnapshot = { null },
                    onDeleteSnapshot = {},
                    snapshotCustomNames = emptyMap(),
                    onRenameSnapshotSlot = { _, _ -> },
                    isKeyboardActive = false, onToggleKeyboard = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bottom_bar.png")
    }

    /** 5 snapshot cases: the master must give up room so the cases stay readable. */
    @Test
    fun bottom_bar_capture_5_snapshots() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxWidth().background(Color(0xFF0B0F17)).padding(8.dp)) {
                MixerBottomBarV2(
                    masterTrack = TrackChannel(id = 0, name = "Master", isMaster = true, volume = 0.75f),
                    onMasterVolumeChange = {},
                    onMasterFxClick = {},
                    isSnapshotArmMode = false, onToggleSnapshotArm = {},
                    activeSnapshotSlot = "slot_default",
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default", "slot_1", "slot_2", "slot_3", "slot_4"),
                    onSnapshotSlotClick = {},
                    onAddSnapshot = { null },
                    onDeleteSnapshot = {},
                    snapshotCustomNames = emptyMap(),
                    onRenameSnapshotSlot = { _, _ -> },
                    isKeyboardActive = false, onToggleKeyboard = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bottom_bar_5.png")
    }

    /**
     * Armed: the floppy LED is lit and every snapshot case carries the red halo.
     * This is the state that says "tap a case to save the live mixer into it".
     */
    @Test
    fun bottom_bar_capture_armed() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxWidth().background(Color(0xFF0B0F17)).padding(8.dp)) {
                MixerBottomBarV2(
                    masterTrack = TrackChannel(id = 0, name = "Master", isMaster = true, volume = 0.75f),
                    onMasterVolumeChange = {},
                    onMasterFxClick = {},
                    isSnapshotArmMode = true, onToggleSnapshotArm = {},
                    activeSnapshotSlot = "slot_default",
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default", "slot_1", "slot_2"),
                    onSnapshotSlotClick = {},
                    onAddSnapshot = { null },
                    onDeleteSnapshot = {},
                    snapshotCustomNames = emptyMap(),
                    onRenameSnapshotSlot = { _, _ -> },
                    isKeyboardActive = false, onToggleKeyboard = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bottom_bar_armed.png")
    }

    /** A recalled slot: the active case is lit, the floppy is idle. */
    @Test
    fun bottom_bar_capture_active_slot() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxWidth().background(Color(0xFF0B0F17)).padding(8.dp)) {
                MixerBottomBarV2(
                    masterTrack = TrackChannel(id = 0, name = "Master", isMaster = true, volume = 0.75f),
                    onMasterVolumeChange = {},
                    onMasterFxClick = {},
                    isSnapshotArmMode = false, onToggleSnapshotArm = {},
                    activeSnapshotSlot = "slot_1",
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default", "slot_1", "slot_2"),
                    onSnapshotSlotClick = {},
                    onAddSnapshot = { null },
                    onDeleteSnapshot = {},
                    snapshotCustomNames = emptyMap(),
                    onRenameSnapshotSlot = { _, _ -> },
                    isKeyboardActive = false, onToggleKeyboard = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bottom_bar_active.png")
    }

    /**
     * Guards the two ways the armed rim has already broken.
     *
     * 1. It was drawn with `drawBehind`, so the opaque panel gradient painted over it
     *    and the rim was invisible (0 warm pixels).
     * 2. It was a radial bloom sized 124 % of the case, so it spilled past the corners
     *    and filled the whole rectangle.
     *
     * So: some warm pixels must exist, and none may reach the master/keyboard zone on
     * the right of the bar.
     */
    @Test
    fun armed_rim_is_visible_and_stays_inside_the_cases() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxWidth().background(Color(0xFF0B0F17)).padding(8.dp)) {
                MixerBottomBarV2(
                    masterTrack = TrackChannel(id = 0, name = "Master", isMaster = true, volume = 0.75f),
                    onMasterVolumeChange = {},
                    onMasterFxClick = {},
                    isSnapshotArmMode = true, onToggleSnapshotArm = {},
                    activeSnapshotSlot = "slot_default",
                    snapshots = emptyMap(),
                    configuredSnapshotSlots = listOf("slot_default", "slot_1", "slot_2"),
                    onSnapshotSlotClick = {},
                    onAddSnapshot = { null },
                    onDeleteSnapshot = {},
                    snapshotCustomNames = emptyMap(),
                    onRenameSnapshotSlot = { _, _ -> },
                    isKeyboardActive = false, onToggleKeyboard = {}
                )
            }
        }
        val path = "src/test/screenshots/bottom_bar_armed_rim.png"
        composeTestRule.onRoot().captureRoboImage(filePath = path)

        val bmp = BitmapFactory.decodeFile(java.io.File(path).absolutePath)
        val warm = ArrayList<Pair<Int, Int>>()
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                if (r - g > 10 && r > 60) warm.add(x to y)
            }
        }
        assertTrue("the armed rim must be visible, not painted over", warm.size > 400)
        // The master slider and the keyboard module sit in the last 700 px: a rim that
        // spills past its case would light them up.
        val masterZone = bmp.width - 700
        assertEquals("the rim must not spill onto the master", 0, warm.count { it.first >= masterZone })
    }
}
