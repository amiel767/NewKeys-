package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.theme.*

/**
 * Geometric Helper for Mapping MIDI Notes to Precise Key Centers & Snap Positions
 */
object KeyPositionMapper {
    const val MIN_MIDI = 33  // A1 (33), Bb1 (34), B1 (35), then C2 (36) to C8 (108)
    const val MAX_MIDI = 108 // C8

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiToNoteName(midi: Int): String {
        val clamped = midi.coerceIn(0, 127)
        val semitone = (clamped % 12 + 12) % 12
        val octave = (clamped / 12) - 1
        return "${NOTE_NAMES[semitone]}$octave"
    }

    fun midiToKeyLeftEdgeFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        if (clampedMidi < 36) {
            return when (clampedMidi) {
                33 -> 0.0f
                34 -> 0.7f
                35 -> 1.0f
                else -> 0.0f
            }
        }
        val octaveIndex = (clampedMidi - 36) / 12
        val semitone = (clampedMidi - 36) % 12
        val octaveWhiteStart = 2.0f + octaveIndex * 7f
        val whiteOffset = when (semitone) {
            0 -> 0.0f  // C
            1 -> 0.7f  // C#
            2 -> 1.0f  // D
            3 -> 1.7f  // D#
            4 -> 2.0f  // E
            5 -> 3.0f  // F
            6 -> 3.7f  // F#
            7 -> 4.0f  // G
            8 -> 4.7f  // G#
            9 -> 5.0f  // A
            10 -> 5.7f // A#
            11 -> 6.0f // B
            else -> 0.0f
        }
        return octaveWhiteStart + whiteOffset
    }

    fun midiToKeyRightEdgeFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        if (clampedMidi < 36) {
            return when (clampedMidi) {
                33 -> 1.0f
                34 -> 1.3f
                35 -> 2.0f
                else -> 1.0f
            }
        }
        val octaveIndex = (clampedMidi - 36) / 12
        val semitone = (clampedMidi - 36) % 12
        val octaveWhiteStart = 2.0f + octaveIndex * 7f
        val whiteOffset = when (semitone) {
            0 -> 1.0f  // C
            1 -> 1.3f  // C#
            2 -> 2.0f  // D
            3 -> 2.3f  // D#
            4 -> 3.0f  // E
            5 -> 4.0f  // F
            6 -> 4.3f  // F#
            7 -> 5.0f  // G
            8 -> 5.3f  // G#
            9 -> 6.0f  // A
            10 -> 6.3f // A#
            11 -> 7.0f // B
            else -> 1.0f
        }
        return octaveWhiteStart + whiteOffset
    }

    /**
     * Maps a MIDI note to its exact center horizontal position (in white key width units).
     * A1 starts at 0.5.
     */
    fun midiToKeyCenterFraction(midiNote: Int): Float {
        val clampedMidi = midiNote.coerceIn(MIN_MIDI, MAX_MIDI)
        if (clampedMidi < 36) {
            return when (clampedMidi) {
                33 -> 0.5f
                34 -> 1.0f
                35 -> 1.5f
                else -> 0.5f
            }
        }
        val octaveIndex = (clampedMidi - 36) / 12
        val semitone = (clampedMidi - 36) % 12
        val octaveWhiteStart = 2.0f + octaveIndex * 7f
        val whiteOffset = when (semitone) {
            0 -> 0.5f  // C
            1 -> 1.0f  // C#
            2 -> 1.5f  // D
            3 -> 2.0f  // D#
            4 -> 2.5f  // E
            5 -> 3.5f  // F
            6 -> 4.0f  // F#
            7 -> 4.5f  // G
            8 -> 5.0f  // G#
            9 -> 5.5f  // A
            10 -> 6.0f // A#
            11 -> 6.5f // B
            else -> 0.5f
        }
        return octaveWhiteStart + whiteOffset
    }

    /**
     * Converts an X position in white key width units to the nearest MIDI note.
     */
    fun xFractionToNearestMidi(xFraction: Float, minAllowedMidi: Int = MIN_MIDI, maxAllowedMidi: Int = MAX_MIDI): Int {
        var closestMidi = minAllowedMidi
        var minDistance = Float.MAX_VALUE

        for (midi in minAllowedMidi..maxAllowedMidi) {
            val center = midiToKeyCenterFraction(midi)
            val dist = kotlin.math.abs(center - xFraction)
            if (dist < minDistance) {
                minDistance = dist
                closestMidi = midi
            }
        }
        return closestMidi
    }

    fun getTrackNeonColor(trackId: Int, saturationMultiplier: Float = 1.0f): Color {
        // Sunday Keys Palette: Strictly matching mixer track channel colors
        val base = when ((trackId - 1) % 8) {
            0 -> Color(0xFFE58929) // Track 1: Amber
            1 -> Color(0xFF36B37E) // Track 2: Emerald Green
            2 -> Color(0xFF3B82F6) // Track 3: Cobalt Sky Blue
            3 -> Color(0xFF9333EA) // Track 4: Violet / Purple
            4 -> Color(0xFFEC4899) // Track 5: Rose
            5 -> Color(0xFF14B8A6) // Track 6: Teal
            6 -> Color(0xFFF59E0B) // Track 7: Gold
            7 -> Color(0xFF6366F1) // Track 8: Indigo
            else -> Color(0xFF3B82F6)
        }
        if (saturationMultiplier == 1.0f) return base
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (base.red * 255).toInt(),
                (base.green * 255).toInt(),
                (base.blue * 255).toInt()
            ),
            hsv
        )
        hsv[1] = (hsv[1] * saturationMultiplier).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun getTrackNeonGradient(trackId: Int, saturationMultiplier: Float = 1.0f): Pair<Color, Color> {
        val c1 = getTrackNeonColor(trackId, saturationMultiplier)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (c1.red * 255).toInt(),
                (c1.green * 255).toInt(),
                (c1.blue * 255).toInt()
            ),
            hsv
        )
        hsv[2] = (hsv[2] * 0.75f).coerceIn(0f, 1f)
        val c2 = Color(android.graphics.Color.HSVToColor(hsv))
        return c1 to c2
    }
}

