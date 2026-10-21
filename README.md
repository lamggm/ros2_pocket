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

- Real DDS endpoint discovery, publisher/subscriber counts excluding this
  inspector, and ROS node names from Jazzy `ros_discovery_info`.
- [Topic Explorer / QoS](docs/EXPLORER-QOS.md): local search, namespace/type
  filters and favorites; publisher/subscriber details, real graph-GID associations
  (unknown explicitly), announced endpoint QoS and conservative incompatibility
  reasons. Filtering/favorites do not restart DDS or subscribe.
- Decoders: `std_msgs/msg/{String,Bool,Int32,Int64,Float32,Float64}`.
  Custom/other types are listed but **not decoded**.
- One active reader: latest value, count, received Hz, compatible publisher count
  and monotonic message age. Data older than three seconds is marked stale.
- Default QoS: **best effort / volatile**. A reliable reader cannot match a
  best-effort writer; a transient-local reader cannot match a volatile writer.
- Foreground-only: Stop releases DDS and multicast resources. Returning reconnects;
  a process restart retains domain/peer, but requires selecting the topic again.
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

Prerequisites: JDK 17, Android SDK platform 35/build-tools 35.0.0, Gradle 8.7,
C/C++ compiler, CMake, Ninja, Python 3, Git, curl and unzip. Install the Android SDK
and accept its licenses separately. No credentials or release-signing keys are
required to build the debug package.

Set `JAVA_HOME` if JDK 17 is not the active `javac`; set `ANDROID_HOME` (or
`ANDROID_SDK_ROOT`) to your SDK. `POCKET_GRADLE` can name the Gradle 8.7 executable
if `gradle` is not in PATH. There is no Gradle wrapper in this prototype.

```bash
bash tools/bootstrap.sh   # verified NDK r28c download; pinned Cyclone host/Android build
bash tools/build.sh       # tests, native build, Android unit tests/lint/debug APK
```

`POCKET_TOOLCHAINS` can override the external user-cache toolchain directory.
The Gradle invocation is **offline**, so AGP 8.5.2/JUnit 4.13.2 must already be in
your Gradle cache. For a new machine, run the final Gradle tasks once without
`--offline` to resolve dependencies, then repeat `tools/build.sh`.

Output: `app/build/outputs/apk/debug/app-debug.apk`, **arm64-v8a**, debug-signed.
A debug build is not a signed/store release. Binaries, signing keys, toolchains,
local environment files, packet captures, screenshots and device dumps are not
stored in this repository. Upstream native LICENSE/NOTICE are included in APK assets.

## Verification

The prototype passed **16 real-tablet checks**: live String samples with an
advancing sequence, actual ROS node graph, Home return with a new sample, producer
loss/stale display/removal, replacement by Cyclone DDS, reliable-versus-best-effort
QoS rejection/recovery, wrong/correct domain isolation, Float64 samples, process
restart, retained connection settings and resumed topic reading. Both Jazzy
Fast DDS and Cyclone DDS were exercised.

Additional native regression: after a real live sample, an invalid DDS topic name
forces actual topic-creation failure; repeated polls must show inactive telemetry
and a persistent error. Successful resubscription and session reset clear it.
Reader-creation failure was not separately induced. C++ domain/endpoint/name/JSON
and Java input/network-selection tests accompany the source.

### Reproduce on your own isolated bench

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
