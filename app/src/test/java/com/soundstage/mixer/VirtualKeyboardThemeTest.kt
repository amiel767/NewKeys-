package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.ui.components.KeyboardLine
import com.soundstage.mixer.ui.components.KeyboardPressStyle
import com.soundstage.mixer.ui.components.KeyboardTheme
import com.soundstage.mixer.ui.components.KeyboardThemes
import com.soundstage.mixer.ui.components.VirtualKeyboardActions
import com.soundstage.mixer.ui.components.VirtualKeyboardContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

/**
 * The neon themes are a claim about pixels, so they are checked as pixels: the LED style must
 * put light *around* a pressed key (additive halo, brighter than the unpressed face), and the
 * OLED theme must render its dark key faces as true #000000.
 *
 * Screenshots land in `src/test/screenshots/vkb_theme_*.png` for the report.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])
class VirtualKeyboardThemeTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val lines = listOf(KeyboardLine("0", "S90 Full Grand", Color(0xFF8FB2E8), 36, 84))

    private fun capture(theme: KeyboardTheme, style: KeyboardPressStyle, file: String, pressed: Set<Int>) {
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0F17))) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                    VirtualKeyboardContainer(
                        lines = lines,
                        expanded = true,
                        pressedNotes = pressed,
                        theme = theme,
                        pressStyle = style,
                        actions = VirtualKeyboardActions.NoOp,
                        modifier = Modifier.height(150.dp)
                    )
                }
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$file")
    }

    /** Two captures in one test: `setContent` may only be called once per test. */
    private fun capturePair(
        theme: KeyboardTheme, style: KeyboardPressStyle,
        fileOn: String, pressed: Set<Int>, fileOff: String
    ) {
        // The holder lives outside the composition: a plain `mutableStateOf` inside setContent
        // would be re-initialised on every recomposition and the change would never stick.
        val holder = PressedHolder(pressed)
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0F17))) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                    VirtualKeyboardContainer(
                        lines = lines,
                        expanded = true,
                        pressedNotes = holder.notes,
                        theme = theme,
                        pressStyle = style,
                        actions = VirtualKeyboardActions.NoOp,
                        modifier = Modifier.height(150.dp)
                    )
                }
            }
        }
        // `waitForIdle` rather than a manual clock: the second capture must observe the state
        // change, and with `autoAdvance = false` the recomposition never ran (both PNGs came out
        // byte-identical). The keyboard's animations settle, so waiting is safe here.
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$fileOn")
        composeTestRule.runOnIdle { holder.notes = emptySet() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$fileOff")
    }

    private class PressedHolder(initial: Set<Int>) {
        var notes by mutableStateOf(initial)
    }

    /**
     * All 14 themes must stay legible: a white key face is clearly lighter than the black key
     * face next to it. Guards against a theme that renders as a flat block. (The white face is
     * *not* compared to the edge — dark themes such as OLED and Dark have a light edge.)
     */
    @Test
    fun everyThemeRendersDistinctKeyFaces() {
        assertEquals(14, KeyboardThemes.all.size)
        val ids = KeyboardThemes.all.map { it.id }
        assertEquals("theme ids must be unique", ids.size, ids.toSet().size)
        KeyboardThemes.all.forEach { t ->
            assertTrue(
                "${t.id}: a white key must be lighter than a black key",
                luminance(t.whiteMid) > luminance(t.blackMid)
            )
        }
    }

    /** The neon family is the only one allowed to claim additive bloom. */
    @Test
    fun onlyTheNeonFamilyUsesBloom() {
        val neon = KeyboardThemes.all.filter { it.neon }.map { it.id }
        assertEquals(listOf("neon_cyan", "neon_magenta", "neon_lime", "neon_amber", "oled"), neon)
    }

    /** OLED is the "true black" theme: dark faces are literally #000000. */
    @Test
    fun oledDarkFacesArePureBlack() {
        val oled = KeyboardThemes.byId("oled")
        assertEquals(Color(0xFF000000), oled.blackTop)
        assertEquals(Color(0xFF000000), oled.blackMid)
        assertEquals(Color(0xFF000000), oled.blackBottom)
    }

    @Test
    fun neonCyanColorStyleCapture() {
        capture(KeyboardThemes.byId("neon_cyan"), KeyboardPressStyle.Color, "vkb_theme_neon_color.png", setOf(36, 40, 43))
    }

    @Test
    fun oledCapture() {
        capture(KeyboardThemes.byId("oled"), KeyboardPressStyle.Led, "vkb_theme_oled.png", setOf(36, 40, 43))
    }

    /**
     * The actual claim behind "LED must really glow": pressing keys must ADD light, not just
     * recolour them. Measured as the number of bright pixels in the pressed capture against the
     * same theme with nothing pressed. A fill-only implementation lands near 1x; the additive
     * bloom roughly doubles it.
     */
    @Test
    fun ledAddsLightAroundPressedKeys() {
        capturePair(
            KeyboardThemes.byId("neon_cyan"), KeyboardPressStyle.Led,
            "vkb_theme_neon_led.png", setOf(36, 40, 43), "vkb_theme_neon_led_off.png"
        )

        val on = brightPixels("src/test/screenshots/vkb_theme_neon_led.png")
        val off = brightPixels("src/test/screenshots/vkb_theme_neon_led_off.png")
        assertTrue(
            "LED must add light: pressed=$on bright pixels vs unpressed=$off",
            on > off * 1.5
        )
    }

    private fun brightPixels(path: String): Int {
        val img = javax.imageio.ImageIO.read(java.io.File(path)) ?: error("cannot read $path")
        var n = 0
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                if (g > 200 && b > 200) n++
            }
        }
        return n
    }

    private fun luminance(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
}
