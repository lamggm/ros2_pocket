#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---portable}"
if [[ $# -gt 1 || ( "$MODE" != --portable && "$MODE" != --fixture ) ]]; then
 printf 'Usage: bash tools/test.sh [--portable|--fixture]\n' >&2; exit 2
fi
cd "$(dirname "$0")/.."
export TMPDIR="${TMPDIR:-$HOME/.cache/ros2-pocket/tmp}"
mkdir -p "$TMPDIR" build/tests app/src/main/cpp/generated
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
TOOLCHAINS="$(realpath "${POCKET_TOOLCHAINS:-$HOME/.cache/ros2-pocket/toolchains}")"
"$TOOLCHAINS/host/bin/idlc" -l c -o app/src/main/cpp/generated app/src/main/cpp/PocketTypes.idl
cmake -S app/src/main/cpp -B build/native-host -G Ninja -DCMAKE_PREFIX_PATH="$TOOLCHAINS/host" -DCMAKE_BUILD_TYPE=Debug -DPOCKET_TEST_ADDRESS=127.0.0.1
cmake --build build/native-host --parallel 4
if [[ "$MODE" == --portable ]]; then
 python3 -m unittest discover -s tests -p 'test_*.py' -v
 javac -encoding UTF-8 -d build/tests tests/InputRulesTest.java app/src/main/java/com/lamggm/rospocket/InputRules.java
 java -cp build/tests InputRulesTest
 javac -encoding UTF-8 -d build/tests tests/ConnectionPolicyTest.java app/src/main/java/com/lamggm/rospocket/ConnectionPolicy.java
 java -cp build/tests ConnectionPolicyTest
 ctest --test-dir build/native-host -L portable --no-tests=error --output-on-failure --output-junit portable.xml
else
 image="ros2-pocket-ci-fixture:$(python3 -c 'import hashlib;from pathlib import Path;print(hashlib.sha256(b"".join(Path(p).read_bytes() for p in ["tests/fixture.Dockerfile","tests/fixture.py","tests/fixture-loopback.xml"])).hexdigest()[:12])')"
 docker build --platform linux/amd64 -f tests/fixture.Dockerfile -t "$image" tests
 name=""
 cleanup() { if [[ -n "$name" ]]; then docker rm -f "$name" >/dev/null 2>&1 || true; fi; }
 trap cleanup EXIT
 trap 'exit 130' INT
 trap 'exit 143' TERM
 for rmw in rmw_fastrtps_cpp rmw_cyclonedds_cpp; do
  # A fresh unique name; never stop the existing tablet fixture.
  name="ros2-pocket-ci-${rmw}-$$-${RANDOM}"
  docker run --detach --name "$name" --network none --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
   --cap-drop ALL --security-opt no-new-privileges --pids-limit 128 --memory 512m --cpus 1 \
   -v "$PWD/tests:$PWD/tests:ro" \
   -v "$PWD/build/native-host:$PWD/build/native-host:ro" \
   -v "$TOOLCHAINS/host:$TOOLCHAINS/host:ro" \
   -e ROS_DOMAIN_ID=67 -e ROS_LOCALHOST_ONLY=1 -e POCKET_ROSOUT_FIXTURE=1 -e RMW_IMPLEMENTATION="$rmw" \
   -e CYCLONEDDS_URI=file:///fixture/loopback.xml "$image" >/dev/null
  # The reader and publisher share only this container's loopback, not the host.
  # Copy CTest's small manifest to writable tmpfs; executables/libs stay read-only.
  rc=0
  docker exec "$name" sh -ec 'mkdir -p /tmp/native; cp "$1/CTestTestfile.cmake" /tmp/native/; ctest --test-dir /tmp/native -R "^rosout_jazzy_interop$" --no-tests=error --output-on-failure --output-junit /tmp/rosout.xml && ctest --test-dir /tmp/native -L fixture -E "^rosout_jazzy_interop$" --no-tests=error --output-on-failure --output-junit /tmp/fixture.xml' sh "$PWD/build/native-host" || rc=$?
  if [[ "$rc" -ne 0 ]]; then docker logs "$name" || true; exit "$rc"; fi
  docker exec "$name" cat /tmp/fixture.xml > "build/native-host/$rmw.xml"
  docker exec "$name" cat /tmp/rosout.xml > "build/native-host/$rmw-rosout.xml"
  cleanup
  name=""
 done
fi
