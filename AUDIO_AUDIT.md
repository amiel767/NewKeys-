# Audit audio du projet — synthèse

Date : 2026-10-10
Repository : `amiel767/NewKeys-`

Ce document regroupe les éléments vérifiés dans le code audio du projet, dans une logique d’audit lecture seule.

## 1) Flux Oboe / sortie audio

### 1.1 Ouverture du flux Oboe

Fichier : `app/src/main/cpp/audio_engine.cpp`

```c++
bool AudioEngine::openAndStartStream() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    mOboeActive.store(false, std::memory_order_relaxed);
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(oboe::kUnspecified)
        ->setDataCallback(this)
        ->setErrorCallback(this)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music);

    if (mDriverType == 1) {
        LOGI("Requesting OpenSL ES audio backend...");
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
    } else {
        LOGI("Requesting Oboe High-Performance (AAudio Exclusive) backend...");
        builder.setAudioApi(oboe::AudioApi::AAudio);
    }
```

### 1.2 Paramètres observés
- Direction : `Output`
- Performance mode : `LowLatency`
- Sharing mode : `Exclusive`, puis fallback `Shared`
- Format : `Float`, puis fallback `I16`
- Canaux : `Stereo`
- Fréquence : `kUnspecified` puis réglée selon le flux réel
- API audio : `AAudio` par défaut, `OpenSL ES` si `driverType == 1`
- Usage : `Media`
- Content type : `Music`

### 1.3 Gestion de reconnexion / erreur

```c++
void AudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGI("Oboe stream error/disconnected: %s. Reopening stream asynchronously...", 
        oboe::convertToText(error));
    if (mIsReconnecting.exchange(true)) {
        LOGI("Reconnection already pending or in progress, skipping duplicate request");
        return;
    }
    std::thread([this]() {
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
        openAndStartStream();
        mIsReconnecting.store(false);
    }).detach();
}
```

Le flux réessaie de se reconnecter de manière asynchrone après un `onErrorAfterClose`.

---

## 2) Callback audio complet `onAudioReady`

Fichier : `app/src/main/cpp/audio_engine.cpp`

```c++
oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    static std::atomic<bool> sPriorityConfigured{false};
    if (!sPriorityConfigured.exchange(true, std::memory_order_relaxed)) {
        struct sched_param param{};
        param.sched_priority = 90;
        if (pthread_setschedparam(pthread_self(), SCHED_FIFO, &param) != 0) {
            param.sched_priority = sched_get_priority_max(SCHED_RR);
            pthread_setschedparam(pthread_self(), SCHED_RR, &param);
            LOGI("Oboe audio thread realtime priority configured with policy SCHED_RR");
        } else {
            LOGI("Oboe audio thread realtime priority configured with policy SCHED_FIFO");
        }
    }

    auto startTime = std::chrono::high_resolution_clock::now();

    mOboeActive.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(mRenderMutex);

    oboe::AudioFormat format = audioStream->getFormat();
    if (format == oboe::AudioFormat::I16) {
        auto *outputBuffer16 = static_cast<int16_t *>(audioData);
        size_t totalSamples = static_cast<size_t>(numFrames * 2);
        if (mFloatRenderBuffer.size() < totalSamples) {
            mFloatRenderBuffer.resize(totalSamples, 0.0f);
        }
        float *floatBuf = mFloatRenderBuffer.data();

        // 1. Render unified 16-channel Soundfont engine
        mSynthEngine.renderStereo(floatBuf, numFrames, false);

        // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
        mDrumSampler.renderStereo(floatBuf, numFrames, true);

        if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
            float maxAbs = 0.0f;
            for (size_t i = 0; i < totalSamples; ++i) {
                float absVal = std::abs(floatBuf[i]);
                if (absVal > maxAbs) maxAbs = absVal;
            }

            // 3. Apply Master Delay & Chorus
            mMasterChorus.process(floatBuf, numFrames);
            mMasterDelay.process(floatBuf, numFrames);

            // 4. Apply Master Reverb, SoundGoodizer & Punch
            if (maxAbs > 0.0001f) {
                mMasterReverb.process(floatBuf, numFrames);
                mSoundGoodizer.process(floatBuf, numFrames);
                mMasterPunch.process(floatBuf, numFrames);
            }

            // 5. Apply Spatial Stereo Widener
            mSpatialWidener.process(floatBuf, numFrames);

            // 6. Apply Master 3-Band Biquad EQ
            mEqLow.process(floatBuf, numFrames);
            mEqMid.process(floatBuf, numFrames);
            mEqHigh.process(floatBuf, numFrames);

            // 7. Master Studio Peak Limiter, Parallel Compressor & Sub-Bass Cut HPF
            processMasterChain(floatBuf, numFrames);
        }

        for (size_t i = 0; i < totalSamples; ++i) {
            float s = std::clamp(floatBuf[i], -1.0f, 1.0f);
            outputBuffer16[i] = static_cast<int16_t>(s * 32767.0f);
        }
    } else {
        auto *outputBuffer = static_cast<float *>(audioData);
        size_t totalSamples = static_cast<size_t>(numFrames * 2);

        mSynthEngine.renderStereo(outputBuffer, numFrames, false);
        mDrumSampler.renderStereo(outputBuffer, numFrames, true);

        if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
            float maxAbs = 0.0f;
            for (size_t i = 0; i < totalSamples; ++i) {
                float absVal = std::abs(outputBuffer[i]);
                if (absVal > maxAbs) maxAbs = absVal;
            }

            mMasterChorus.process(outputBuffer, numFrames);
            mMasterDelay.process(outputBuffer, numFrames);

            if (maxAbs > 0.0001f) {
                mMasterReverb.process(outputBuffer, numFrames);
                mSoundGoodizer.process(outputBuffer, numFrames);
                mMasterPunch.process(outputBuffer, numFrames);
            }

            mSpatialWidener.process(outputBuffer, numFrames);
            mEqLow.process(outputBuffer, numFrames);
            mEqMid.process(outputBuffer, numFrames);
            mEqHigh.process(outputBuffer, numFrames);
            processMasterChain(outputBuffer, numFrames);
        }
    }

    return oboe::DataCallbackResult::Continue;
}
```

