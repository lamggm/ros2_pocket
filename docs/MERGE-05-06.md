# Neutral reconciliation: increment 06 × integration increment 05

Parents: `2c974cc6e0034fe3e3cb88c68b982acb4aaba051` (06 + reconciled 04) and
`cee351a8fe282db7d1398789bba1d3eed1218a6a` (integrated 05); merge base
`bc7230178cbfeda5010bb524fe83183b4a1cd330`.

Intent 06/04: exactly four independently bounded telemetry readers, independent
QoS/errors/age/count/Hz/matches/generations, slot-zero compatibility, ordered
Activity/JNI lifecycle, plus a separate dedicated bounded rosout reader and
pre-poll applied-clear fence. Intent 05: actual typed IDL and schema-v1 structured
inspection for five geometry/odometry types and six primitives, correct stable
numeric paths, exact integers/nonfinite states, bounded UTF-8/tree projection and
conservative unsupported/error lifecycle. Neither parent is favored.

## Every original conflict hunk

Hunk numbers are original marker order within each file; stable anchors avoid
line-number drift. Essential companions outside markers are listed separately.

| File / hunk / anchor | Class | Decision / rationale |
|---|---|---|
| README H1 / decoder and reader scope | disjoint-intent + superseded | Keep 05's eleven decoders/schema/wire ceiling and 06's four independent watches/dedicated rosout. 05's one-selected-reader limitation is superseded, not its structured contract. |
| CMakeLists H1 / host suites | disjoint-intent | Keep every 06 multiwatch target and 05 structured_core target, labels/deadlines, plus all cleanly merged typed/security fixtures. No duplicate targets/gates. |
| inspector.cpp H1 / discovery_error and old single-reader fields | same-question-different-answer | Ownership question: choose 06's Watch (including inherited primary), not a second set of legacy telemetry fields in Impl. Move 05 message storage into Watch so every slot owns a tree. Keep discovery error on the shared session. |
| inspector.cpp H2 / stop_reader | superseded + disjoint-intent | 05's single-reader method is superseded by Watch::stop_reader; retain its message=null clearing in the per-watch method, covering accepted replacement, removal and creation failure. |
| inspector.cpp H3 / String clipping | same-question-different-answer | Choose 05 bounded_utf8 helper instead of duplicate inline 06 clipping. Same 8192-byte limit and codepoint-boundary behavior, now shared with typed leaves. |
| inspector.cpp H4 / MaxSampleSize explanation | disjoint-intent | Keep 05's receive-side explanation and the one existing 64-KiB setting. Never duplicate the same domain ceiling or bound standard ROS IDL strings. |
| inspector.cpp H5 / poll JSON | disjoint-intent | Keep ordered four watches, root slot-zero projection including message, and dedicated rosout before one final JSON close. No duplicate root fields. |
| MainActivity H1 / watch and decoder UI | disjoint-intent | Keep four-slot selectors/QoS/remove/status controls and 05 decoder list/structured inspector. Extend the inspector to four per-slot Views, not a misleading primary tree under the selected extra watch. |
| MainActivity H2 / disconnect | disjoint-intent | Clear primary structured state AND all extra requests/status/trees, retaining console clear and ordered teardown. |
| test_tools H1 / first fixture config stub | same-question-different-answer | Keep existing 06 literal 'test discovery'; it describes the identical third hashed build input. Adjust 05's digest oracle to the retained runner algorithm, preserving its all-three-input identity assertion. |
| test_tools H2 / behavioral gate fixture config stub | same-question-different-answer | Same literal choice; no assertion/test path removed. Real inner shell/CTest failure gates survive. |
| tools/test.sh H1 / fixture image identity | same-question-different-answer | Choose existing 06 sha256sum-of-three-input-digests algorithm, not 05's concatenated-bytes variant. Both cover all inputs; retaining the common runner avoids churn. The regression verifies its exact identity and all Dockerfile COPY inputs. |

## Authorized semantic companions

- `Watch.message` is initialized/cleared independently and populated by the shared
  typed intake for **every** slot, then emitted per watch. Root message is the
  inherited slot-zero message. Descriptor support is never restricted to primary.
- `MainActivity` keeps four `StructuredMessageView` instances. Primary remains
  tagged `structuredMessage` and explicitly labeled Watch 1/generation. Extras
  are `structuredMessage:1..3`, each following its own status/generation label.
  Primary Views precede extras in the hierarchy so existing primary accessibility
  lookup semantics remain unchanged. Each tree is cleared immediately on its
  request/QoS/removal, error/pending/offline and lifecycle boundary; another watch
  is untouched. Freshness is recomputed at paint time, including explicit stale
  text, not just color. Numeric selection stays local; replacement resets it.
- A new session with no primary request records that empty reader as applied,
  avoiding a false pending subscription while preserving the removal fence.
- Requested/applied extra Frames and primary pending state are captured **before**
  poll. Existing Activity/config/network fences and dedicated rosout applied
  clear epoch remain authoritative. One process-wide executor and JNI mutex are
  unchanged; no new worker/reader manager or production fault-injection API.
- CMake Python test invocations use the stable `/usr/bin/python3` available in
  host/container rather than leaking a host-version-specific interpreter path.
  Normalize mounted test paths, mount tests once read-only, preserve first-burst
  rosout-first on each fresh RMW fixture and separate reports. Every required
  selection retains `--no-tests=error` and 16-KiB passing-output retention.
