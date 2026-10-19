#!/usr/bin/env bash
# Runs the Robolectric render loop that produces /tmp/liveui/screen.png.
set -u
export JAVA_HOME=${JAVA_HOME:-/workspace/jdk/jdk21}
export ANDROID_HOME=${ANDROID_HOME:-/workspace/android-sdk}
export PATH="$JAVA_HOME/bin:$PATH"
export LIVEUI_DIR=${LIVEUI_DIR:-/tmp/liveui}
mkdir -p "$LIVEUI_DIR"
cd "$(dirname "$0")/../.."
# -Proborazzi.test.record=true: captureRoboImage only writes in record mode.
exec ./gradlew :app:testDebugUnitTest --tests "*LiveUiLoop*" -Proborazzi.test.record=true