### 2.1 Fréquence / découpage

Le callback traite `numFrames` venant du stream audio. Le code ne montre pas de découpage manuel en sous-blocs ; il traite le bloc demandé par Oboe dans un seul callback.

### 2.2 Verrous, allocations, logs, appels JNI
- `std::lock_guard<std::mutex> lock(mRenderMutex);` protège la zone de rendu.
- `mFloatRenderBuffer.resize(...)` est un buffer de travail en float.
- Il y a des logs audio (`LOGI`) toutes les 500 callbacks ou si le temps de rendu dépasse 3500 µs.
- Aucun appel JNI n’est présent dans le chemin de rendu audio.
- Le code ne montre pas d’appels bloquants dans la callback, hors `pthread_setschedparam` au démarrage.

### 2.3 Détection d’xruns / underruns

Le code ne contient pas de logique explicite d’underrun/xrun. L’outil Oboe gère les erreurs via `onErrorBeforeClose` et `onErrorAfterClose`.

---

## 3) FluidSynth : synthèse, réglages, rendu

### 3.1 Instance unique / gestion des canaux

Fichier : `app/src/main/cpp/soundfont_engine.cpp`

```c++
bool SoundfontEngine::init(int sampleRate, int polyphony, const char* instanceName) {
    std::lock_guard<std::recursive_mutex> lock(mMutex);
    destroyInternal();

    mInstanceName = instanceName ? instanceName : "FaderEngine";
    mConfiguredPolyphony = std::clamp(polyphony, 16, 256);

    mSettings = new_fluid_settings();
    ...
    fluid_settings_setnum(mSettings, "synth.sample-rate", static_cast<double>(sampleRate));
    fluid_settings_setnum(mSettings, "synth.gain", 2.2);
    fluid_settings_setint(mSettings, "synth.polyphony", mConfiguredPolyphony);
    fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);
    fluid_settings_setint(mSettings, "synth.reverb.active", 0);
    fluid_settings_setint(mSettings, "synth.chorus.active", 1);
    fluid_settings_setint(mSettings, "synth.dynamic-sample-loading", 1);

    mSynth = new_fluid_synth(mSettings);
    ...
    fluid_synth_set_gain(mSynth, 2.2f);
    fluid_synth_set_interp_method(mSynth, -1, FLUID_INTERP_4THORDER);
    fluid_synth_reverb_on(mSynth, -1, 0);
    fluid_synth_chorus_on(mSynth, -1, 1);
```

Paramètres observés :
- `synth.sample-rate` : valeur fournie au moteur
- `synth.gain` : `2.2`
- `synth.polyphony` : `mConfiguredPolyphony` (borné entre 16 et 256)
- `synth.midi-channels` : `kMaxChannels`
- `synth.reverb.active` : `0`
- `synth.chorus.active` : `1`
- `synth.dynamic-sample-loading` : `1`
- `synth` interpolation : `FLUID_INTERP_4THORDER`

### 3.2 Rendement réel dans le callback