- `tests/fixture.Dockerfile` lifetime changes from 120 to **420 seconds**, still
  bounded. The first combined run really reached the inherited deadline during
  primitive_int32 (exit 137); this was not a decoder failure. A new runner
  regression first failed on 120, then passed on 420. Per-test CTest deadlines,
  container network-none/nonroot/read-only/resource limits remain unchanged.
- Added `structured_multiwatch.py` uses actual rclpy publishers and a **separate
  native production Session process** in the network-none domain. It checks four
  independent trees, source stamp/UTF-8, entire fixed covariance arrays, exact
  Int64/nonfinite JSON null, all **44 slot/type combinations**, advancing healthy
  counters and unchanged generations through failure/removal/reassignment/QoS,
  same-topic differing QoS, conservative unsupported rejection, dedicated new log
  sequences and empty clear while all four structured streams advance.
- Added `StructuredMultiWatchActivityTest`: eight deterministic new host-View
  regressions, plus inherited baseline gates, for independent local selection,
  per-slot replacement/removal, primary removal, QoS during blocked poll, pending
  frame before policy capture, delayed stale trees, independent error clearing,
  queued lifecycle/offline/disconnect fencing, and an empty primary session that
  must stay inactive rather than inventing a pending reader. JNI is an explicit double.
- IDL clean merge keeps one builtin Time shared by Header/Log, true nested typed
  layouts and fixed covariance[36], unbounded standard strings. 04 dedicated
  rosout and all original 05 structured/security and 06 coexistence tests remain.

## Bounds preserved

Exactly four telemetry slots, 64 cached samples per reader, one take of at most
64 per slot/256 total per poll; latest text <=8192 UTF-8 bytes per slot. Typed
string leaves <=512 UTF-8 bytes, closed schemas <192 nodes, UI traversal <=192
nodes/depth 8/512 code points per label; stable numericPaths and exact Int64 tokens.
One domain-wide 64-KiB serialized CDR ceiling applies before DDS deserialization,
including discovery/graph/rosout. Dedicated rosout keeps its independent
128-sample intake, 200-entry/256-KiB retained ring. These are not total-process
memory, aggregate-traffic or authentication guarantees. App intake/paint cadence
remains 500/approximately 1500 ms. No history, plotting, commands or bridge added.

## Verified local evidence (pre-commit integrated source)

Ignored `build/reconcile05-evidence/` retains every failed/setup/RED invocation,
not mixed with final result files:

- `03-native-red.log`: actual combined driver fails because watch.message absent;
  `06-native-rmw_*.log`: focused GREEN under both RMWs.
- `04-jvm-red.log` and `jvm-red-xml/`: missing extra structured View behavioral RED.
  `07-jvm-focused.log` / `jvm-focused-failed-xml/`: two parent accessibility lookup
  failures identified and fixed by primary-first hierarchy (not weakened tests).
- `08-full-build.log`: offline native/APK build, 20 Python + 12 portable CTest,
  domain/policy Java contracts, **111 JVM tests**, lint, then forced-fresh JVM/lint.
- `09-full-fixture.log`: retained 120-second lifetime failure. `10-lifetime-red.log`
  and `11-lifetime-green.log`: deadline regression. `12-full-fixture.log`: **22
  fixture tests per RMW (1 rosout-first + 21)** pass, including unchanged 05
  wire/security and all original 04/06 tests; durations about 172 seconds per RMW.
  The added deadline regression makes the final Python total **21**. The later
  empty-primary regression (`17-inactive-red.log`) makes the final JVM total **112**.
- `precommit-gates.json`: parsed suite totals/real CTest measurements, not inferred
  test counts. Reports are `build/native-host/rmw_{fastrtps,cyclonedds}_cpp*.xml`.

20-second native-child measurements (5/10/20/40 Hz source; budgets <25% one core,
RSS <96 MiB and span <8 MiB were fixed before execution):

| Workload / RMW | CPU % one core | RSS max bytes | RSS span | received Hz |
|---|---:|---:|---:|---|
| four primitives + log / Fast DDS | 0.3491 | 7,757,824 | 0 | 5.036 / 10.023 / 20.046 / 40.042 |
| four primitives + log / Cyclone | 0.3491 | 8,019,968 | 0 | 4.986 / 9.973 / 19.996 / 39.991 |
| four structured + log / Fast DDS | 2.9379 | 7,901,184 | 0 | 4.979 / 10.009 / 20.017 / 39.985 |
| four structured + log / Cyclone | 2.8397 | 8,445,952 | 0 | 4.982 / 10.014 / 20.027 / 40.004 |

The final clean-commit gates/reports/build live in `build/reconcile05-evidence/`
with `final-` names. Artifact-derived `build/artifacts/app-debug.json` is the
source of truth for exact merge SHA, dirty=false, source-content snapshot, APK
identity/signature/hash. Android/native/generated outputs are removed before
that build; source snapshots must match before/after. No pre-commit APK is the
final candidate. Exact final measurements/hashes are in the untracked final
verification report, never a guessed self-referential commit hash in this file.

## Limitations and remaining boundaries

This is local merge verification, **not official CI or integration approval**.
Parent owns independent review, push/PR publication, official CI and separately
authorized integration merge. No push, PR/remote merge, main change, tablet/ADB,
installation, robot/LAN publisher, firewall/VPN/access change is performed here.
No physical native loading/rendering, RF loss/IP change, battery/endurance,
physical 16-KiB-device execution, source-frequency guarantee or plot acceptance
is claimed. Host CPU/RSS/20s measurements are not Android qualification.
