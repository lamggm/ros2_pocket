# Multi-topic watch — increment 06

## User contract

Choose **Watch 1–4**, then use **Ler** in the Explorer to assign/replace that
slot. The QoS checkboxes edit only the selected slot. **Remover watch selecionado**
stops only that slot; the other readers, participant, discovery and counters stay
alive. Watch 1 uses the original detailed reading panel; Watches 2–4 each have
an independent status/value/count/intake Hz/monotonic age/matched-writer/QoS panel.
A topic may be assigned to several slots with different requested QoS.

The four fixed slots are the UI capacity boundary; `Session::watch` and
`Session::remove_watch` independently reject slots outside 0..3 (including
negative values). No UI action creates a fifth reader. A DDS topic/reader
creation failure empties only the attempted slot and preserves its error across
polls, while the connection and other slots remain healthy. Reassign or remove
that slot to clear its error. Prevalidation rejection preserves the prior reader,
but marks it erroneous/not fresh, preserving the earlier legacy contract.

Home/Stop tears down **all** watch readers, the participant, network binding and
multicast lock. Returning reconnects the remembered in-memory selections using
their own QoS, with fresh counters/values; it never reuses cached telemetry.
Explicit Disconnect clears all selections. Process restart retains connection
settings and primary QoS, not watch selections or extra-slot QoS. These choices
are intentionally not a persistent dashboard feature.

## Native/JNI contract for integration

- `Session::watch(int slot, wireTopic, wireType, reliable, transient)` replaces one
  reader. `remove_watch(int slot)` is idempotent. Both return `{ok,error?}` JSON.
- Existing `subscribe(...)` remains an alias for slot zero; existing top-level
  `subscription`, `last`, `count`, `hz`, `age`, `matched`, `qos`, `error`,
  `readerActive`, `subscriptionState`, `fresh`, `readerGeneration` remain the
  primary projection. Consumers should parse JSON, not search the whole document
  for a count that can now belong to a different slot.
- Every `poll()` includes a four-element `watches` array, ordered by stable `slot`.
  Each entry contains `slot`, `generation`, `subscription`, `type`, `readerActive`,
  `subscriptionState`, `fresh`, `last`, `count`, `hz`, `age`, `matched`, `qos`,
  `error`. Age is seconds from a steady clock, or -1 without a sample. States use
  the existing connection-diagnostics vocabulary. Hz is received intake.
- `generation` advances for an accepted replacement/removal, including actual
  creation failure. Interpret it together with top-level `sessionGeneration`;
  slot generations reset with a new session. A failure in one watch does not
  change another watch's generation. Connection state reflects the participant /
  discovery, not one failed watch. Legacy top-level error still reports slot zero.
- JNI exposes `watch` / `removeWatch` under the existing process-global mutex;
  **all** Android native operations run on the existing single process-wide DDS
  executor, including teardown across Activity instances. No second executor or
  singleton manager is introduced. Session C++ itself is executor-confined.
- Extra-slot immutable request identities distinguish desired from actually
  applied readers. A frame captures its identity/pending state before native
  poll and checks that identity again on paint. Activity/network/config epochs
  additionally reject obsolete callbacks. Primary removal has the same pending
  fence as primary replacement. Delayed snapshots accrue paint-time age.

### Parallel dependencies, not claimed complete

Increment **04 `/rosout` is not integrated** here. There is no competing log
manager or Log reader. Its future dedicated reader should live in the same
Session/participant and executor, with its own explicit cache/intake budget;
it must neither consume/reassign a watch slot nor silently increase this four
watch API boundary. Rosout + four-watch coexistence requires post-integration
DDS and lifecycle acceptance.

Increment **05 structured messages is not integrated**. The existing six
primitive descriptors are unchanged; discovery registry, graph and endpoint QoS
contracts remain intact. Extend the shared `descriptor()` dispatch and the
per-watch `Watch::values()` decoder when reconciling 05, preserving its field /
header contract. Structured multiwatch and plot acceptance are still integration
dependencies; this branch does not claim support for those types.

