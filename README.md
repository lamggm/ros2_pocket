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

The operator authorized **11–15 in parallel**, in isolated worktrees, with parent
reconciliation afterward and physical tests only when the tablet is available.
This tree prepares identity/docs/CI; it does **not** freeze a final candidate.
See the [integration and qualification checklist](docs/FINALIZATION-v0.2.md).

## Connect

1. Launch **ROS Pocket DEV** (`com.lamggm.rospocket.dev`, version 0.2.0,
   versionCode 2 in this preparation). The header uses the actual build version;
   this version label is not final candidate identity or physical qualification.
2. Connect to the same Wi-Fi LAN as the ROS 2 participants.
3. Choose their **Domain ID** (portable range 0–101 in this version).
4. Leave **peer IPv4 empty** for automatic discovery, then connect.
5. Choose **Watch 1–4**, then **Ler** on a supported Explorer topic. QoS edits
   and **Remover watch selecionado** affect only that selected slot.

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
  [Structured inspection / schema v1](docs/STRUCTURED-MESSAGES.md) exposes bounded
  per-watch field trees, numeric paths, source stamps, UTF-8 frames and fixed
  covariance arrays. NaN/Inf are explicit, never invented zeros. Other/custom
  types remain listed but unsupported.
- [Reader-creation failure tests and hook isolation](docs/READER-FAILURE-TESTS.md):
  native actual-argument regression scope and explicit instrumentation/device blockers.
- [Up to four independent watches](docs/MULTIWATCH.md): value/tree, count,
  received Hz, matches, QoS, persistent errors and monotonic age per slot.
  Replace/remove one without restarting the other readers or discovery.
- The dedicated bounded `/rosout` reader is independent of all four watches.
- DDS wire admission is capped at **64 KiB serialized CDR per sample**, before
  deserialization, including graph/log traffic; not a total-process memory bound.
- Default QoS: **best effort / volatile**. A reliable reader cannot match a
  best-effort writer; a transient-local reader cannot match a volatile writer.
- Foreground-only: Stop releases DDS and multicast resources. Returning reconnects;
  a process restart retains domain/peer/QoS, but requires selecting the topic again.
- IPv4, same-Wi-Fi-LAN operation. `DontRoute=true` filters distant VPN/Docker DDS
  locators. Routed subnets, IPv6-only networks and SROS2/DDS Security are not
  supported or validated by this prototype.
- Node graph uses Jazzy's 16-byte GID schema. Older ROS graph formats can differ;
  topic discovery is independent of graph decoding.
- [Numeric snapshot plots](docs/LIVE-PLOTS.md): select typed numeric paths from
  any of the four independent watches, including all 87 Odometry numeric leaves;
  bounded 30-second/600-point histories and up to four overlaid series. Plot
  checkboxes are separate from the structured inspector's field selection.
  Pause freezes the display only; per-reader replacement/removal/error retires
  only that reader's traces. No display-text parsing or source-rate reconstruction.
  Real isolated DDS integration is tested; physical plot acceptance is **pending**.
- No parameter editing, services/actions, images, LaserScan, maps, TF or RViz.
- Native intake runs about twice per second; visible updates are limited to about
  once per 1.5 seconds. Received Hz measures intake, not guaranteed source rate.
  Each watch cache/intake is capped at 64 samples (256 total per poll); latest
  text is capped at 8192 UTF-8 bytes. DDS samples above 64 KiB are dropped.
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

Os gates 11/13/14 estão registrados em
[`ci/finalization-gates.json`](ci/finalization-gates.json), com seleções reais,
não vazias, e mínimos explícitos. O entrypoint comum `tools/test.sh --portable`
executa `python3 tools/verify_finalization_ci.py`, rejeitando registros pendentes,
seleções ausentes/vazias, skips, falhas e contagens abaixo do mínimo.

`tools/test.sh --fixture` mantém os gates DDS anteriores e executa também
`tools/host_campaigns.py`: multiwatch real nos dois RMWs, controles de mutação,
fases DDS reais e replay pelo modelo/Canvas nativo host. Os adapters com hooks
ficam em um build host separado; não habilitam capacidade no APK ordinary.
O replay força a suíte Java completa e arquiva XML próprio, rejeitando o caso
opt-in vazio/skipped. `POCKET_TOOLCHAINS` é explícito e não exige caminhos do
host pessoal no CI. Os dois entrypoints já são chamados pelo workflow existente;
não é necessário ampliar credenciais nem alterar o YAML público.

Registro e implementação do pipeline não provam execução remota. Revisão do
SHA reconciliado, regressões executadas, CI remoto e qualificação física são
gates separados; nenhum registro substitui Android instrumentation, soak físico
ou aceite final do operador.

