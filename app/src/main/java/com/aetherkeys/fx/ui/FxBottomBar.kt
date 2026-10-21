package com.aetherkeys.fx.ui

/**
 * Bottom bar values are read from FxGeom (BOTTOM_BAR_Y/H, LEVEL_X/Y/W). This file only documents
 * the contract used by FxOpenPanel: the bar is a plain strip; the level control is the last parameter
 * of every module (params.size - 1), in normalised 0..1, displayed in dB by FxParamFormat.
 */
object FxBottomBar {
    const val LEVEL_MIN_DB = -24f
    const val LEVEL_MAX_DB = 6f

    /** Level in dB from normalised value: linear mapping over the module range, as in the reference. */
    fun levelDb(norm: Float): Float = LEVEL_MIN_DB + (LEVEL_MAX_DB - LEVEL_MIN_DB) * norm
}
