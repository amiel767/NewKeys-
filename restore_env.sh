#!/bin/bash
# Restaure l'environnement de build apres un wipe de sandbox.
# Le JDK et le keystore disparaissent a chaque wipe ; le depot, non.
set -x
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_SDK_ROOT=/workspace/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -d "$JAVA_HOME" ]; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq openjdk-21-jdk-headless unzip zip
fi

cd /workspace/project/NewKeys-
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$JAVA_HOME/bin/java" -version
echo "JAVA_HOME=$JAVA_HOME"
