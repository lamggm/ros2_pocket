# ROS Pocket

A native Android, **read-only** ROS 2/DDS inspector: Android UI → JNI/C++ →
Eclipse Cyclone DDS 0.10.5. No rosbridge, auxiliary bridge/server, application
command publishers, service calls or motion commands. This is a development
prototype, not a port of desktop rqt plugins or a full rcljava client.

## Development sequence

The v0.2 development base is **`integration/v0.2`**. Follow the ordered
[roadmap and acceptance gates](docs/ROADMAP-v0.2.md); implementation branches
start from the latest integration and target it in their PRs, not `main`.
Read [AGENTS.md](AGENTS.md) before agent-driven work. The final integration PR
stays draft until qualification and operator acceptance on the physical tablet.

## Connect

1. Launch **ROS Pocket DEV** (`com.lamggm.rospocket.dev`, version 0.1.0).
2. Connect to the same Wi-Fi LAN as the ROS 2 participants.
3. Choose their **Domain ID** (portable range 0–101 in this version).
4. Leave **peer IPv4 empty** for automatic discovery, then connect.
5. Tap a supported topic to inspect its latest value, count and received Hz.

Multicast filtering, AP/client isolation and host firewalls can prevent discovery.
A matching Domain ID alone does not guarantee connectivity. Some Android VPNs
refuse physical Wi-Fi binding; if needed, **the device owner** may explicitly
exclude this app from the VPN. The app never changes VPN settings itself. An
excluded app cannot use the VPN/tailnet connection.

## Features and limits

Connection states, recovery, and the additive session/JNI contract for downstream
workstreams are documented in [connection diagnostics](docs/connection-diagnostics.md).

- Real DDS endpoint discovery, publisher/subscriber counts excluding this
  inspector, and ROS node names from Jazzy `ros_discovery_info`.
- [Topic Explorer / QoS](docs/EXPLORER-QOS.md): local search, namespace/type
  filters and favorites; publisher/subscriber details, real graph-GID associations
  (unknown explicitly), announced endpoint QoS and conservative incompatibility
  reasons. Filtering/favorites do not restart DDS or subscribe.
- `/rosout` console with typed Jazzy decoding, an independent bounded reader,
  local filters, pause and clear: [scope and validation](docs/ROSOUT-CONSOLE.md).
- Decoders: `std_msgs/msg/{String,Bool,Int32,Int64,Float32,Float64}`,
  `geometry_msgs/msg/{Vector3,Quaternion,Twist,Pose}` and `nav_msgs/msg/Odometry`.
  [Structured inspection / schema v1](docs/STRUCTURED-MESSAGES.md) exposes a bounded
  native field tree, selectable numeric paths, source Header stamps, UTF-8 frames
  and both fixed covariance arrays. NaN/Inf are explicit, never invented zeros.
  Custom/other types (including standalone Point, Header and covariance wrappers)
  are listed but **not decoded**.
- One active **selected telemetry** reader: latest value, count, received Hz,
  compatible publisher count and monotonic message age. The dedicated `/rosout`
  reader is independently bounded. Telemetry older than three seconds is stale.
- Default QoS: **best effort / volatile**. A reliable reader cannot match a
  best-effort writer; a transient-local reader cannot match a volatile writer.
- Foreground-only: Stop releases DDS and multicast resources. Returning reconnects;
  a process restart retains domain/peer/QoS, but requires selecting the topic again.
- IPv4, same-Wi-Fi-LAN operation. `DontRoute=true` filters distant VPN/Docker DDS
  locators. Routed subnets, IPv6-only networks and SROS2/DDS Security are not
  supported or validated by this prototype.
- Node graph uses Jazzy's 16-byte GID schema. Older ROS graph formats can differ;
  topic discovery is independent of graph decoding.
- No parameter editing, services/actions, plots, images, LaserScan, maps, TF or RViz.
- Native intake runs about twice per second; visible updates are limited to about
  once per 1.5 seconds. Received Hz measures intake, not guaranteed source rate.
- Portuguese-only UI; lint currently has nonfatal localization, launcher-icon and
  data-extraction-rule warnings.
- No high-rate/endurance, physical Wi-Fi-toggle/IP-change, older-device or
  16-KiB-page physical-device acceptance has been completed.

## Build on Linux (x86_64)

