#include <jni.h>
#include <memory>
#include <string>
#include "audio_engine.h"

static std::unique_ptr<AudioEngine> gEngine;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_startEngine(JNIEnv *env, jobject) {
    if (!gEngine) gEngine = std::make_unique<AudioEngine>();
    return gEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_stopEngine(JNIEnv *env, jobject) {
    if (gEngine) gEngine->stop();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioBridge_loadSoundFont(
    JNIEnv *env, jobject, jstring absolutePath) {
    if (!gEngine) return -1;
    const char *pathChars = env->GetStringUTFChars(absolutePath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(absolutePath, pathChars);
    return gEngine->soundfont().loadSoundFont(path);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_selectProgram(
    JNIEnv *env, jobject, jint channel, jint soundFontId, jint bank, jint preset) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->soundfont().selectProgram(channel, soundFontId, bank, preset);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_noteOn(
    JNIEnv *env, jobject, jint channel, jint midiNote, jint velocity) {
    if (gEngine) gEngine->soundfont().noteOn(channel, midiNote, velocity);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_noteOff(
    JNIEnv *env, jobject, jint channel, jint midiNote) {
    if (gEngine) gEngine->soundfont().noteOff(channel, midiNote);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_allNotesOff(
    JNIEnv *env, jobject, jint channel) {
    if (gEngine) gEngine->soundfont().allNotesOff(channel);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setTrackVolume(
    JNIEnv *env, jobject, jint channel, jfloat volume01) {
    if (gEngine) gEngine->soundfont().setChannelVolume(channel, volume01);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setTrackPan(
    JNIEnv *env, jobject, jint channel, jfloat pan) {
    if (gEngine) gEngine->soundfont().setChannelPan(channel, pan);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_setTrackTranspose(
    JNIEnv *env, jobject, jint channel, jint semitones) {
    if (gEngine) gEngine->soundfont().setChannelTransposeSemitones(channel, semitones);
}
