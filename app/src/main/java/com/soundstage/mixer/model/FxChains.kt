package com.soundstage.mixer.model

/**
 * Modèle des 2 chaînes FX (Tonic et Pads), indépendantes.
 * Chaque paramètre est un Float 0..1 (UI 0..100 %).
 * L'ordre des effets est fixe et appliqué côté natif :
 *   Filter -> Brightness -> Delay -> Reverb -> Shimmer (Shimmer : Tonic only)
 */
data class SurfaceFxChain(
    val filterOn: Boolean = false,
    val filterAmount: Float = 0.5f,
    val brightnessOn: Boolean = false,
    val brightnessAmount: Float = 0.5f,
    val delayOn: Boolean = false,
    val delayAmount: Float = 0.3f,
    val reverbOn: Boolean = true,
    val reverbAmount: Float = 0.4f,
    val shimmerOn: Boolean = false,
    val shimmerAmount: Float = 0.3f,
) {
    fun enabledCount(includeShimmer: Boolean): Int {
        var n = 0
        if (filterOn) n++
        if (brightnessOn) n++
        if (delayOn) n++
        if (reverbOn) n++
        if (includeShimmer && shimmerOn) n++
        return n
    }
    fun withShimmerDisabled(): SurfaceFxChain = copy(shimmerOn = false, shimmerAmount = 0f)
}

data class FxChainsState(
    val tonic: SurfaceFxChain = SurfaceFxChain(reverbOn = true, reverbAmount = 0.4f, shimmerOn = true, shimmerAmount = 0.4f, brightnessOn = true, brightnessAmount = 0.6f),
    val pads: SurfaceFxChain = SurfaceFxChain(reverbOn = true, reverbAmount = 0.3f, brightnessOn = true, brightnessAmount = 0.5f),
) {
    companion object {
        val DEFAULT = FxChainsState()
    }
}
