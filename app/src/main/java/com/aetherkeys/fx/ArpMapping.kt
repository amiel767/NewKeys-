package com.aetherkeys.fx

/**
 * LA CORRESPONDANCE arpégiateur <-> moteur, en un seul endroit.
 *
 * Le moteur (`aetherfx::ArpEngine`) reçoit ses paramètres NORMALISÉS 0..1, dans
 * l'ordre de `aetherfx::ArpParam` (RATE, MODE, OCTAVES, GATE, SWING, HOLD,
 * VELOCITY, STEPS). La page, elle, parle en valeurs lisibles (subdivision, sens,
 * octaves, grille). Les deux conversions vivaient éparpillées dans l'hôte, avec
 * deux erreurs mesurées :
 *
 *  - `RATE` était envoyé `sub / 3f`. Six subdivisions pour six taux : la page
 *    proposait « 1/8 » et le moteur jouait une **croche doublée** (1/16), et
 *    « 1/32 » saturait sur 1/16. L'index de [FxDesign.SUBS] suit désormais
 *    exactement l'ordre de `ArpRate`, donc la subdivision EST le taux.
 *  - `MODE` était envoyé `mode / 5f` sur une table d'affichage qui ne suit pas
 *    l'ordre du moteur : « random » (index 3) tombait sur **DownUp**. La table
 *    [MODES] fixe l'ordre moteur, une fois.
 *
 * La conversion inverse existe pour la RELECTURE : à l'ouverture, la page doit
 * reprendre ce que le moteur porte déjà, pas le réinitialiser.
 */
object ArpMapping {

    /** Index de [FxDesign.STYLES] -> index de `aetherfx::ArpMode` (Up,Down,UpDown,DownUp,Random,Chord). */
    private val MODES = intArrayOf(0, 1, 2, 4)

    /** Bornes réelles du moteur, miroir de `resolveParams()` dans `fx_arp.h`. */
    const val GATE_MIN = 0.05f
    const val GATE_MAX = 1.0f
    const val VEL_MIN = 0.15f
    const val VEL_MAX = 1.0f
    const val OCT_MIN = 1
    const val OCT_MAX = 4

    // ---- page -> moteur ----------------------------------------------------

    /** La subdivision EST le taux : [FxDesign.SUBS] suit l'ordre de `ArpRate`. */
    fun rateNorm(sub: Int): Float = (sub.coerceIn(0, FxDesign.SUBS.size - 1)).toFloat() /
        (FxDesign.SUBS.size - 1).toFloat()

    fun modeNorm(style: Int): Float {
        val idx = MODES.getOrElse(style.coerceIn(0, MODES.size - 1)) { 0 }
        return idx / 5f
    }

    fun octavesNorm(oct: Int): Float =
        ((oct.coerceIn(OCT_MIN, OCT_MAX) - OCT_MIN).toFloat() / (OCT_MAX - OCT_MIN).toFloat())

    fun gateNorm(gate: Float): Float =
        ((gate.coerceIn(GATE_MIN, GATE_MAX) - GATE_MIN) / (GATE_MAX - GATE_MIN))

    /** `swing` est un pourcentage 0..100 ; le moteur n'en retient que 0.66 au plus. */
    fun swingNorm(swingPct: Int): Float = (swingPct.coerceIn(0, 100)).toFloat() / 100f

    fun holdNorm(hold: Boolean): Float = if (hold) 1f else 0f

    fun velocityNorm(vel: Float): Float =
        ((vel.coerceIn(VEL_MIN, VEL_MAX) - VEL_MIN) / (VEL_MAX - VEL_MIN))

    /** Les 16 colonnes de la grille -> index moteur de la dernière colonne active. */
    fun stepsNorm(steps: Int): Float = ((steps.coerceIn(1, 16) - 1).toFloat() / 15f)

    // ---- moteur -> page (relecture) ----------------------------------------

    fun rateFromNorm(norm: Float): Int =
        (norm.coerceIn(0f, 1f) * (FxDesign.SUBS.size - 1) + 0.5f).toInt().coerceIn(0, FxDesign.SUBS.size - 1)

    /** Index moteur -> index de [FxDesign.STYLES] (0 si inconnu, comme le moteur). */
    fun modeFromNorm(norm: Float): Int {
        val idx = (norm.coerceIn(0f, 1f) * 5f + 0.5f).toInt()
        return MODES.indexOf(idx).takeIf { it >= 0 } ?: 0
    }

    fun octavesFromNorm(norm: Float): Int =
        (OCT_MIN + (norm.coerceIn(0f, 1f) * (OCT_MAX - OCT_MIN) + 0.5f).toInt()).coerceIn(OCT_MIN, OCT_MAX)

    fun gateFromNorm(norm: Float): Float = GATE_MIN + norm.coerceIn(0f, 1f) * (GATE_MAX - GATE_MIN)

    fun swingFromNorm(norm: Float): Int = (norm.coerceIn(0f, 1f) * 100f + 0.5f).toInt().coerceIn(0, 100)

    fun holdFromNorm(norm: Float): Boolean = norm > 0.5f

    fun velocityFromNorm(norm: Float): Float = VEL_MIN + norm.coerceIn(0f, 1f) * (VEL_MAX - VEL_MIN)

    fun stepsFromNorm(norm: Float): Int =
        (1 + (norm.coerceIn(0f, 1f) * 15f + 0.5f).toInt()).coerceIn(1, 16)
}