## Resource acceptance budgets (defined before implementation)

The fixture source records the initial budgets before the implementation:

| Resource | Limit / acceptance |
|---|---|
| Watch readers | 4, enforced in C++ and fixed-slot UI |
| Reader cache | KEEP_LAST 64; max samples 64, one unkeyed instance |
| Native application intake | one take of at most 64 per slot per poll; 256 total |
| Retained latest text | at most 8192 UTF-8 bytes per slot, no split codepoint |
| DDS serialized sample | 64 KiB (`Internal/MaxSampleSize`), added via its own RED/GREEN |
| App cadence | 500 ms intake; ~1500 ms visible refresh |
| Native reader process (host fixture) | CPU <25% of one core; RSS <96 MiB; RSS span <8 MiB |
| 20-second four-watch measurement | intake within 80–120% of synthetic 5/10/20/40 Hz |

The sample cap also applies to discovery/graph samples in this DDS domain:
oversized samples are dropped, not silently decoded/truncated in DDS. The latest
application string is truncated on a UTF-8 boundary; the DDS cap prevents an
arbitrary unbounded wire string from entering the watch cache. This is not a
claim of a hard total-process memory ceiling: DDS discovery, sockets, allocator
and Android/UI overhead have separate costs. No historical app sample buffers,
plots or graph history are introduced.

## Validation evidence

Base: `e71bdf3` from `origin/integration/v0.2` (01/02/03), with **uncommitted**
06 changes. The final build's ignored `build/artifacts/app-debug.json` records
actual APK identity/signature/hash plus exact dirty-source content snapshot;
`build/source-before.json` must match its source. No commit, push or PR is made
for this increment.

### Real RED → GREEN cycles

Ignored logs live under `build/multiwatch-evidence/`:

1. `01-contract-red-actual.log`: snapshot missing `watches`; matching contract GREEN.
2. `02-independent-red.log`: new native watch/remove API absent (compiler RED),
   followed by real rclpy→DDS four-reader GREEN for both RMWs. Setup failures
   (unnormalized mounted path, unsourced ROS environment) are retained separately,
   not presented as behavior RED.
3. `03-connection-isolation-red.log`: actual DDS topic creation failure incorrectly
   changed a healthy connection to error; GREEN keeps other watches receiving.
4. `04-ui-red-actual.log`: accessible extra slot controls missing; GREEN on real
   Robolectric Views. Initial test compilation error is retained separately.
5. `05-ui-lifecycle-red.log`: primary change blanked another live watch, explicit
   disconnect left extra telemetry live, and queued primary removal repainted the
   old reader. GREEN fixes the independently observed assertions. A harness NPE
   after Stop was fixed separately (drain the process executor, not null worker).
6. `06-bounded-utf8-red.log`: actual long DDS String split a UTF-8 codepoint and
   broke JSON decoding. GREEN retains a valid <=8192-byte latest value.
7. `07-wire-budget-red.log`: actual >64-KiB samples entered the reader cache.
   GREEN drops them while other readers continue advancing.
8. `08-editor-red.log`: selected slot editor identity missing after refresh;
   GREEN preserves an explicit selected-slot label.
9. `09-failed-qos-red.log`: actual failed creation reported previous QoS;
   GREEN retains requested reliable/transient-local QoS with persistent error.

The container test uses real Jazzy rclpy publishers with String/Float64/Int32/Bool,
best-effort/reliable and volatile/transient-local QoS, separate from the native
reader process. It exercises mismatch/recovery, fifth-slot rejection, actual
invalid DDS topic creation after a live sample, persistent isolated errors,
independent producer pause/stale/recovery, idempotent removal/stop, unchanged
surviving generations, backlog intake, long Unicode and oversized wire samples.
The normal fixture gate runs all its tests once for Fast DDS and once for Cyclone.
The existing domain-67/69 fixture regressions remain included, not bypassed.

