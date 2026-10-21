#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---online}"
if [[ $# -gt 1 || ( "$MODE" != --online && "$MODE" != --offline ) ]]; then
 printf 'Usage: bash tools/bootstrap.sh [--online|--offline]\n' >&2; exit 2
fi
export TMPDIR="${TMPDIR:-$HOME/.cache/ros2-pocket/tmp}"
CACHE="${POCKET_TOOLCHAINS:-$HOME/.cache/ros2-pocket/toolchains}"
mkdir -p "$TMPDIR" "$CACHE"
cd "$CACHE"
if [[ "$MODE" == --offline ]] && { ! test -x android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/clang || ! test -d cyclonedds/.git; }; then
 printf 'Offline bootstrap requires the previously verified NDK r28c and pinned Cyclone source. Run --online first.\n' >&2; exit 2
fi
if ! test -x android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/clang; then
 curl --fail --location --retry 2 --connect-timeout 20 --max-time 900 --output android-ndk-r28c-linux.zip.part https://dl.google.com/android/repository/android-ndk-r28c-linux.zip
 python3 -c 'import hashlib;from pathlib import Path;p=Path("android-ndk-r28c-linux.zip.part");h=hashlib.file_digest(p.open("rb"),"sha1").hexdigest();assert p.stat().st_size==722261334 and h=="a7b54a5de87fecd125a17d54f73c446199e72a64";p.rename("android-ndk-r28c-linux.zip")'
 unzip -q android-ndk-r28c-linux.zip
fi
if ! test -d cyclonedds/.git; then git clone --depth 1 --branch 0.10.5 https://github.com/eclipse-cyclonedds/cyclonedds.git; fi
test "$(git -C cyclonedds rev-parse HEAD)" = 2cdd114cbd18340c606573b4cc8dc20cc161ec5a
cmake -S cyclonedds -B cyclone-host -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$CACHE/host" -DBUILD_EXAMPLES=OFF -DBUILD_DDSPERF=OFF -DBUILD_TESTING=OFF -DENABLE_SECURITY=OFF -DENABLE_SSL=OFF -DENABLE_SHM=OFF -DBUILD_SHARED_LIBS=ON -DENABLE_LTO=OFF
cmake --build cyclone-host --parallel 6
cmake --install cyclone-host
cmake -S cyclonedds -B cyclone-android -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_TOOLCHAIN_FILE="$CACHE/android-ndk-r28c/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DCMAKE_INSTALL_PREFIX="$CACHE/android" -DBUILD_EXAMPLES=OFF -DBUILD_DDSPERF=OFF -DBUILD_TESTING=OFF -DBUILD_IDLC=OFF -DENABLE_SECURITY=OFF -DENABLE_SSL=OFF -DENABLE_SHM=OFF -DBUILD_SHARED_LIBS=ON -DENABLE_LTO=OFF
cmake --build cyclone-android --parallel 6
cmake --install cyclone-android