Le rendu est fait via `fluid_synth_write_float` dans `SoundfontEngine::renderStereo()` :

```c++
if (!accumulate) {
    fluid_synth_write_float(mSynth, numFrames, outputBuffer, 0, 2, outputBuffer, 1, 2);
} else {
    size_t totalSamples = static_cast<size_t>(numFrames * 2);
    if (mTempRenderBuffer.size() < totalSamples) {
        mTempRenderBuffer.resize(totalSamples, 0.0f);
    }
    std::fill(mTempRenderBuffer.begin(), mTempRenderBuffer.begin() + totalSamples, 0.0f);

    fluid_synth_write_float(
        mSynth,
        numFrames,
        mTempRenderBuffer.data(), 0, 2,
        mTempRenderBuffer.data(), 1, 2
    );

    for (size_t i = 0; i < totalSamples; ++i) {
        outputBuffer[i] += mTempRenderBuffer[i];
    }
}
```

### 3.3 Soundfonts chargés par piste / synthèse unique

Le code utilise un synthétiseur unique, avec plusieurs canaux MIDI. Le chargement se fait par `fluid_synth_sfload` :

```c++
int sfontId = fluid_synth_sfload(mSynth, absolutePath.c_str(), 0);
```

Poursuite de programme / sélection de preset :

```c++
if (sfont && soundFontId > 0) {
    fluid_synth_sfont_select(mSynth, channel, soundFontId);
    result = fluid_synth_program_select(mSynth, channel, soundFontId, bank, preset);
```

### 3.4 Canaux MIDI et rôle des pistes

Fichier `NativeAudioBridge.kt` :

```kotlin
// UI Tracks 1..8 map strictly to MIDI channels 0..7
const val CHANNEL_TRACK_1 = 0
const val CHANNEL_TRACK_2 = 1
const val CHANNEL_TRACK_3 = 2
const val CHANNEL_TRACK_4 = 3
const val CHANNEL_TRACK_5 = 4
const val CHANNEL_TRACK_6 = 5
const val CHANNEL_TRACK_7 = 6
const val CHANNEL_TRACK_8 = 7

// Isolated dedicated channels for DrumPad & Tonic Pad
const val CHANNEL_DRUMPAD = 8
const val CHANNEL_TONIC_PAD = 9
```

Le code montre 8 voies de piste + 2 canaux dédiés (drum + tonic pad). La piste 1..8 correspond aux canaux MIDI 0..7.

### 3.5 Nombre de pistes / ajout / retrait

Le code visible ne montre ni limite dynamique de pistes ni mécanisme de création/suppression à l’exécution. Il est basé sur un modèle de canaux MIDI statiques et un synthé existant. Cela est compatible avec le fait qu’il n’y a pas de logique `Loops` visible dans le code inspecté.

---

## 4) Effets master actuels

### 4.1 Effets présents dans le code

Fichier : `app/src/main/cpp/audio_engine.h`

```c++
// DSP Modules
StereoBiquad mEqLow;
StereoBiquad mEqMid;
StereoBiquad mEqHigh;
StereoReverb mMasterReverb;
StereoDelay mMasterDelay;
StereoChorus mMasterChorus;
SoundGoodizerDsp mSoundGoodizer;
SpatialWidenerDsp mSpatialWidener;
MasterPunchDsp mMasterPunch;
StereoBiquad mPadFilter;
SimpleMasterCompressor mMasterCompressor;
SoftClipper mSoftClipper;
```

### 4.2 Ce que fait chacun
- `StereoBiquad` : EQ (low / mid / high shelf / peaking)
- `StereoReverb` : réverbération maison (comb + allpass)
- `StereoDelay` : delay / echo
- `StereoChorus` : chorus stéréo
- `SoundGoodizerDsp` : saturation / boost / “fl studio style”
- `SpatialWidenerDsp` : élargissement stéréo
- `MasterPunchDsp` : punch transitoire
- `SimpleMasterCompressor` : compression master
- `SoftClipper` : limiter analogique doux

### 4.3 Paramètres principaux visibles
Dans `audio_engine.h` / `audio_engine.cpp` :
- Reverb : `size`, `decay`, `damp`, `mix`
- Delay : `timeSec`, `feedback`, `mix`, `pingPong`
- Chorus : `rateHz`, `depthMs`, `mix`
- Compressor : `thresholdDb`, `ratio`, `attackMs`, `releaseMs`, `makeupGainDb`
- EQ : `lowGainDb`, `midGainDb`, `highGainDb`
- Spatial widener : `amount`
- Master punch : `amount`
- Pad brightness : `brightness`

