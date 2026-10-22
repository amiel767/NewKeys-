package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.components.v2.ChannelStripV2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class ChannelStripCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun channel_strip_capture() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF0B0F17)).padding(10.dp)) {
                Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChannelStripV2(
                        track = TrackChannel(id = 1, name = "Track 1", patchName = "S90 Full Grand",
                            soundfontName = "S90 Full Grand"),
                        isDimmed = false, accentColor = Color(0xFF8FB2E8),
                        onVolumeChange = {}, onPanChange = {}, onSoloToggle = {}, onMuteToggle = {},
                        onTrackNameClick = {}, onFxClick = {}, onTrackSettingsClick = {},
                        onPowerToggle = {}, onDeleteTrack = {},
                        modifier = Modifier.width(150.dp).fillMaxHeight()
                    )
                    ChannelStripV2(
                        track = TrackChannel(id = 2, name = "Track 2", patchName = "Motif XF Galaxy DX",
                            soundfontName = "Motif XF Galaxy DX"),
                        isDimmed = false, accentColor = Color(0xFFD4B06A),
                        onVolumeChange = {}, onPanChange = {}, onSoloToggle = {}, onMuteToggle = {},
                        onTrackNameClick = {}, onFxClick = {}, onTrackSettingsClick = {},
                        onPowerToggle = {}, onDeleteTrack = {},
                        modifier = Modifier.width(150.dp).fillMaxHeight()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/channel_strip.png")
    }
}
