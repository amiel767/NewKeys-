# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Le natif construit ce type par FindClass("com/soundstage/mixer/audio/PresetInfo")
# puis GetMethodID("<init>", ...) : le nom et le constructeur doivent survivre a R8.
-keep class com.soundstage.mixer.audio.PresetInfo { <init>(...); }

# Les methodes `external fun` (JNI) sont conservees par R8, mais on garde la classe
# qui les porte pour que le nom du package ne soit jamais obfusque.
-keepclasseswithmembernames class * { native <methods>; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