Prerequisites: **JDK 17**, Android SDK platform 35/build-tools 35.0.0,
C/C++ compiler, CMake ≥3.16, Ninja, Python ≥3.11, Git, curl and unzip. Docker
is needed only for the isolated Jazzy fixtures. Install the SDK and accept its
licenses separately; e.g. with Android's command-line tools:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
sdkmanager --licenses
```

No account, credentials, bare Gradle installation, private dependency cache or
release-signing key is required. The versioned **official Gradle 8.7 wrapper**
checks the distribution against its official SHA-256. Wrapper JAR and upstream
checksum references are recorded in [dependency provenance](docs/DEPENDENCIES.md).

### Clean online bootstrap/build

From the repository root, with network access to Google's Android/Maven
repositories, Maven Central, Gradle distributions and GitHub:

```bash
# Optional: new empty caches prove independence from existing private caches.
export POCKET_TOOLCHAINS="$PWD/build/toolchains"
export GRADLE_USER_HOME="$PWD/build/gradle-user-home"
bash tools/bootstrap.sh --online
bash tools/build.sh --online
bash tools/test.sh --fixture  # Docker: real Jazzy Fast DDS + Cyclone DDS
```

Both scripts default to `--online`. Bootstrap verifies the official NDK r28c
archive (size and upstream SHA-1), checks the pinned Cyclone DDS source commit,
and builds host IDLC/ddsc and Android ddsc. Build generates typed descriptors,
runs portable C++/Java/Python tests and the real loopback DDS session test,
builds native Android libraries, then runs wrapper tasks
`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. The scripts accept no
arbitrary Gradle executable override. Use separate build directories/caches when
switching toolchains; CMake caches contain absolute compiler/library paths.

### Warmed offline mode

```bash
# Keep the SAME POCKET_TOOLCHAINS and GRADLE_USER_HOME used online.
bash tools/bootstrap.sh --offline
bash tools/build.sh --offline
bash tools/test.sh --portable
```

