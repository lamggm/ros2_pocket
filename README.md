# ROS Pocket

A native Android, **read-only** ROS 2/DDS inspector: Android UI → JNI/C++ →
Eclipse Cyclone DDS 0.10.5. No rosbridge, auxiliary bridge/server, application
command publishers, service calls or motion commands. This is a development
prototype, not a port of desktop rqt plugins or a full rcljava client.

## Development sequence

**Current development:** [`integration/v0.3`](https://github.com/lamggm/ros2_pocket/tree/integration/v0.3).
Follow the [v0.3 roadmap](docs/ROADMAP-v0.3.md): reconcile the final v0.2 base →
bench navigation → local profiles (storage, then explicit application) →
bounded closeout. Implementation PRs target `integration/v0.3`, not `main` or
`integration/v0.2`. Read [AGENTS.md](AGENTS.md) before agent-driven work.
Navigation groups the existing controls into **Conexão**, **Tópicos**, **Monitor**
and **Logs** without creating another DDS session or worker. It was integrated in
[PR #15](https://github.com/lamggm/ros2_pocket/pull/15), merge `de3c254`, with passing
CI 40537386529 and a bounded 11-check real-tablet smoke of source `a86cf7d`.
Local profiles were integrated in [PR #16](https://github.com/lamggm/ros2_pocket/pull/16),
merge `e655da3`, with passing [CI 40569878837](https://github.com/lamggm/ros2_pocket/actions/runs/40569878837).
Their physical-tablet smoke is still **pending**. See [local profiles](#local-profiles).
This closeout prepares **0.3.0-rc1**, not a physically accepted release. The
[candidate scope and short pending smoke](docs/FINALIZATION-v0.3.md) distinguish
host/CI validation from device acceptance; no tag, release or main promotion is included.
LaserScan, user-facing export and numeric batches remain out of this cycle.
Host checks are not device acceptance; navigation evidence does not qualify profiles.

**Inherited v0.2:** [PR #1](https://github.com/lamggm/ros2_pocket/pull/1) was
merged into `main` at `c659b8521c093aacc3ccecd0855c07727398db2b`; the
[post-merge CI](https://github.com/lamggm/ros2_pocket/actions/runs/40515533393)
passed. The operator approved a **bench prototype**, not full physical/endurance
qualification. The [delivery scope and evidence](docs/FINALIZATION-v0.2.md)
retain the actual source/APK identities of host checks and the short real-tablet
plot/pause/resume smoke. Extended Wi-Fi/IP, endurance and physical qualification
remain explicitly unvalidated backlog, not automatic blockers for v0.3.
Historical thresholds and validators are unchanged; deferred tests are not PASS.
No tag, release or store publication is included.

**CI limitation:** PR verification is enabled. The push filter does not yet
include `integration/v0.3`; the operator chose not to broaden GitHub credential
permissions to publish that workflow change. See the [roadmap](docs/ROADMAP-v0.3.md)
for the explicit follow-up and integrated-tree verification requirements.

## Connect

1. Launch **ROS Pocket DEV** (`com.lamggm.rospocket.dev`, candidate version
   **0.3.0-rc1**, versionCode **3**). The header uses the actual build version;
   the candidate label does not imply physical qualification. No installation is
   performed by this preparation; future in-place updates must match the installed signer.
2. Connect to the same Wi-Fi LAN as the ROS 2 participants.
3. In **Conexão**, choose their **Domain ID** (portable range 0–101 in this version).
4. Leave **peer IPv4 empty** for automatic discovery, then connect.
5. In **Monitor**, choose **Watch 1–4**, then open **Tópicos** and tap **Ler** on
   a supported Explorer topic. Return to **Monitor** for values, structured
   messages and plots. QoS edits and **Remover watch selecionado** affect only
   that selected slot. **Logs** contains `/rosout`; **Conexão** contains diagnosis
   and the ROS node list. Switching sections does not disconnect.

Multicast filtering, AP/client isolation and host firewalls can prevent discovery.
A matching Domain ID alone does not guarantee connectivity. Some Android VPNs
refuse physical Wi-Fi binding; if needed, **the device owner** may explicitly
exclude this app from the VPN. The app never changes VPN settings itself. An
excluded app cannot use the VPN/tailnet connection.

## Local profiles

In **Conexão**, below the connection diagnostics, **Perfis locais** stores named
bench configurations: Domain ID, optional IPv4 peer, four watch slots (including
empty slots), their requested topic/type/QoS, and up to four numeric graph fields.
Only configuration is saved, never samples, live counters or DDS handles.

1. Configure the connection/watches/graph using the existing controls.
2. Choose **Novo perfil**, enter a name, then **Salvar configuração atual**.
3. Selecting an existing profile only chooses the target for operations. Saving
   explicitly replaces that profile with the current configuration; **Renomear**
   changes only its name. Deletion requires confirmation and does not alter a
   running session.
4. Disconnect explicitly, select a saved profile and tap **Aplicar**. Application
   while connected (including reconnecting) is blocked. Apply loads the requested
   configuration without creating any DDS readers.
5. Tap **Conectar** separately. Unavailable topics/type mismatches remain explicit,
   never silently replaced. Saved graph fields wait for fresh matching typed data;
   no old points are restored. A checked restored field can be unchecked normally.

Once a profile catalog exists, process restart starts disconnected even if legacy
connection preferences requested reconnection. The **last explicitly applied
configuration snapshot** is restored, not the last selected or subsequently edited
profile. Deleting that last-applied profile also clears its startup snapshot (as
stated in the confirmation); it does not change the current configuration/session.
The applied snapshot takes precedence over Android's automatically saved View
drafts during Activity restoration. Home/return within the same Activity retains the existing foreground-only
connection policy. Apply/disconnect never leaves saved samples masquerading as live.

Schema v1 limits: 16 profiles, unique case-insensitive names of 1–48 Unicode
characters / 128 UTF-8 bytes, no control/format characters, file at most 128 KiB,
four slots, four unique numeric fields from the existing standard decoders.
The private `bench-profiles-v1.json` uses Android `AtomicFile`, bounded reads,
explicit saves and a stale-writer guard. Invalid/unknown schemas preserve the
file and legacy preferences, disable profile edits and block automatic connection;
the user can still connect manually. Reopen after resolving the file problem;
there is intentionally no destructive reset button. A failed save is reported,
not converted into a successful empty catalog. Disk I/O occurs only in bounded
manual UI operations/startup, never on the DDS worker. This is not a claim of
power-loss durability or a multi-process database. No cloud, sync, account,
import/export, new permissions or native DDS/JNI changes are included.

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
- Foreground-only: Stop releases DDS and multicast resources. Returning reconnects
  within the existing Activity. Legacy process restart retains domain/peer/QoS;
  with a profile catalog it starts disconnected, restoring only the last explicitly
  applied configuration and awaiting new samples after explicit connection.
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
  Real isolated DDS integration and a short physical two-series/pause/resume smoke
  passed; full physical plot qualification remains **pending**.
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

The [extended device qualification plan](docs/DEVICE-QUALIFICATION-v0.2.md)
retains its 21 mandatory gates and measured soak for claiming full qualification;
its validators and thresholds are unchanged. That extended claim is not required
for the operator-approved [bench prototype scope](docs/FINALIZATION-v0.2.md).
The offline toolkit never touches a device or grants release authorization.
[v0.2 release notes](docs/RELEASE-NOTES-v0.2.md) disclose what actually passed and
what remains unvalidated; `main` promotion and release remain separate decisions.

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
