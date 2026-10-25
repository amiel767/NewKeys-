package com.soundstage.mixer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.components.LocalTrackColors
import com.soundstage.mixer.ui.components.TrackColorMode
import com.soundstage.mixer.ui.components.TrackColorsLogic
import com.soundstage.mixer.ui.components.TrackPalettes
import com.soundstage.mixer.ui.settings.SettingsActions
import com.soundstage.mixer.ui.settings.SettingsState
import com.soundstage.mixer.ui.settings.SettingsScreen
import com.soundstage.mixer.ui.settings.SettingsTab
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the Track colors page inside Settings. It is a real Compose render of
 * the shipped screen, so the capture proves the page matches the Settings style
 * and that the mini mixer preview draws the resolved scheme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class TrackColorsCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun render(state: SettingsState, name: String) {
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                val scheme = TrackColorsLogic.scheme(
                    state.trackColorMode, state.trackColorPaletteId,
                    state.trackRandomLanes, state.trackSingleColor
                )
                CompositionLocalProvider(LocalTrackColors provides scheme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                        SettingsScreen(
                            state = state,
                            actions = object : SettingsActions {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test
    fun track_colors_page_palette() {
        render(
            SettingsState(
                tab = SettingsTab.Interface,
                trackColorsOpen = true,
                trackColorMode = TrackColorMode.Palette,
                trackColorPaletteId = TrackPalettes.Rainbow.id
            ),
            "settings_track_colors_page"
        )
    }

    @Test
    fun track_colors_page_single() {
        render(
            SettingsState(
                tab = SettingsTab.Interface,
                trackColorsOpen = true,
                trackColorMode = TrackColorMode.Single,
                trackSingleColor = Color(0xFFEC4899)
            ),
            "settings_track_colors_single"
        )
    }

    @Test
    @Config(qualifiers = "w851dp-h1600dp-440dpi", sdk = [36])
    fun track_colors_page_all_palettes() {
        // Tall viewport: every palette card is in frame at once, so the capture
        // proves the full set renders without scrolling.
        render(
            SettingsState(
                tab = SettingsTab.Interface,
                trackColorsOpen = true,
                trackColorMode = TrackColorMode.Palette,
                trackColorPaletteId = TrackPalettes.Vintage.id
            ),
            "settings_track_colors_all_palettes"
        )
    }
    @Test
    @Config(qualifiers = "w851dp-h1600dp-440dpi", sdk = [36])
    fun interface_tab_has_the_track_colors_card() {
        // The entry point is a tappable card in the Interface tab. A tall viewport
        // puts the whole tab in frame, so the card is reachable without scrolling
        // (scrolling a long column exhausts the Robolectric heap). Assert the card
        // exists and that tapping it opens the page.
        var opened = false
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    SettingsScreen(
                        state = SettingsState(tab = SettingsTab.Interface),
                        actions = object : SettingsActions {
                            override fun onOpenTrackColors() { opened = true }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onNodeWithTag("track_colors_card").assertExists().performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        assertTrue(opened)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_interface_card.png")
    }

    @Test
    @Config(qualifiers = "w851dp-h1600dp-440dpi", sdk = [36])
    fun theColourSlidersAreGoneFromTheInterfaceTab() {
        // Hue / accent saturation / background tone / background lightness were removed on
        // request. "Reset colors" stays. The track colours card is the only colour control
        // left in this tab, so assert both halves of that.
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    SettingsScreen(
                        state = SettingsState(tab = SettingsTab.Interface),
                        actions = object : SettingsActions {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)

        for (gone in listOf(
            "Primary interface hue",
            "Accent color saturation",
            "Background tone saturation",
            "Background lightness"
        )) {
            composeTestRule.onNodeWithText(gone).assertDoesNotExist()
        }
        composeTestRule.onNodeWithText("Reset colors").assertExists()
        composeTestRule.onNodeWithText("VIRTUAL KEYBOARD").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_interface_no_sliders.png")
    }
}
