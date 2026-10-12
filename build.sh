#!/bin/bash
# Builds the debug APK with the bundled toolchain and copies it to releases/kadamm-v<version>.apk.
# Pass "install" to also install it on a USB-connected phone.
set -e
cd "$(dirname "$0")"
T="$PWD/tools/android-build"
export JAVA_HOME="$T/jdk/jdk-17.0.20.1+1/Contents/Home" ANDROID_HOME="$T/sdk" ANDROID_SDK_ROOT="$T/sdk" GRADLE_USER_HOME="$T/gradle-home"
"$T/gradle-8.9/bin/gradle" --offline assembleDebug -q
VERSION=$(grep versionName version.properties | cut -d= -f2)
cp app/build/outputs/apk/debug/app-debug.apk "releases/kadamm-v$VERSION.apk"
echo "Built releases/kadamm-v$VERSION.apk"
if [ "$1" = "install" ]; then "$T/sdk/platform-tools/adb" install -r "releases/kadamm-v$VERSION.apk"; fi