Upstream native LICENSE/NOTICE are included in APK assets. Generated binaries,
signing keys, local environment files and test evidence remain ignored.

## Verification

The [v0.2 device qualification plan](docs/DEVICE-QUALIFICATION-v0.2.md) defines
21 required evidence gates, exact source/APK binding, and a predeclared measured
soak. The offline report toolkit never touches a device and cannot replace
physical acceptance. [v0.2 release notes](docs/RELEASE-NOTES-v0.2.md) remain draft;
preparation of this matrix is not final qualification or release approval.

Debug builds provide a bounded, metadata-only [device QA dump contract](docs/DEVICE-QA-CONTRACT.md)
for all four actual worker counters while the chart remains on-screen. This is
read-only, does not poll DDS again, and is disabled in release builds. A valid
dump is not qualification approval; unavailable test-hook state still blocks soak.

The **v0.1 prototype** passed **16 real-tablet checks** (not v0.2 acceptance): live String samples with an
advancing sequence, actual ROS node graph, Home return with a new sample, producer
loss/stale display/removal, replacement by Cyclone DDS, reliable-versus-best-effort
QoS rejection/recovery, wrong/correct domain isolation, Float64 samples, process
restart, retained connection settings and resumed topic reading. Both Jazzy
Fast DDS and Cyclone DDS were exercised. The isolated fixture also validates
real Jazzy `/rosout` messages in separate ROS publisher and Cyclone reader processes.

Additional native regression: actual topic- and reader-creation argument failures
have isolated host tests with structurally verified inactive telemetry, repeated
polls, identity-bound raw DDS receipts, and new advancing fixture data after both
recoveries. See [native failure scope](docs/READER-FAILURE-TESTS.md): Android
instrumentation is built and Linux JNI extra-watch-plus-rosout coexistence is
exercised, but Android execution/physical acceptance remain UNVERIFIED. See the
[explicit diagnostic policy](docs/ANDROID-DIAGNOSTIC-POLICY.md). C++
domain/endpoint/name/JSON and Java input/network-selection tests accompany the source.

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
bounded publisher lifetimes and cleanup traps; the existing tablet fixture
name is never reused or stopped. JUnit XML stays under `build/native-host/`.

The recipe is intentionally Linux x86_64 (host ABI compatible with Ubuntu 24.04).
Its base digest and all additional APT versions are pinned; signed upstream APT
metadata validates the packages. If upstream removes a pinned version, provisioning
fails instead of silently upgrading: review/update pins and rerun both RMWs. This
is a reproducible recipe, not a promise of permanent upstream package retention or
byte-identical image layers. Keep a built image locally for disconnected benches.
Only synthetic fixture logs are involved; they are not uploaded as CI artifacts.

### Reproduce on your own isolated tablet bench

Never run synthetic publisher lifecycle tests against a robot domain. The
[scoped tablet harness](docs/TABLET-HARNESS.md) documents explicit transport/image
configuration, user/package/focus guards, private dump handling, bounded navigation,
source freshness and the distinction between synthetic and physical evidence.

`tests/tablet_regression.py` starts its **own unique labeled fixture** on the
reserved domains 67/68, with run-specific String/Float64 topic and node remaps.
Do not precreate a fixed-name container or preselect a topic for this command.
It uses an explicit existing local image without pulling, verifies ownership,
stops by immutable container ID, and independently disconnects the DEV app.
It never stops an unrelated container merely because its name/domain resembles
an old fixture. No VPN, firewall or Wi-Fi policy is changed.

`tests/tablet_accept.py` is a separate **LAN-address/discovery-only** check. It
requires `POCKET_TABLET`, the exact `POCKET_EXPECT_WIFI_IPV4`, the app already
connected and a separately controlled generic `/pocket_test/chatter` publisher.
It does not create that fixture or establish v0.2 acceptance. The regression
additionally requires `POCKET_ROS_IMAGE`; `POCKET_FIXTURE_INTERFACE` optionally
pins only its owned Cyclone publisher to the independently verified LAN interface.
`POCKET_BENCH_PEER` optionally configures the existing app IPv4 peer field when
multicast discovery is unavailable. Default is empty/automatic; the private report
records the mode, and explicit-peer success is not automatic-discovery acceptance.

For the separate native failure regression, use a separately controlled generic
`/pocket_test/chatter` publisher and configure the native host CMake build with
`-DPOCKET_TEST_ADDRESS=<host-LAN-IPv4>` and, with the fixture running, execute:

```bash
ctest --test-dir build/native-host -R '^subscribe_failure$' --output-on-failure
```

Fixture-dependent CTest and physical-tablet tests are separate from the offline
build. Local test output stays in ignored `build/` directories. Raw logs, endpoint
identities and private device evidence are intentionally not published.

## Dependencies

Pins and upstream license provenance: [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).