Measurements from the final bounded-sample GREEN run (`final-fixture.log`,
corresponding archived `build/multiwatch-evidence/measured-rmw_*.xml`), private `--network none` containers
limited to one CPU / 512 MiB, native child `/proc` only:

| Publisher RMW | Duration s | CPU % one core | Peak RSS bytes | RSS span bytes | Received Hz (5/10/20/40 requested) |
|---|---:|---:|---:|---:|---|
| Fast DDS | 20.0177 | 0.1998 | 7,925,760 | 0 | 4.9956 / 9.9911 / 20.0322 / 40.0145 |
| Cyclone DDS | 20.0188 | 0.1998 | 7,946,240 | 0 | 4.9953 / 9.9906 / 20.0312 / 40.0124 |

No inference from host numbers to tablet CPU/RSS, battery, Wi-Fi endurance,
physical 16-KiB loading, RF loss/IP change or long-duration soak is made.
Robolectric uses an explicit JNI double; it is not Android native-load evidence.
Reader-creation failure distinct from topic-creation failure is not fault-injected.

### Final gate result on Ryzen

- `tools/build.sh --offline`: passed; Python tool/provenance tests **19/19**,
  portable CTest **10/10** (including real loopback Session and Explorer),
  standalone Java input/connection-policy harnesses passed, Android/JVM tests
  **51/51**, `lintDebug` **0 errors / 66 warnings**, and `assembleDebug` passed.
  The JVM total includes the inherited lifecycle suite exercised against the
  multiwatch Activity harness; it is not 51 new independent scenarios.
- `tools/test.sh --fixture`: **4/4 per RMW, 8/8 total**, real DDS in disposable
  `--network none` containers. `final-fixture.log` and per-RMW XML preserve the
  final assertions and measurements. Synthetic publishers never use host/LAN
  networking in this gate.
- Redacted pinned Gitleaks current-source scan: **no leaks**, exit 0. The scan
  copies the exact nonignored tracked/untracked source snapshot, not just HEAD.
  This is distinct from CI's full-history scan; no claim of a new full-history
  scan is made here.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, **4,768,614 bytes**,
  `com.lamggm.rospocket.dev`, version **0.1.0 (1)**, debug certificate
  `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`,
  verified APK Signature Scheme **v2**. SHA-256:
  `bab8aeaea76240710ddcc31b272b013da03548660b6670fd12195c9d77737001`.
  Only **arm64-v8a** is packaged; both `libddsc.so` / `librospocket.so` are
  AArch64 with all ELF LOAD segments aligned to **16384**. This is artifact
  inspection, not physical 16-KiB-device execution.
- Dirty-source provenance: `build/artifacts/app-debug.json` matches
  `build/source-before.json`; detailed source-file hashes, ELF evidence, scan
  log, signature report and gate summary are under ignored
  `build/multiwatch-evidence/`. Documentation belongs to the source snapshot;
  the ignored metadata, rather than this document, is the final snapshot ID.
- No device installation/ADB mutation, release signing/publication, commit,
  push, PR or feature merge was performed. The required integration baseline
  was incorporated only by fast-forward, without a merge commit.

### Reproduction (no device or LAN mutation)

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash tools/test.sh --portable  # includes real loopback session/Explorer gates
bash tools/test.sh --fixture   # disposable network-none, both ROS RMWs
bash tools/build.sh --offline  # warmed toolchains; unit tests, lint, native APK, provenance
```

The fixture runner mounts tests read-only and sources Jazzy only for its rclpy
multiwatch test. Required CTest selection remains `--no-tests=error`. Fixture
reports are copied from writable container tmpfs before the unique container is
removed. Never run these publishers on a robot/LAN domain. No ADB/device install,
service/action/parameter call, VPN, firewall or access change is involved.
