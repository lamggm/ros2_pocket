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
  `subscriptionState`, `fresh`, `last`, `message`, `count`, `hz`, `age`, `matched`, `qos`,
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

Increment **04 `/rosout` is reconciled locally** from integration commit
`bc7230178cbfeda5010bb524fe83183b4a1cd330`. Its dedicated reader shares the
Session/participant and serialized executor, but consumes no watch slot and has
its own 128-sample intake / 200-entry, 256-KiB retained-ring budgets. Combined
real DDS tests below prove four-watch/log coexistence; this is not physical
Android acceptance or permission to merge increment 06 before 05.

Increment **05 structured messages is reconciled locally** from
`cee351a8fe282db7d1398789bba1d3eed1218a6a`. All eleven supported descriptors use
the same per-slot intake path; each watch owns its latest schema-v1 `message`
tree independently. The legacy root `message` is exactly slot zero, not the
currently edited watch. Four bounded `StructuredMessageView` instances expose
numeric selection locally, labeled by watch/generation; replacement, QoS,
removal, errors and lifecycle clear only the affected tree (all on Stop/offline).
Selection is scoped to topic/type because every subscription request clears its
own view, while normal value refresh preserves its local numeric path.

See [MERGE-05-06](MERGE-05-06.md) for hunk decisions, combined typed/log tests,
measured budgets and remaining qualification boundaries. No plots, bridge,
commands or publication actions were introduced.

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

The original increment-06 evidence below was collected over base `e71bdf3`.
The reconciled local tree combines committed 06 `40173e8` and integration 04
`bc72301`; final clean-commit identity is derived from ignored
`build/artifacts/app-debug.json` after the merge commit and rebuild. Historical
pre-merge artifact hashes are not the current candidate. Publication and final
qualification remain the parent's separate gates.

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

### Reconciled 04 + 06 gate on Ryzen

Evidence is ignored under `build/reconcile04-evidence/`; the definitive APK,
clean source SHA/content snapshot and signature are in
`build/artifacts/app-debug.json`. No artifact hash is embedded in this source
document because documentation itself participates in the source snapshot.

- Full `tools/build.sh --offline` passed in a private `unshare -Urn` network
  namespace with only loopback up: Python tool/provenance **20/20**, portable
  CTest **11/11**, both standalone Java harnesses, native arm64 build, JVM
  **66/66**, lint **0 errors / 73 warnings**, and debug assembly/provenance.
  Explicit `--rerun-tasks` JVM/lint also passed. JVM totals include inherited
  connection scenarios repeated through the multiwatch Activity harness, not
  66 distinct new scenarios.
- `tools/test.sh --fixture`: dedicated rosout **1/1 first**, then all other
  fixtures **4/4**, for each RMW (**10/10** total). Containers are unique,
  resource-bounded and `--network none`. Image identity hashes all Docker build
  inputs: Dockerfile, fixture.py, fixture-loopback.xml; scripts and exact native
  libraries/binaries remain read-only mounted. Passed-test output retention is
  16 KiB so XML retains measured values rather than truncating the evidence.
- New coexistence fixture publishes actual Jazzy `rcl_interfaces/msg/Log` over
  domain 71 alongside String/Float64/Int32/Bool at 5/10/20/40 Hz. All four
  watches and dedicated logs receive; log sequence advances throughout sustained
  QoS mismatch, failed creation, removal, reassignment and same-topic/different-QoS
  cases. Clearing logs with the log publisher paused leaves log count zero while
  all four healthy counters advance **6/12/24/48** with unchanged generations
  (both RMWs). Resuming the log publisher produces new log sequence numbers.
  Stop clears all five readers' presentation. No host/LAN publisher is used.
- RED/GREEN: `image-hash-red.log` exposes incomplete image identity;
  `coexistence-red.log` fails the unknown helper `clear_rosout` command after
  real log/watch reception. Adding the test-only native helper command and
  full-input image key makes the complete fixture green; no app fault-injection
  or publisher API is introduced.
- Existing deterministic stale-paint regression covers all extra slots 1..3;
  Explorer compatibility remains explicitly labeled **Watch 1**. Rosout retains
  its worker-applied pre-poll clear epoch and both policy-monitor / blocked-poll
  clear-race regressions. Both features retain ordered process-wide JNI teardown.
- Pinned Gitleaks **8.30.1** current tracked-source scan passed: **74 files,
  zero findings**, redacted report retained. This is separate from CI's
  full-history scan, which is not claimed as newly executed here.

Measurements with the dedicated rosout reader active, private containers limited
to one CPU / 512 MiB, native child `/proc` only (`fixture-final.log`, per-RMW XML):

| Publisher RMW | Duration s | CPU % one core | Peak RSS bytes | RSS span bytes | Received Hz (5/10/20/40 requested) |
|---|---:|---:|---:|---:|---|
| Fast DDS | 20.0400 | 0.2495 | 8,028,160 | 0 | 4.9900 / 9.9800 / 20.0099 / 40.0199 |
| Cyclone DDS | 20.0412 | 0.2495 | 7,843,840 | 0 | 4.9897 / 9.9795 / 20.0088 / 40.0176 |

The native 64-KiB serialized sample ceiling is shared by watches, dedicated
rosout and discovery in this Session. A bounded rosout ring is not a claim of
bounded total process/JNI/UI memory or lossless DDS intake.

**Not complete:** increment 05 structured descriptors/fields/headers and its
post-integration coexistence are absent. Increment 06 must not merge into
integration before 05 is merged, reconciled and re-tested. No physical device
installation/native loading, Wi-Fi loss/IP change, tablet CPU/RSS/battery,
physical 16-KiB-device execution, plots or long-duration soak is claimed.
Robolectric uses a JNI double, not Android native-load evidence. DDS reader
creation failure distinct from topic creation is not fault-injected. No push,
PR publication, integration/main merge, ADB, VPN or firewall mutation is performed
by this reconciliation; the parent owns the existing draft PR and publication.

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
