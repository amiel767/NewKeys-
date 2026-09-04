#include <jni.h>
#include <string>
#include <android/log.h>
#include "audio_engine.h"

#define TAG "LiveKeysNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static AudioEngine gAudioEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_startEngine(
        JNIEnv *env,
        jobject /* this */,
        jint driverType) {
    return static_cast<jboolean>(gAudioEngine.start(driverType));
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_stopEngine(
        JNIEnv *env,
        jobject /* this */) {
    gAudioEngine.stop();
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setAudioDriver(
        JNIEnv *env,
        jobject /* this */,
        jint driverType) {
    gAudioEngine.setDriver(driverType);
}

JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioBridge_loadSoundFont(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jstring soundFontPath) {
    if (!soundFontPath) return -1;

    const char *path = env->GetStringUTFChars(soundFontPath, nullptr);
    if (!path) return -1;

    int sfontId = gAudioEngine.getEngine(engineIndex).loadSoundFont(path);
    env->ReleaseStringUTFChars(soundFontPath, path);
    return sfontId;
}

JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioBridge_unloadSoundFont(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint soundFontId) {
    return gAudioEngine.getEngine(engineIndex).unloadSoundFont(soundFontId);
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_audio_NativeAudioBridge_listPresets(
        JNIEnv *env,
        jobject /* this */,
        jint soundFontId) {
    
    // We get the presets from ENGINE_FADER (0) since SoundFonts are loaded there
    std::vector<NativePresetInfo> presets = gAudioEngine.getEngine(0).listPresets(soundFontId);
    
    jclass presetInfoClass = env->FindClass("com/example/audio/PresetInfo");
    if (presetInfoClass == nullptr) return nullptr;
    
    jmethodID constructor = env->GetMethodID(presetInfoClass, "<init>", "(IILjava/lang/String;)V");
    if (constructor == nullptr) return nullptr;
    
    jobjectArray result = env->NewObjectArray(presets.size(), presetInfoClass, nullptr);
    if (result == nullptr) return nullptr;
    
    for (size_t i = 0; i < presets.size(); ++i) {
        jstring nameStr = env->NewStringUTF(presets[i].name.c_str());
        jobject presetObj = env->NewObject(presetInfoClass, constructor, presets[i].bank, presets[i].preset, nameStr);
        env->SetObjectArrayElement(result, i, presetObj);
        env->DeleteLocalRef(nameStr);
        env->DeleteLocalRef(presetObj);
    }
    
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_selectProgram(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jint soundFontId,
        jint bank,
        jint preset) {
    return static_cast<jboolean>(
            gAudioEngine.getEngine(engineIndex).selectProgram(channel, soundFontId, bank, preset));
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_programChange(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jint preset) {
    return static_cast<jboolean>(
            gAudioEngine.getEngine(engineIndex).programChange(channel, preset));
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_noteOn(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jint midiNote,
        jint velocity) {
    gAudioEngine.getEngine(engineIndex).noteOn(channel, midiNote, velocity);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_noteOff(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jint midiNote) {
    gAudioEngine.getEngine(engineIndex).noteOff(channel, midiNote);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_allNotesOff(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel) {
    gAudioEngine.getEngine(engineIndex).allNotesOff(channel);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setTrackVolume(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jfloat volume) {
    gAudioEngine.getEngine(engineIndex).setChannelVolume(channel, volume);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setTrackPan(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jfloat pan) {
    gAudioEngine.getEngine(engineIndex).setChannelPan(channel, pan);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setTrackTranspose(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jint semitones) {
    gAudioEngine.getEngine(engineIndex).setChannelTransposeSemitones(channel, semitones);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_pitchBend(
        JNIEnv *env,
        jobject /* this */,
        jint engineIndex,
        jint channel,
        jint bendValue) {
    gAudioEngine.getEngine(engineIndex).pitchBend(channel, bendValue);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setMasterVolume(
        JNIEnv *env,
        jobject /* this */,
        jfloat volume) {
    float gain = std::clamp(volume, 0.0f, 1.0f) * 1.0f;
    gAudioEngine.setMasterGain(gain);
}

} // extern "C"
