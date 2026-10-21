package com.aetherkeys.fx.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.aetherkeys.fx.FxParam

/** Shell-level kind. Shaper and Source are instruments (one per track); Effect goes into the 6 (track) / 8 (master) slots. */
enum class FxScreenKind { EFFECT, SHAPER, SOURCE }

/** One control of an effect screen: its measured box (FxKnobBoxes) and the engine parameter index it drives. */
data class FxKnobSpec(val label: String, val box: KnobBox, val paramIndex: Int)

/**
 * Contract every effect or instrument screen implements. The shell (FxWindow) only knows this interface.
 * Scenes never lay out knobs themselves: positions come from FxKnobBoxes, scaled by the shell.
 */
interface EffectModule {
    val id: Int
    val name: String
    val type: String
    val kind: FxScreenKind
    val accent: Color
    /** Font family id, resolved by the app's font table (see FxFonts). */
    val fontId: String
    val knobs: List<FxKnobSpec>
    val presets: List<String>
    /** Registry parameters of this screen, in engine index order (size = engine param count). */
    val params: List<FxParam>

    /** Registry parameter at [index], or null when the screen has fewer parameters. */
    fun paramAt(index: Int): FxParam? = params.getOrNull(index)

    /**
     * Draws the animated scene inside the scene frame.
     * [t]: seconds since the screen opened (never reset by a preset change).
     * [mini]: true when drawing a rack card (reduced detail, same layout).
     */
    fun DrawScope.drawScene(params: FloatArray, t: Float, mini: Boolean, tm: TextMeasurer)
}

/** Used until a real scene exists: the frame, the knobs and the header are real, the scene area is empty. */
class PlaceholderModule(
    override val id: Int,
    override val name: String,
    override val type: String,
    override val kind: FxScreenKind,
    override val accent: Color,
    override val fontId: String,
    override val knobs: List<FxKnobSpec>,
    override val presets: List<String>,
    override val params: List<FxParam> = emptyList(),
) : EffectModule {
    override fun DrawScope.drawScene(params: FloatArray, t: Float, mini: Boolean, tm: TextMeasurer) = Unit
}
