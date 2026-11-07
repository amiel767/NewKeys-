package com.soundstage.mixer.fx

import com.aetherkeys.fx.ArpMapping
import com.soundstage.mixer.audio.NativeAudioBridge

/**
 * L'ARPÉGIATEUR VU DU MOTEUR, hors de toute couche Compose.
 *
 * Le moteur intercepte UN canal MIDI : les notes envoyées là nourrissent l'arp au
 * lieu du synthé, et ce sont les notes qu'il émet qui sonnent. Armer le canal EST
 * l'interrupteur (voir `SoundfontEngine::setArpChannel`).
 *
 * Cette logique vivait dans la fenêtre FX, donc elle n'existait que quand la
 * fenêtre était ouverte. Or l'arp doit survivre au redémarrage et rester armé
 * sans qu'on rouvre quoi que ce soit : le ViewModel en a besoin au démarrage et à
 * chaque changement de patch/setlist. Elle est donc ici, partagée, sans état.
 *
 * ISOLEMENT : un rack appartient à UN patch. L'arp aussi. `disarmAll()` coupe
 * tous les canaux avant de charger un patch/setlist, sinon l'arp du patch sortant
 * reste armé sur son canal et avale les notes du patch entrant — c'est le
 * chevauchement entre patches.
 */
object ArpEngine {

    // Index des paramètres natifs (`aetherfx::ArpParam` dans fx_arp.h).
    const val P_ON = 0
    const val P_RATE = 1
    const val P_MODE = 2
    const val P_OCTAVES = 3
    const val P_GATE = 4
    const val P_SWING = 5
    const val P_HOLD = 6
    const val P_VELOCITY = 7
    const val P_STEPS = 8

    /** Le canal qu'intercepte une piste : 0..9 (pistes, Drum, Pad). Le master n'en a pas. */
    fun channelForStrip(strip: Int): Int = if (strip in 0..9) strip else -1

    /** Arme le canal [strip] (ou tous les désarme si `strip` n'en a pas). */
    fun arm(strip: Int, armed: Boolean) {
        val ch = if (armed) channelForStrip(strip) else -1
        NativeAudioBridge.safeSetArpChannel(NativeAudioBridge.ENGINE_FADER, ch)
    }

    fun allNotesOff() = NativeAudioBridge.safeArpAllNotesOff(NativeAudioBridge.ENGINE_FADER)

    /** L'état de page -> l'enregistrement persistant. */
    fun toPersisted(a: com.aetherkeys.fx.ArpState, present: Boolean, engaged: Boolean): FxPersistence.Arp =
        FxPersistence.Arp(
            sub = a.sub, style = a.style, steps = a.steps, bpm = a.bpm, swing = a.swing,
            gate = a.gate, octaves = a.octaves, hold = a.hold, velAmt = a.velAmt,
            on = a.on.toList(), vel = a.vel.toList(), present = present, engaged = engaged,
        )

    /** L'enregistrement persistant -> l'état de page (à l'ouverture de la fenêtre). */
    fun applyTo(a: com.aetherkeys.fx.ArpState, p: FxPersistence.Arp) {
        a.sub = p.sub; a.style = p.style; a.steps = p.steps; a.bpm = p.bpm; a.swing = p.swing
        a.gate = p.gate; a.octaves = p.octaves; a.hold = p.hold; a.velAmt = p.velAmt
        p.on.forEachIndexed { i, v -> if (i < a.on.size) a.on[i] = v }
        p.vel.forEachIndexed { i, v -> if (i < a.vel.size) a.vel[i] = v }
    }

    /**
     * Désarme TOUS les canaux et relâche les notes. À appeler avant de charger un
     * patch ou un setlist : sans ça l'arp du patch sortant reste armé.
     */
    fun disarmAll() {
        NativeAudioBridge.safeSetArpChannel(NativeAudioBridge.ENGINE_FADER, -1)
        NativeAudioBridge.safeArpAllNotesOff(NativeAudioBridge.ENGINE_FADER)
    }

    /** Pousse toute la configuration de l'arpégiateur au moteur (sans l'armer). */
    fun push(strip: Int, a: FxPersistence.Arp, bpm: Int) {
        val e = NativeAudioBridge.ENGINE_FADER
        NativeAudioBridge.safeSetArpParam(e, P_RATE, ArpMapping.rateNorm(a.sub))
        NativeAudioBridge.safeSetArpParam(e, P_MODE, ArpMapping.modeNorm(a.style))
        NativeAudioBridge.safeSetArpParam(e, P_OCTAVES, ArpMapping.octavesNorm(a.octaves))
        NativeAudioBridge.safeSetArpParam(e, P_GATE, ArpMapping.gateNorm(a.gate))
        NativeAudioBridge.safeSetArpParam(e, P_SWING, ArpMapping.swingNorm(a.swing))
        NativeAudioBridge.safeSetArpParam(e, P_HOLD, ArpMapping.holdNorm(a.hold))
        NativeAudioBridge.safeSetArpParam(e, P_VELOCITY, ArpMapping.velocityNorm(a.velAmt))
        NativeAudioBridge.safeSetArpParam(e, P_STEPS, ArpMapping.stepsNorm(a.steps))
        NativeAudioBridge.safeSetArpTempo(e, bpm.coerceIn(20, 400).toDouble())
        for (i in 0 until 16) {
            val active = i < a.steps && a.on.getOrElse(i) { true }
            val v = if (i < a.steps) a.vel.getOrElse(i) { 1f } else 0f
            NativeAudioBridge.safeSetArpStep(e, i, active, v)
        }
    }
}
