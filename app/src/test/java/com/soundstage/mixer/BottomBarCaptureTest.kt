package com.soundstage.mixer

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
}
