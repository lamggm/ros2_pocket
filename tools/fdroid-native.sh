#!/usr/bin/env bash
# Source-only native preparation for F-Droid's build phase. Apache-2.0.
set -euo pipefail
if [[ $# -ne 2 ]]; then
 printf 'Usage: bash tools/fdroid-native.sh CYCLONEDDS_SOURCE NDK_ROOT\n' >&2; exit 2
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$(realpath "$1")"
NDK="$(realpath "$2")"
PIN=2cdd114cbd18340c606573b4cc8dc20cc161ec5a
if [[ "$(git -C "$SOURCE" rev-parse HEAD 2>/dev/null || true)" != "$PIN" ]] || ! git -C "$SOURCE" diff --quiet HEAD --; then
 printf 'Expected pinned Cyclone DDS source %s, unchanged tracked files\n' "$PIN" >&2; exit 2
fi
if ! test -f "$NDK/source.properties" || ! grep -Eq '^Pkg.Revision[[:space:]]*=[[:space:]]*28\.2\.13676358[[:space:]]*$' "$NDK/source.properties"; then
 printf 'Expected Android NDK r28c/28.2.13676358\n' >&2; exit 2
fi
JOBS="${POCKET_BUILD_JOBS:-4}"
[[ "$JOBS" =~ ^[1-9][0-9]?$ ]] || { printf 'Invalid POCKET_BUILD_JOBS\n' >&2; exit 2; }
OUT="$ROOT/build/fdroid-native"
mkdir -p "$OUT" "$ROOT/app/src/main/cpp/generated" "$ROOT/app/src/main/jniLibs/arm64-v8a"
COMMON=(-DBUILD_EXAMPLES=OFF -DBUILD_DDSPERF=OFF -DBUILD_TESTING=OFF -DENABLE_SECURITY=OFF -DENABLE_SSL=OFF -DENABLE_SHM=OFF -DBUILD_SHARED_LIBS=ON -DENABLE_LTO=OFF)
cmake -S "$SOURCE" -B "$OUT/cyclone-host-build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$OUT/host" "${COMMON[@]}"
cmake --build "$OUT/cyclone-host-build" --parallel "$JOBS"
cmake --install "$OUT/cyclone-host-build"
cmake -S "$SOURCE" -B "$OUT/cyclone-android-build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DCMAKE_INSTALL_PREFIX="$OUT/android" -DBUILD_IDLC=OFF "${COMMON[@]}"
cmake --build "$OUT/cyclone-android-build" --parallel "$JOBS"
cmake --install "$OUT/cyclone-android-build"
"$OUT/host/bin/idlc" -l c -o "$ROOT/app/src/main/cpp/generated" "$ROOT/app/src/main/cpp/PocketTypes.idl"
"$OUT/host/bin/idlc" -l c -o "$ROOT/app/src/main/cpp/generated" "$ROOT/app/src/main/cpp/GraphHumble.idl"
cmake -S "$ROOT/app/src/main/cpp" -B "$OUT/app-android-build" -G Ninja -DCMAKE_BUILD_TYPE=RelWithDebInfo -DPOCKET_ENABLE_TEST_HOOKS=OFF -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static -DCycloneDDS_DIR="$OUT/android/lib/cmake/CycloneDDS"
cmake --build "$OUT/app-android-build" --parallel "$JOBS"
cp "$OUT/app-android-build/librospocket.so" "$ROOT/app/src/main/jniLibs/arm64-v8a/"
cp "$OUT/android/lib/libddsc.so" "$ROOT/app/src/main/jniLibs/arm64-v8a/"
printf 'F-Droid native preparation complete: source-built ARM64, hooks OFF; unsigned packaging is a separate step.\n'
