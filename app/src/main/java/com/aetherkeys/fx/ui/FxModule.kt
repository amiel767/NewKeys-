package com.aetherkeys.fx.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.aetherkeys.fx.FxKind
import com.aetherkeys.fx.FxRegistry
import com.soundstage.mixer.ui.fx.FxSceneState
import com.soundstage.mixer.ui.fx.FxStyle
import com.soundstage.mixer.ui.fx.drawFxScene
import com.soundstage.mixer.ui.fx.knobsFor

/**
 * Bridges the generated registry (com.aetherkeys.fx) and the already-written
 * scenes (com.soundstage.mixer.ui.fx) to the shell's [EffectModule] contract.
 * Nothing is re-drawn here: [drawScene] delegates to `drawFxScene`, the faithful
 * port of the reference's per-effect drawing functions.
 */
class FxModule(private val effectId: Int) : EffectModule {
    // Same bound as FxRegistry.info(): an unknown id must never index all[id] and crash.
    private val info = FxRegistry.all.getOrElse(effectId) { FxRegistry.all[0] }

    override val id = effectId
    override val name = info.name
    override val type = info.type
    override val kind: FxScreenKind = when (info.kind) {
        FxKind.EFFECT -> FxScreenKind.EFFECT
        FxKind.SHAPER -> FxScreenKind.SHAPER
        FxKind.SOURCE -> FxScreenKind.SOURCE
    }
    override val accent = FxStyle.accent(effectId)
    override val fontId = info.name
    override val presets = info.presets.map { it.name }
    override val params = info.params

    /**
     * Shell knob specs, from the `AB(id,param,x,y,z)` calls in the reference: the
     * measured box carries the position and the knob diameter (z), and the parameter
     * index is bound here instead of being paired by position at draw time.
     */
    override val knobs: List<FxKnobSpec> = knobsFor(effectId).map { s ->
        FxKnobSpec(
            label = info.params.getOrNull(s.param)?.name ?: "",
            box = KnobBox(info.params.getOrNull(s.param)?.name ?: "", s.x, s.y, s.size, s.size),
            paramIndex = s.param,
        )
    }

    private val sceneState = FxSceneState()

    override fun DrawScope.drawScene(params: FloatArray, t: Float, mini: Boolean, tm: TextMeasurer) {
        sceneState.presetIndex = 0
        drawFxScene(effectId, params, t, tm, sceneState)
    }
}

/** All 15 modules, ids 0..14, in registry order. */
fun fxModules(): Map<Int, EffectModule> = FxRegistry.all.associate { it.id to FxModule(it.id) }
