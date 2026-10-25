package com.soundstage.mixer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.soundstage.mixer.ui.components.TrackColorMode
import com.soundstage.mixer.ui.components.TrackColorsLogic
import com.soundstage.mixer.ui.components.TrackPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic for the Settings > Interface > Track colors feature: mode resolution,
 * random draws, hue math and the drift. No Android framework needed beyond Color.
 */
class TrackColorsLogicTest {

    private val single = Color(0xFF8FB2E8)

    @Test
    fun paletteMode_returnsTheEightPaletteColors() {
        val scheme = TrackColorsLogic.scheme(TrackColorMode.Palette, "rainbow", emptyList(), single)
        assertEquals(8, scheme.lanes.size)
        assertEquals(TrackPalettes.Rainbow.colors, scheme.lanes)
    }

    @Test
    fun unknownPalette_fallsBackToDefault() {
        val scheme = TrackColorsLogic.scheme(TrackColorMode.Palette, "does-not-exist", emptyList(), single)
        assertEquals(TrackPalettes.Default.colors, scheme.lanes)
    }

    @Test
    fun singleMode_usesOneColorForEveryLane() {
        val scheme = TrackColorsLogic.scheme(TrackColorMode.Single, "rainbow", emptyList(), single)
        assertEquals(List(8) { single }, scheme.lanes)
        // The master keeps its own colour: the master strip is not a lane.
        assertNotEquals(single, scheme.master)
    }

    @Test
    fun randomMode_usesTheFrozenAssignment() {
        val frozen = List(8) { TrackColorsLogic.hueColor(it * 40f) }
        val scheme = TrackColorsLogic.scheme(TrackColorMode.Random, "default", frozen, single)
        assertEquals(frozen, scheme.lanes)
    }

    @Test
    fun randomMode_withoutAssignment_fallsBackToDefaultLanes() {
        val scheme = TrackColorsLogic.scheme(TrackColorMode.Random, "default", emptyList(), single)
        assertEquals(TrackColorsLogic.defaultLanes, scheme.lanes)
    }

    @Test
    fun randomLanes_areDistinctWhenThePoolIsLargeEnough() {
        val pool = TrackPalettes.Default.colors
        val lanes = TrackColorsLogic.randomLanes(pool, kotlin.random.Random(42))
        assertEquals(8, lanes.size)
        // 8 distinct colours from an 8-colour pool: a permutation, so no repeats.
        assertEquals(8, lanes.map { it.toArgb() }.toSet().size)
    }

    @Test
    fun randomLanes_areDeterministicForAGivenSeed() {
        val a = TrackColorsLogic.randomLanes(TrackPalettes.Rainbow.colors, kotlin.random.Random(7))
        val b = TrackColorsLogic.randomLanes(TrackPalettes.Rainbow.colors, kotlin.random.Random(7))
        assertEquals(a.map { it.toArgb() }, b.map { it.toArgb() })
    }

    @Test
    fun randomLanes_tinyPool_repeatsButStaysEight() {
        val lanes = TrackColorsLogic.randomLanes(listOf(Color.Red, Color.Blue), kotlin.random.Random(1))
        assertEquals(8, lanes.size)
        assertTrue(lanes.all { it == Color.Red || it == Color.Blue })
    }

    @Test
    fun lane_wrapsAroundEightTracks() {
        val scheme = TrackColorsLogic.scheme(TrackColorMode.Palette, "default", emptyList(), single)
        assertEquals(scheme.lane(1), scheme.lane(9))
        assertEquals(scheme.lane(3), scheme.lane(11))
    }

    @Test
    fun hueColor_spansTheWheel() {
        val red = TrackColorsLogic.hueColor(0f)
        val green = TrackColorsLogic.hueColor(120f)
        val blue = TrackColorsLogic.hueColor(240f)
        assertTrue("red dominates", red.red > red.green && red.red > red.blue)
        assertTrue("green dominates", green.green > green.red && green.green > green.blue)
        assertTrue("blue dominates", blue.blue > blue.red && blue.blue > blue.green)
    }

    @Test
    fun drift_shiftsHueButKeepsAlpha() {
        val base = Color(0xFF3B82F6)
        val drifted = TrackColorsLogic.driftColor(base, 14f)
        assertEquals(base.alpha, drifted.alpha, 0.01f)
        assertNotEquals(base.toArgb(), drifted.toArgb())
    }

    @Test
    fun drift_byZero_isIdentity() {
        val base = Color(0xFF3B82F6)
        assertEquals(base.toArgb(), TrackColorsLogic.driftColor(base, 0f).toArgb())
    }

    @Test
    fun everyPaletteHasEightDistinctColors() {
        TrackPalettes.all.forEach { p ->
            assertEquals("${p.id}: lane count", 8, p.colors.size)
            assertEquals("${p.id}: lanes must be distinct", 8, p.colors.map { it.toArgb() }.toSet().size)
        }
    }

    @Test
    fun paletteIdsAndNamesAreUnique() {
        assertEquals(TrackPalettes.all.size, TrackPalettes.all.map { it.id }.toSet().size)
        assertEquals(TrackPalettes.all.size, TrackPalettes.all.map { it.name }.toSet().size)
    }

    @Test
    fun atLeastTenPalettesAreOffered() {
        // Sunset and Ocean plus at least eight more.
        assertTrue("expected a rich palette set, got ${TrackPalettes.all.size}", TrackPalettes.all.size >= 10)
    }

    @Test
    fun everyPaletteResolvesThroughTheScheme() {
        TrackPalettes.all.forEach { p ->
            val scheme = TrackColorsLogic.scheme(TrackColorMode.Palette, p.id, emptyList(), single)
            assertEquals("${p.id} must resolve to its own colours", p.colors, scheme.lanes)
        }
    }

    @Test
    fun everyPaletteIsAValidRandomPool() {
        TrackPalettes.all.forEach { p ->
            val lanes = TrackColorsLogic.randomLanes(p.colors, kotlin.random.Random(p.id.hashCode()))
            assertEquals(8, lanes.size)
            assertEquals("${p.id}: random draw must stay distinct", 8, lanes.map { it.toArgb() }.toSet().size)
            assertTrue("${p.id}: draw must come from the palette", lanes.all { it in p.colors })
        }
    }
}