Offline is **not** first-time provisioning: it requires the previously verified
NDK, pinned Cyclone Git checkout and all native build prerequisites, installed
JDK/SDK/licenses, the official wrapper distribution extracted in that Gradle
user home, and every Gradle/Maven dependency (including lint and JUnit) resolved
by a successful online build. Missing native downloads/distribution fail early;
missing Maven artifacts fail in Gradle with `--offline`, never fall back online.
The official wrapper itself does not honor `--offline` for distribution downloads,
so the build script checks its exact distribution-cache location before running it.
The fixture mode is separate and may require image/package downloads; it is **not**
part of the offline promise. `POCKET_TOOLCHAINS`, `GRADLE_USER_HOME`, `TMPDIR` and
`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) are configurable; caches default outside Git.

### Outputs and source provenance

- `app/build/outputs/apk/debug/app-debug.apk`: arm64-v8a, debug-signed.
- `build/artifacts/app-debug.json`: actual APK package/versionCode/versionName,
  native ABI list, byte size, SHA-256, verified debug certificate DN/SHA-256, and
  Git HEAD plus `dirty` and content snapshot SHA-256.

Metadata is extracted with SDK `aapt2`/`apksigner` and ZIP inspection, **not** copied
from Gradle defaults. A before/after source snapshot rejects edits during a build.
Dirty local builds identify their base HEAD **and** `dirty: true`; they do not claim
to be exact artifacts of that clean commit. The snapshot covers sorted tracked
and nonignored untracked paths/content (including deletions), not user caches.
Debug certificates can differ between machines/CI runs and from the tablet;
these APKs do not authorize uninstalling/replacing a differently signed install.
This is reproducible provisioning/build, not a claim of byte-identical signed APKs.
No production signing, release upload or device installation is performed.

### Public CI

[`.github/workflows/verify.yml`](.github/workflows/verify.yml) runs on standard,
ephemeral GitHub-hosted `ubuntu-24.04` runners, with `contents: read`, no persisted
checkout credentials, no secrets/release keys or personal/self-hosted runners.
PRs use `pull_request` (including forks), never `pull_request_target`. Actions
are pinned to upstream-verified commits, the secret scanner to an image digest.
A bounded job performs full-history redacted secret scanning, wrapper integrity,
fresh-cache bootstrap, portable/unit tests, lint, debug assembly, both isolated
Jazzy RMW regressions and a warmed offline rerun. Only the debug APK and its JSON
are uploaded, for **7 days**; no device dumps, fixture snapshots, private logs,
keystores or toolchains. CI success must be observed for the published SHA; local
validation is not evidence that a remote workflow already ran.

Upstream native LICENSE/NOTICE are included in APK assets. Generated binaries,
signing keys, local environment files and test evidence remain ignored.

## Verification

The prototype passed **16 real-tablet checks**: live String samples with an
advancing sequence, actual ROS node graph, Home return with a new sample, producer
loss/stale display/removal, replacement by Cyclone DDS, reliable-versus-best-effort
QoS rejection/recovery, wrong/correct domain isolation, Float64 samples, process
restart, retained connection settings and resumed topic reading. Both Jazzy
Fast DDS and Cyclone DDS were exercised. The isolated fixture also validates
real Jazzy `/rosout` messages in separate ROS publisher and Cyclone reader processes.

Additional native regression: after a real live sample, an invalid DDS topic name
forces actual topic-creation failure; repeated polls must show inactive telemetry
and a persistent error. Successful resubscription and session reset clear it.
Reader-creation failure was not separately induced. C++ domain/endpoint/name/JSON
and Java input/network-selection tests accompany the source.

### Automated isolated host fixtures (no tablet)

`bash tools/test.sh --portable` runs the registered `core` and `session` CTests
(`portable` label, real session on 127.0.0.1/domain 67), Java rules and Python
script/metadata contracts. It does not need Docker or a running ROS publisher.
`bash tools/test.sh --fixture` builds [tests/fixture.Dockerfile](tests/fixture.Dockerfile)
from a digest-pinned public Jazzy ROS base plus exact package versions (including
transitive additions). It runs `rosout_jazzy_interop` first with a fresh
producer, then the remaining fixtures, once per Fast DDS/Cyclone DDS RMW. The
producer waits for a DDS subscriber instead of relying on a startup sleep. The
real host-built test binary and ddsc library are
mounted read-only and executed with the publisher inside the **same disposable
`--network none` container**, on its private loopback/domain 67.
The same fixture gate also exercises all supported structured/primitive types,
Unicode and truncation boundaries, NaN/Inf, numeric paths and reader recovery.
The fixture image tag hashes its Dockerfile, publisher and loopback configuration
together so parallel feature worktrees cannot reuse a stale publisher image. No host/LAN/robot
network or device is accessible. Containers have unique names, resource limits,
120-second publisher lifetimes and cleanup traps; the existing tablet fixture
name is never reused or stopped. JUnit XML stays under `build/native-host/`.

The recipe is intentionally Linux x86_64 (host ABI compatible with Ubuntu 24.04).
Its base digest and all additional APT versions are pinned; signed upstream APT
metadata validates the packages. If upstream removes a pinned version, provisioning
fails instead of silently upgrading: review/update pins and rerun both RMWs. This
is a reproducible recipe, not a promise of permanent upstream package retention or
byte-identical image layers. Keep a built image locally for disconnected benches.
Only synthetic fixture logs are involved; they are not uploaded as CI artifacts.

### Reproduce on your own isolated tablet bench

Never run these destructive fixture lifecycle tests against a robot domain.
The synthetic fixture uses domains **67/68**, node `/pocket_fixture`, and topics
`/pocket_test/chatter` (String) and `/pocket_test/value` (Float64). It exits after
30 minutes. It is a benchmark publisher, not a bridge and not a runtime dependency.

Configure these values locally; no device addresses or personal image names are
embedded in the scripts:

- `POCKET_TABLET`: your authorized `adb devices` serial or wireless endpoint.
- `POCKET_EXPECT_WIFI_IPV4`: your tablet's actual Wi-Fi IPv4 address, for acceptance.
- `POCKET_ROS_IMAGE`: your local ROS Jazzy test image containing rclpy/std_msgs and
  both `rmw_fastrtps_cpp` and `rmw_cyclonedds_cpp`.

Start an owned `ros2-pocket-fixture` container in domain 67 using that image,
host networking, the Fast DDS RMW and `tests/fixture.py` mounted read-only as
`/pocket-fixture.py`. Source `/opt/ros/jazzy/setup.bash`, then run
`timeout 1800 python3 /pocket-fixture.py`. The tablet regression verifies the
mounted script/domain before stopping and replacing that **specific** container.
Select `/pocket_test/chatter` in the app before running `tests/tablet_regression.py`.
`tests/tablet_accept.py` performs the initial discovery/LAN-address assertion.
UI automation is restricted to this development app, not VPN/settings apps.

For the native failure regression, configure the native host CMake build with
`-DPOCKET_TEST_ADDRESS=<host-LAN-IPv4>` and, with the fixture running, execute:

```bash
ctest --test-dir build/native-host -R '^subscribe_failure$' --output-on-failure
```

Fixture-dependent CTest and physical-tablet tests are separate from the offline
build. Local test output stays in ignored `build/` directories. Raw logs, endpoint
identities and private device evidence are intentionally not published.

## Dependencies

Pins and upstream license provenance: [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).
