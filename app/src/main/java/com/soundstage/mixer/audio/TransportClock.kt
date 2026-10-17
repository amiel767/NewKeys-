package com.soundstage.mixer.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Horloge maître partagée par tous les composants rythmiques (métronome,
 * looper, indicateur de beats). Une seule source de vérité pour le BPM,
 * la signature, la subdivision et l'ancrage.
 *
 * Thread-safe. Aucune dépendance Android. Utilisable en test unitaire.
 *
 * Le temps est exprimé en nanosecondes depuis System.nanoTime().
 * L'ancrage `barAnchorNs` représente le début de la mesure 0 de référence.
 * Toutes les positions sont dérivées de (nowNs - barAnchorNs).
 */
class TransportClock {

    data class Signature(val beatsPerBar: Int, val beatUnit: Int) {
        companion object {
            val SUPPORTED: List<Signature> = listOf(
                Signature(4, 4), Signature(3, 4), Signature(2, 4),
                Signature(6, 8), Signature(9, 8), Signature(12, 8),
                Signature(5, 4), Signature(7, 8), Signature(5, 8), Signature(3, 8),
            )
            fun parse(s: String): Signature {
                val parts = s.split("/")
                if (parts.size != 2) return Signature(4, 4)
                val b = parts[0].toIntOrNull() ?: 4
                val u = parts[1].toIntOrNull() ?: 4
                return Signature(b, u)
            }
            fun default() = Signature(4, 4)
        }
        fun label(): String = "$beatsPerBar/$beatUnit"
    }

    enum class Subdivision(val subdivPerBeat: Int, val label: String) {
        Quarter(1, "1/4"),
        Eighth(2, "1/8"),
        Sixteenth(4, "1/16"),
        Triplet(3, "Triplet");
        companion object {
            fun parse(s: String): Subdivision = when (s) {
                "1/8" -> Eighth
                "1/16" -> Sixteenth
                "triplet", "Triplet" -> Triplet
                else -> Quarter
            }
        }
    }

    data class BeatPosition(
        val barIndex: Long,
        val beatInBar: Int,
        val subInBeat: Int,
        val fractionalInSub: Float,
    )

    @Volatile var bpm: Int = 120
        set(value) {
            val clamped = value.coerceIn(40, 240)
            if (clamped != field) {
                field = clamped
                reanchorFromNow()
            }
        }

    @Volatile var signature: Signature = Signature.default()
        set(value) {
            if (value != field) {
                field = value
                reanchorFromNow()
            }
        }

    @Volatile var subdivision: Subdivision = Subdivision.Quarter

    @Volatile private var barAnchorNs: Long = System.nanoTime()

    private val _tickFlow = MutableSharedFlow<TickEvent>(replay = 0, extraBufferCapacity = 64)
    val ticks: SharedFlow<TickEvent> = _tickFlow.asSharedFlow()

    data class TickEvent(
        val barIndex: Long,
        val beatInBar: Int,
        val subInBeat: Int,
        val isAccentStrong: Boolean,
        val isAccentMedium: Boolean,
        val timestampNs: Long,
    )

    fun beatDurationNs(): Long {
        val b = bpm.coerceIn(40, 240)
        return (60_000_000_000L / b.toLong())
    }

    fun barDurationNs(): Long = beatDurationNs() * signature.beatsPerBar.toLong()

    fun subDurationNs(): Long = beatDurationNs() / subdivision.subdivPerBeat.toLong()

    fun barAnchor(): Long = barAnchorNs

    fun currentPosition(): BeatPosition = positionAt(System.nanoTime())

    fun positionAt(nowNs: Long): BeatPosition {
        val barDur = barDurationNs().coerceAtLeast(1L)
        val subDur = subDurationNs().coerceAtLeast(1L)
        val elapsed = (nowNs - barAnchorNs).coerceAtLeast(0L)
        val barIndex = elapsed / barDur
        val inBar = elapsed % barDur
        val beatInBar = (inBar / beatDurationNs()).toInt().coerceIn(0, signature.beatsPerBar - 1)
        val inBeat = inBar % beatDurationNs()
        val subInBeat = (inBeat / subDur).toInt().coerceIn(0, subdivision.subdivPerBeat - 1)
        val frac = ((inBeat % subDur).toFloat() / subDur.toFloat()).coerceIn(0f, 1f)
        return BeatPosition(barIndex, beatInBar, subInBeat, frac)
    }

    fun timeToNextSubdivision(nowNs: Long = System.nanoTime()): Long {
        val subDur = subDurationNs()
        val elapsed = (nowNs - barAnchorNs).coerceAtLeast(0L)
        val next = ((elapsed / subDur) + 1) * subDur
        return (barAnchorNs + next) - nowNs
    }

    fun timeToNextBar(nowNs: Long = System.nanoTime()): Long {
        val barDur = barDurationNs()
        val elapsed = (nowNs - barAnchorNs).coerceAtLeast(0L)
        val next = ((elapsed / barDur) + 1) * barDur
        return (barAnchorNs + next) - nowNs
    }

    fun reanchorTo(nowNs: Long) {
        barAnchorNs = nowNs
    }

    private fun reanchorFromNow() {
        // Ré-ancrage précis : on aligne le prochain battement sur l'instant courant
        // pour éviter la dérive cumulative après un changement de BPM.
        val now = System.nanoTime()
        barAnchorNs = now
    }

    fun emitTickFromScheduler(bar: Long, beat: Int, sub: Int, strong: Boolean, medium: Boolean, ts: Long) {
        _tickFlow.tryEmit(TickEvent(bar, beat, sub, strong, medium, ts))
    }

    fun isStrongBeat(beatInBar: Int): Boolean = beatInBar == 0

    fun isMediumBeat(beatInBar: Int): Boolean {
        return when (signature.beatsPerBar) {
            2 -> beatInBar == 0
            3 -> beatInBar == 0
            4 -> beatInBar == 0 || beatInBar == 2
            5 -> beatInBar == 0 || beatInBar == 3
            6 -> beatInBar == 0 || beatInBar == 3
            7 -> beatInBar == 0 || beatInBar == 2 || beatInBar == 4
            9 -> beatInBar == 0 || beatInBar == 3 || beatInBar == 6
            12 -> beatInBar == 0 || beatInBar == 3 || beatInBar == 6 || beatInBar == 9
            else -> beatInBar == 0
        }
    }
}