### 4.4 Références dans l’API JNI
Le code est déclaré dans `NativeAudioBridge.kt` et connectée dans `native-lib.cpp` :

```kotlin	external fun setMasterEq(lowGainDb: Float, midGainDb: Float, highGainDb: Float)
	external fun setSoundGoodizer(enabled: Boolean, mode: Int, amount: Float)
	external fun setMasterReverb(enabled: Boolean, size: Float, decay: Float, damp: Float, mix: Float)
	external fun setMasterDelay(enabled: Boolean, timeSec: Float, feedback: Float, mix: Float, pingPong: Boolean)
	external fun setMasterChorus(enabled: Boolean, rateHz: Float, depthMs: Float, mix: Float)
	external fun setMasterCompressor(enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupGainDb: Float)
	external fun setSpatialWidener(amount: Float)
	external fun setMasterPunch(amount: Float)
	external fun setPadBrightness(brightness: Float)
```

### 4.5 Quels effets sont réellement connectés au runtime ?
Le code inspecté montre la chaîne d’effets dans le callback, mais le moteur d’interface Kotlin visible n’appelle pas explicitement ces méthodes. La recherche dans `app/src/main/java` a donc donné `No results found` pour les appels de ces fonctions.

Conclusion : les effets sont bien implémentés en C++, mais leur activation explicite n’est pas confirmée dans le code Kotlin visible.

---

## 5) Mécanisme de file lock-free

Fichier : `app/src/main/cpp/soundfont_engine.h`

```c++
template <typename T, size_t Capacity>
class LockFreeRingBuffer {
public:
    LockFreeRingBuffer() : mHead(0), mTail(0) {}

    bool push(const T& item) {
        size_t currentTail = mTail.load(std::memory_order_relaxed);
        size_t nextTail = (currentTail + 1) % Capacity;
        if (nextTail == mHead.load(std::memory_order_acquire)) {
            return false; // Full
        }
        mBuffer[currentTail] = item;
        mTail.store(nextTail, std::memory_order_release);
        return true;
    }

    bool pop(T& item) {
        size_t currentHead = mHead.load(std::memory_order_relaxed);
        if (currentHead == mTail.load(std::memory_order_acquire)) {
            return false; // Empty
        }
        item = mBuffer[currentHead];
        mHead.store((currentHead + 1) % Capacity, std::memory_order_release);
        return true;
    }

    bool peek(T& item) const {
        size_t currentHead = mHead.load(std::memory_order_relaxed);
        if (currentHead == mTail.load(std::memory_order_acquire)) {
            return false; // Empty
        }
        item = mBuffer[currentHead];
        return true;
    }
``` 

### 5.1 Rôle
Cette file sert à envoyer les événements MIDI (note on/off, CC, pitch bend, etc.) sans verrou explicite entre Kotlin et le thread audio.

### 5.2 Producteurs / consommateurs
- Producteurs : appels JNI depuis Kotlin vers sendCC, noteOn, noteOff, pitchBend, setChannelVolume, etc.
- Consommateur : `SoundfontEngine::renderStereo()` dans le callback audio.

---

## 6) SamplePlaybackEngine / DrumPad

Fichier : `app/src/main/cpp/sample_playback_engine.cpp`

```c++
void SamplePlaybackEngine::triggerSample(int sampleId, float velocity01, float pan) {
    mTriggersReceived.fetch_add(1, std::memory_order_relaxed);

    SamplerCommand cmd{};
    cmd.type = CommandType::TRIGGER;
    cmd.sampleId = sampleId;
    cmd.velocity = std::clamp(velocity01, 0.0f, 1.0f);
    cmd.pan = std::clamp(pan, -1.0f, 1.0f);
    cmd.volume = 1.0f;

    mCommandQueue.push(cmd);
}
```

Puis dans `renderStereo()` :

```c++
processCommands();
...
for (size_t vIdx = 0; vIdx < kMaxVoices; ++vIdx) {
    SamplerVoice& v = mVoices[vIdx];
    if (!v.active || !v.pcmData16 || v.totalFrames == 0) continue;
    ...
    outputBuffer[f * 2] += sampL * gainL;
    outputBuffer[f * 2 + 1] += sampR * gainR;
}
```

Le DrumPad est donc un moteur de samples PCM séparé, ajouté au buffer du synthé avant la chaîne FX.

---

## 7) Loops / snapshots / persistance

### 7.1 Loops
Recherche effectuée sur `app/src/main/java` :
- `loop|Loop|LOOP` → `No results found`

Conclusion : aucune logique de boucles ni de lecture synchronisée au tempo n’est visible dans le code inspecté.

