# Front 13 — mixed-rate multiwatch qualification

This is **qualification software**, not physical acceptance or a release approval.
The base contains the recovered local increment 12; that does not establish its
public integration or transfer any previous APK approval.

## Scope and real execution paths

- `device_multiwatch_fixture.py`: genuine rclpy Jazzy String writers at **1/2/5/10 Hz**,
  sequenced `owner:slot:sequence` values, BE/volatile, reliable/volatile,
  BE/volatile, reliable/transient-local respectively. A separate typed Log writer
  publishes new `mw13.<owner>` sequences to `/rosout`. No command messages,
  parameter changes, services or actions. Names use `run_<uuid>` because a ROS
  topic token cannot start with a digit.
- `multiwatch13_native.cpp`: small host protocol adapter over the **actual Session**.
  Real `watch(4, ...)`, removal, polling and the increment-12 one-shot failure seam;
  no replacement core, fifth slot or changed JNI/UI. Explicit domain 67, loopback.
- `multiwatch13_integration.py` / `campaign()`: four distinct intake counters and
  generations, 12-second 80–120% rate measurement at 500 ms intake polling, fifth
  rejection without config/generation mutation, sustained reliable/BE rejection,
  real topic **and** reader failure in slots 0 and 2, persistent inactive telemetry,
  two advancing recovery values, two readers of the same topic with distinct QoS,
  idempotent removal/recreation, dedicated rosout advancement and clear/resume,
  idempotent stop. Every surviving reader must take **new data**, not just retain
  a fresh label; each disruptive case requires new rosout sequences too.
- Negative-QoS host proof binds the actual discovered writer GID/node/offered QoS
  to contiguous producer events for >=5 s, at most 1.5 s apart. It freezes history
  **before** requesting a newer native capture; zero cumulative intake must persist
  across the same reader/session. Raw snapshots/publication history are retained.
- `run_multiwatch13.py`: existing local image resolved to immutable ID, no pull,
  private `--network none` namespace, read-only precise mounts, 1 CPU / 512 MiB /
  128 PIDs / 170-second process deadline. Cleanup checks the owner label and
  immutable container ID, then independently verifies absence. It never starts
  the generic publisher and never touches other containers.

These are Linux host Session/DDS results, **not Android JNI/physical results**.
The rate tolerance is a short functional gate, not the official soak/performance
budget. All unit/transport/semantic-mutant controls are explicitly synthetic.

## Reproduce the isolated host gate

From this worktree, using existing toolchains read-only:

```bash
export POCKET_TOOLCHAINS=/home/lamggm/Documents/ros2_pocket-xeon/toolchains
mkdir -p app/src/main/cpp/generated build/front13-evidence
"$POCKET_TOOLCHAINS/host/bin/idlc" -l c -o app/src/main/cpp/generated app/src/main/cpp/PocketTypes.idl
cmake -S app/src/main/cpp -B build/multiwatch13-native -G Ninja \
  -DCMAKE_BUILD_TYPE=Debug -DCMAKE_PREFIX_PATH="$POCKET_TOOLCHAINS/host" \
  -DPOCKET_ENABLE_TEST_HOOKS=ON
cmake --build build/multiwatch13-native --parallel 2
c++ -std=c++17 -Wall -Wextra -Werror -DPOCKET_ENABLE_TEST_HOOKS=1 \
  -Iapp/src/main/cpp tests/multiwatch13_native.cpp \
  build/multiwatch13-native/libpocket_core.a -L"$POCKET_TOOLCHAINS/host/lib" \
  -Wl,-rpath,"$POCKET_TOOLCHAINS/host/lib" -lddsc -lpthread \
  -o build/multiwatch13-native/multiwatch13_native
# Set an existing Jazzy fixture image; this runner never pulls/builds it.
python3 tests/run_multiwatch13.py --image "$POCKET_ROS_IMAGE" \
  --binary build/multiwatch13-native/multiwatch13_native \
  --output build/front13-evidence --rmw rmw_fastrtps_cpp
python3 tests/run_multiwatch13.py --image "$POCKET_ROS_IMAGE" \
  --binary build/multiwatch13-native/multiwatch13_native \
  --output build/front13-evidence --rmw rmw_cyclonedds_cpp
python3 -m unittest discover -s tests -p 'test_*multiwatch13.py' -v
python3 tests/multiwatch13_mutation_controls.py
```

