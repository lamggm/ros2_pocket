#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---online}"
if [[ $# -gt 2 || ( "$MODE" != --online && "$MODE" != --offline ) || ( $# -eq 2 && "$2" != --diagnostic ) ]]; then
 printf 'Usage: bash tools/build.sh [--online|--offline] [--diagnostic]\n' >&2; exit 2
fi
NATIVE_HOOKS=OFF
METADATA_ARGS=()
if [[ "${2:-}" == --diagnostic ]]; then NATIVE_HOOKS=ON; METADATA_ARGS+=(--diagnostic --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk); fi
cd "$(dirname "$0")/.."
# Gradle's official wrapper does not apply --offline to distribution downloads.
# This directory key is base36(MD5(distributionUrl)), not a security checksum.
GRADLE_DIST="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu"
if [[ "$MODE" == --offline ]] && { ! test -x "$GRADLE_DIST/gradle-8.7/bin/gradle" || ! test -f "$GRADLE_DIST/gradle-8.7-bin.zip.ok"; }; then
 printf 'Offline build requires Gradle 8.7 wrapper distribution. Run --online with this GRADLE_USER_HOME first.\n' >&2; exit 2
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
 command -v javac >/dev/null || { printf 'Set JAVA_HOME to a JDK 17 installation.\n' >&2; exit 2; }
 JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
export JAVA_HOME
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="$JAVA_HOME/bin:$PATH"
export TMPDIR="${TMPDIR:-$HOME/.cache/ros2-pocket/tmp}"
mkdir -p "$TMPDIR" build/tests app/src/main/cpp/generated app/src/main/jniLibs/arm64-v8a
python3 tools/artifact_metadata.py --source > build/source-before.json
TOOLCHAINS="${POCKET_TOOLCHAINS:-$HOME/.cache/ros2-pocket/toolchains}"
NDK="$TOOLCHAINS/android-ndk-r28c"
GRADLE=./gradlew
bash tools/test.sh --portable
cmake -S app/src/main/cpp -B build/native-android -G Ninja -DPOCKET_ENABLE_TEST_HOOKS="$NATIVE_HOOKS" -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static -DCycloneDDS_DIR="$TOOLCHAINS/android/lib/cmake/CycloneDDS" -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build/native-android --parallel 4
cp build/native-android/librospocket.so app/src/main/jniLibs/arm64-v8a/
cp "$TOOLCHAINS/android/lib/libddsc.so" app/src/main/jniLibs/arm64-v8a/
GRADLE_ARGS=(--no-daemon)
if [[ "$MODE" == --offline ]]; then GRADLE_ARGS+=(--offline); fi
TASKS=(:app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
if [[ "$NATIVE_HOOKS" == ON ]]; then GRADLE_ARGS+=(-PpocketDiagnostic=true); TASKS+=(:app:assembleDebugAndroidTest); fi
"$GRADLE" "${GRADLE_ARGS[@]}" "${TASKS[@]}"
python3 tools/artifact_metadata.py --expected-source build/source-before.json "${METADATA_ARGS[@]}"
