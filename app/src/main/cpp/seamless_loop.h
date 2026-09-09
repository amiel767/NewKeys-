#pragma once

#include <cmath>
#include <algorithm>
#include <cstring>
#include <cstdint>

/**
 * SEAMLESS LOOP TRIMMER
 * Direct PCM sample buffer manipulation for silence trimming and seamless looping.
 */

struct TrimRange {
    int startIndex;
    int endIndex;
};

/**
 * 1. autoTrimSilence(float* pcmBuffer, int totalSamples, float threshold)
 * - Parcourt le pcmBuffer depuis le début et repère le premier index où l'amplitude dépasse 'threshold'. Soit 'startIndex'.
 * - Parcourt depuis la fin vers l'arrière et repère le dernier index au-dessus du threshold. Soit 'endIndex'.
 * - Retourne ces deux index (startIndex et endIndex).
 */
inline TrimRange autoTrimSilence(const float* pcmBuffer, int totalSamples, float threshold = 0.005f) {
    TrimRange range{0, 0};
    if (pcmBuffer == nullptr || totalSamples <= 0) {
        return range;
    }

    range.endIndex = totalSamples - 1;

    // 1. Recherche du premier échantillon au-dessus du seuil (début)
    for (int i = 0; i < totalSamples; ++i) {
        if (std::fabs(pcmBuffer[i]) >= threshold) {
            range.startIndex = i;
            break;
        }
    }

    // Sécurité si tout le buffer est silencieux
    if (range.startIndex == 0 && std::fabs(pcmBuffer[0]) < threshold) {
        bool hasSignal = false;
        for (int i = 0; i < totalSamples; ++i) {
            if (std::fabs(pcmBuffer[i]) >= threshold) {
                hasSignal = true;
                break;
            }
        }
        if (!hasSignal) {
            range.startIndex = 0;
            range.endIndex = totalSamples - 1;
            return range;
        }
    }

    // 2. Recherche du dernier échantillon au-dessus du seuil (fin vers arrière)
    for (int i = totalSamples - 1; i >= range.startIndex; --i) {
        if (std::fabs(pcmBuffer[i]) >= threshold) {
            range.endIndex = i;
            break;
        }
    }

    // S'assurer que endIndex > startIndex
    if (range.endIndex <= range.startIndex) {
        range.endIndex = std::min(range.startIndex + 1, totalSamples - 1);
    }

    return range;
}

/**
 * 2. processSeamlessLoop(float* outputBuffer, float* pcmBuffer, int startIndex, int endIndex, int& currentReadIndex, int framesToRender)
 * - Copie les données PCM du buffer vers outputBuffer.
 * - Si currentReadIndex atteint endIndex, il réinitialise immédiatement currentReadIndex à startIndex.
 * - Applique un micro Crossfade de 10ms (linear fade) lors de la transition (endIndex -> startIndex)
 *   pour éviter tout clic ou pop numérique au raccordement.
 */
inline void processSeamlessLoop(
    float* outputBuffer,
    const float* pcmBuffer,
    int startIndex,
    int endIndex,
    int& currentReadIndex,
    int framesToRender,
    int sampleRate = 44100
) {
    if (outputBuffer == nullptr || pcmBuffer == nullptr || framesToRender <= 0) {
        return;
    }

    int loopLength = endIndex - startIndex;
    if (loopLength <= 0) {
        std::memset(outputBuffer, 0, framesToRender * sizeof(float));
        return;
    }

    // Micro Crossfade de 10ms (linear fade)
    int crossfadeSamples = (sampleRate * 10) / 1000;
    if (crossfadeSamples > loopLength / 2) {
        crossfadeSamples = loopLength / 2;
    }

    // Borner l'index courant de lecture
    if (currentReadIndex < startIndex || currentReadIndex >= endIndex) {
        currentReadIndex = startIndex;
    }

    for (int i = 0; i < framesToRender; ++i) {
        int distToEnd = endIndex - currentReadIndex;

        if (distToEnd <= crossfadeSamples && crossfadeSamples > 0) {
            // Fraction de transition [0.0f -> 1.0f]
            float alpha = 1.0f - (static_cast<float>(distToEnd) / static_cast<float>(crossfadeSamples));

            // Échantillon sortant (fin de boucle)
            float sampleOut = pcmBuffer[currentReadIndex];

            // Échantillon entrant (début de boucle correspondant)
            int incomingIndex = startIndex + (crossfadeSamples - distToEnd);
            float sampleIn = pcmBuffer[incomingIndex];

            // Linear crossfade anti-clic
            outputBuffer[i] = (sampleOut * (1.0f - alpha)) + (sampleIn * alpha);
        } else {
            outputBuffer[i] = pcmBuffer[currentReadIndex];
        }

        currentReadIndex++;

        // Réinitialisation immédiate à startIndex au raccordement
        if (currentReadIndex >= endIndex) {
            currentReadIndex = startIndex;
        }
    }
}