Required outcome: one `MULTIWATCH13_RESULT` with exactly 13 cases per real RMW,
exit 0 **and** `cleanupVerified: true`. Missing/broken DDS is an error, never a
synthetic fallback. Python assertions are test oracles; use normal Python, not `-O`.
The omission controls remove the actual gap/fence/new-log predicates and require
behavioral failures. The development evidence also retains a **real DDS** adapter
mutant that falsely rejects the fifth request while replacing slot zero: the
unchanged campaign kills it on the mutated generation.

## Future tablet harness — prepared, NOT executed

`tablet_multiwatch_acceptance.py` has no default serial and validates all inputs
before constructing a transport. It does **not** install APKs, create publishers,
change networking, or use UIAutomator. It verifies the installed DEV/test APK
hashes and exact installed runner declaration, then invokes
`com.lamggm.rospocket.dev.test/com.lamggm.rospocket.MultiwatchInstrumentation`.
The runner uses actual Views and ordinary worker diagnostic projections, plus
one real JNI request for slot 4 on the shared DDS executor. Failure cases consume
increment-12 owned tickets and inspect real receipts; no fabricated DDS returns.
It restores connection preferences and drains the owned lease on exit.

The source AndroidTest manifest declares the existing ReaderFailure runner
**first**, followed by Multiwatch. AGP 8.5.2 rewrites the first instrumentation node
from `defaultConfig`; a lone Multiwatch declaration is silently renamed to the
old runner. Both names and compiled DEX classes must be checked in the **built
APK**, not inferred from source. Gradle and the existing runner remain untouched.

Build with the existing diagnostic opt-in policy and limits (`--max-workers=2`,
JDK 17, explicit matching ANDROID_HOME/ANDROID_SDK_ROOT and GRADLE_USER_HOME).
Do not label an ON candidate ordinary OFF. After assembling the test APK:

```bash
python3 tests/multiwatch13_packaging.py \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  "$POCKET_TOOLCHAINS/sdk/build-tools/35.0.0"
```

That checks both compiled manifest registrations, both DEX classes and the real
APK signature. It proves packaging only; installation/OS registration/execution
remain unverified until the tablet campaign.

For a **separately authorized future bench**, the operator supplies an already
running, controlled fixture container with label `io.lamggm.multiwatch13=<owner>`,
`ROS_DOMAIN_ID=67` (or 68), and the exact fixture script mounted read-only at its
worktree path. It runs `device_multiwatch_fixture.py --publish --owner <owner>
--domain 67 --seconds 240` under the selected Jazzy RMW and retains stdout events.
The operator must arrange its network/interface appropriately; the harness never
creates a host-network container or alters LAN/VPN/firewall. The host-isolated
network-none fixtures above cannot reach a tablet and are not a substitute.

Only after authorizing the tablet and installing/binding a reconciled candidate:

```text
python3 tests/tablet_multiwatch_acceptance.py
  --serial <explicit-authorized-serial> --owner <32-lowercase-hex>
  --domain 67 --fixture-container <immutable-64-hex-container-id>
  --apk-sha256 <installed-DEV-APK-hash>
  --test-apk-sha256 <installed-test-APK-hash> --output <private-existing-directory>
  [--peer <canonical-bench-IPv4>]
```

Run separately for Fast DDS and Cyclone. Empty peer retains automatic discovery.
During the runner's 25-second negative phase, the harness obtains actual producer
history, closes a Java export fence with a newer zero-intake ordinary-worker QA
capture, and requires >=5-second source/capture spans. Host Linux producer clocks
are **never compared to Android nanoTime**. Delayed-history/intake, missing phase,
missing cases, process/generation changes and incomplete instrumentation fail
closed. Reports keep `physicalAcceptance: false`: a successful functional campaign
is still not the official final matrix, soak or operator approval.

## Remaining gates

Independent review, parent reconciliation, final-source CI/build/provenance and
both physical RMW campaigns remain mandatory. No device/emulator was accessed.
No final candidate has been frozen. Physical Wi-Fi/IP tests, official endurance,
battery and final operator acceptance are separate outstanding gates.
