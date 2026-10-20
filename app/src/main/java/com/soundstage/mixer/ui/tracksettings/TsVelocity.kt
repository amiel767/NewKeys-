package com.soundstage.mixer.ui.tracksettings

import com.soundstage.mixer.ui.components.CurveNode
import com.soundstage.mixer.ui.components.VelocityConfig
import com.soundstage.mixer.ui.components.VelocityCurve
import com.soundstage.mixer.ui.components.VelocityPresets
import kotlin.math.roundToInt

/**
 * Boundary adapter between the Track Settings UI model (VelocitySettings, 5 normalised points)
 * and the single engine model (VelocityConfig, monotone nodes + floor/ceiling/offset/humanize/fixed).
 * Conversion happens only here; the engine stays the only velocity logic.
 */
object TsVelocity {

    /** Node x positions used by the UI (0, 25, 50, 75, 100 %), matching VelocityPresets. */
    private val nodeXs = listOf(0, 32, 64, 96, 127)

    fun toUi(config: VelocityConfig): VelocitySettings {
        val curve = VelocityCurve(config.nodes)
        val ys = VelocityLogic.xs.map { x -> (curve.at(x * 127f) / 127f).coerceIn(0f, 1f) }
        return VelocitySettings(
            preset = detectPreset(ys, config),
            ys = ys,
            floor = config.floor,
            ceiling = config.ceiling,
            offset = config.offset,
            humanize = config.humanize,
            fixed = config.fixedEnabled,
            fixedValue = config.fixedValue
        )
    }

    fun toEngine(ui: VelocitySettings): VelocityConfig {
        val nodes = VelocityLogic.xs.mapIndexed { i, _ ->
            CurveNode(nodeXs[i], (ui.ys[i].coerceIn(0f, 1f) * 127f).roundToInt())
        }
        return VelocityConfig(
            nodes = nodes,
            floor = ui.floor.coerceIn(0, 126),
            ceiling = ui.ceiling.coerceIn(1, 127),
            offset = ui.offset.coerceIn(-40, 40),
            humanize = ui.humanize.coerceIn(0, 24),
            fixedEnabled = ui.fixed,
            fixedValue = ui.fixedValue.coerceIn(1, 127)
        )
    }

    /** True when the 5 points match one of the named presets, so the UI can highlight it. */
    fun detectPreset(ys: List<Float>, config: VelocityConfig): VelocityPreset {
        if (config.fixedEnabled) return VelocityPreset.Custom
        VelocityPreset.values().forEach { p ->
            if (p == VelocityPreset.Custom) return@forEach
            val ref = VelocityLogic.presetYs(p)
            if (ys.indices.all { kotlin.math.abs(ys[it] - ref[it]) < 0.02f }) return p
        }
        return VelocityPreset.Custom
    }

    /** Default UI state for a track that has never been configured. */
    fun defaults(): VelocitySettings = VelocitySettings()
}
