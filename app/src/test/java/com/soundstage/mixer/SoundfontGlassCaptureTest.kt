package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.components.SfRow
import com.soundstage.mixer.ui.components.SfWindowActions
import com.soundstage.mixer.ui.components.SoundfontVariant
import com.soundstage.mixer.ui.components.SoundfontWindow
import com.soundstage.mixer.ui.components.SoundfontWindowState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the real soundfont list so the frosted-glass rows can be compared with the
 * previous flat fills. The "All" tab is the one that shows a loaded file next to idle
 * ones, which is exactly the contrast the change is about.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class SoundfontGlassCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun files(loaded: String?) = listOf(
        SfRow.File("Aether Grand.sf2", "Aether Grand.sf2", 12_480_000L, "12 MB", loaded == "Aether Grand.sf2", 8),
        SfRow.File("Aether Strings.sf2", "Aether Strings.sf2", 8_240_000L, "8 MB", loaded == "Aether Strings.sf2", 5),
        SfRow.File("Aether Organ.sf2", "Aether Organ.sf2", 3_100_000L, "3 MB", loaded == "Aether Organ.sf2", 4),
        SfRow.File("Aether Brass.sf2", "Aether Brass.sf2", 1_450_000L, "1.4 MB", loaded == "Aether Brass.sf2", 2),
    )

    /**
     * `loadedFileId` is left null on purpose: it drives which tab the window opens on, and
     * the tab must stay on "All" for the file rows to be on screen. The loaded state is
     * carried by [SfRow.File.isLoaded] instead, which is exactly the flag the glass reads.
     */
    private fun capture(name: String, loaded: String?) {
        composeTestRule.setContent {
            Box(Modifier.size(560.dp, 420.dp).background(Color(0xFF0B0F1A))) {
                SoundfontWindow(
                    state = SoundfontWindowState(
                        isOpen = true,
                        variant = SoundfontVariant.Track,
                        loadedFileId = null,
                        files = files(loaded),
                    ),
                    actions = object : SfWindowActions {},
                )
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_200)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    /** One row loaded, three idle: the glass and the accent bloom must both read. */
    @Test fun sf_glass_loaded() = capture("sf_glass_loaded", "Aether Strings.sf2")

    /** Nothing loaded: every row must sit at the same, quiet level. */
    @Test fun sf_glass_idle() = capture("sf_glass_idle", null)
}