### 7.2 Snapshots / presets
Recherche sur `snapshot|Snapshot` dans `app/src/main/java` :
- `No results found`

Conclusion : aucun système de snapshot persistant visible.

---

## 8) Verrous et priorité audio

### 8.1 mRenderMutex / mStreamMutex
Fichier : `app/src/main/cpp/audio_engine.h`

```c++
std::shared_ptr<oboe::AudioStream> mStream;
std::mutex mStreamMutex;
std::mutex mRenderMutex;
```

Utilisation dans `audio_engine.cpp` :
- `mStreamMutex` protège l’ouverture et la fermeture du flux Oboe.
- `mRenderMutex` protège la callback audio.

### 8.2 Priorité thread audio
Dans `onAudioReady()` :

```c++
if (!sPriorityConfigured.exchange(true, std::memory_order_relaxed)) {
    struct sched_param param{};
    param.sched_priority = 90;
    if (pthread_setschedparam(pthread_self(), SCHED_FIFO, &param) != 0) {
        param.sched_priority = sched_get_priority_max(SCHED_RR);
        pthread_setschedparam(pthread_self(), SCHED_RR, &param);
        LOGI("Oboe audio thread realtime priority configured with policy SCHED_RR");
    } else {
        LOGI("Oboe audio thread realtime priority configured with policy SCHED_FIFO");
    }
}
```

Le callback audio essaie de forcer une priorité temps réel.

---

## 9) Schéma texte du flux audio réel

```text
Kotlin / UI / événements MIDI
    │
    ├─ noteOn / noteOff / pitchBend / CC / setChannelVolume / setChannelPan / program select
    │    └─ JNI -> NativeAudioBridge -> native-lib.cpp
    │
    └─ mEventQueue (LockFreeRingBuffer, ring buffer)
            │
            ▼
    Thread audio Oboe (callback onAudioReady)
            │
            ├─ mSynthEngine.renderStereo()
            │      └─ FluidSynth
            │            ├─ lecture des événements MIDI
            │            ├─ stream de synthèse stéréo
            │            └─ sortie float stereo
            │
            ├─ mDrumSampler.renderStereo()
            │      └─ SamplePlaybackEngine PCM WAV / sample drum
            │            └─ addition sur le buffer stereo
            │
            ├─ master chain FX
            │      ├─ MasterDelay
            │      ├─ MasterChorus
            │      ├─ MasterReverb
            │      ├─ SoundGoodizer
            │      ├─ MasterPunch
            │      ├─ SpatialWidener
            │      ├─ EQ Low/Mid/High
            │      └─ processMasterChain()
            │            ├─ MasterCompressor
            │            ├─ SoftClipper
            │            └─ clamp final
            │
            └─ sortie Oboe
                  └─ device audio Android / casque / BT / sortie matérielle
```

---

## 10) Fichiers principaux touchés

Les fichiers qui constituent le cœur du rendu audio sont :
- `app/src/main/cpp/audio_engine.h`
- `app/src/main/cpp/audio_engine.cpp`
- `app/src/main/cpp/soundfont_engine.h`
- `app/src/main/cpp/soundfont_engine.cpp`
- `app/src/main/cpp/sample_playback_engine.cpp`
- `app/src/main/cpp/native-lib.cpp`
- `app/src/main/java/com/soundstage/mixer/audio/NativeAudioBridge.kt`

---

## 11) Ce qui n’a pas pu être confirmé de manière solide

- Les versions exactes SDK / minSdk / targetSdk / compileSdk du projet n’ont pas été confirmées à partir du code audio fourni.
- Le code audio ne contient pas de preuve claire de `Loops` ou de `Snapshots` persistant.
- L’API Kotlin n’a pas montré d’appels actifs aux effets master dans le code visible ; ils sont présents en JNI mais pas branchés dans l’UI observable ici.
- Le projet ne contient pas de code explicite de gestion d’underrun/xrun dans le pipeline audio visible.
- Le code de version / build native (CMake, NDK, ABI, etc.) n’a pas été confirmé dans le périmètre de lecture fournie ici.

---

## 12) Conclusion rapide

Le projet utilise un flux audio moderne basé sur Oboe / AAudio, un synthétiseur FluidSynth unique avec canaux MIDI, un moteur de samples DrumPad séparé, puis une chaîne de master effects en C++. Les mécanismes de rendu et de file lock-free sont présents et cohérents. En revanche, la logique de boucles, de snapshots persistant, et l’intégration visible des effets en Kotlin ne sont pas confirmées dans le code interrogé.

---

Ce document est une synthèse de lecture seule, sans modification du code applicatif.
