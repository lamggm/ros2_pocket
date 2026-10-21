#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" ]]; then
 command -v javac >/dev/null || { printf 'Set JAVA_HOME to a JDK 17 installation.\n' >&2; exit 2; }
 JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
export JAVA_HOME
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="$JAVA_HOME/bin:$PATH"
export TMPDIR="${TMPDIR:-$HOME/.cache/ros2-pocket/tmp}"
mkdir -p "$TMPDIR" build/tests app/src/main/cpp/generated app/src/main/jniLibs/arm64-v8a
TOOLCHAINS="${POCKET_TOOLCHAINS:-$HOME/.cache/ros2-pocket/toolchains}"
NDK="$TOOLCHAINS/android-ndk-r28c"
GRADLE="${POCKET_GRADLE:-gradle}"
command -v "$GRADLE" >/dev/null || { printf 'Install Gradle 8.7 or set POCKET_GRADLE to its executable.\n' >&2; exit 2; }
c++ -std=c++17 -Wall -Wextra -Werror -Iapp/src/main/cpp tests/core_test.cpp -o build/tests/core_test
build/tests/core_test
javac -encoding UTF-8 -d build/tests tests/InputRulesTest.java app/src/main/java/com/lamggm/rospocket/InputRules.java
java -cp build/tests InputRulesTest
"$TOOLCHAINS/host/bin/idlc" -l c -o app/src/main/cpp/generated app/src/main/cpp/PocketTypes.idl
cmake -S app/src/main/cpp -B build/native-host -G Ninja -DCMAKE_PREFIX_PATH="$TOOLCHAINS/host" -DCMAKE_BUILD_TYPE=Debug
cmake --build build/native-host --parallel 4
cmake -S app/src/main/cpp -B build/native-android -G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static -DCycloneDDS_DIR="$TOOLCHAINS/android/lib/cmake/CycloneDDS" -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build/native-android --parallel 4
cp build/native-android/librospocket.so app/src/main/jniLibs/arm64-v8a/
cp "$TOOLCHAINS/android/lib/libddsc.so" app/src/main/jniLibs/arm64-v8a/
"$GRADLE" --no-daemon --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
