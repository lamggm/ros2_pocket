# Neutral reconciliation: increment 06 × integration increment 04

Parents: `40173e8fab1b07f3a35089e3422f567ca89c8b9b` (06) and
`bc7230178cbfeda5010bb524fe83183b4a1cd330` (04); merge base `e71bdf3`.

Intent 06: four bounded independent telemetry slots with independent QoS, errors,
counters and monotonic freshness, slot-zero compatibility and ordered lifecycle.
Intent 04: a dedicated bounded typed rosout console, independent of telemetry,
with UTF-8, pause/filter/clear and worker-applied pre-poll epoch fencing.
Neither parent is favored. All parent assertions and feature targets survive.

## Every conflict hunk

Hunk numbers are the original conflict order within each file. Locations below
refer to stable function/contract anchors instead of shifting merge-marker lines.

| File / hunk / anchor | Class | Decision and rationale |
|---|---|---|
| README.md H1 / scope | superseded + disjoint-intent | 04's one-selected-reader wording is superseded by 06's four slots; retain the independent dedicated rosout claim alongside them. |
| inspector.hpp H1 / public methods | disjoint-intent | Keep watch/remove_watch and clear_rosout; no API replaces another. |
| inspector.cpp H1 / Watch, Session::Impl | same-question-different-answer + disjoint-intent | Ownership question: choose 06's Watch abstraction (four slots including inherited slot zero), not 04's old single-reader fields; add 04's dedicated rosout handles only to Session::Impl, outside slots. |
| inspector.cpp H2 / buffers and cleanup | superseded + disjoint-intent | Legacy single telemetry fields/stop_reader from 04 are superseded by Watch; keep discovery_error, dedicated RosoutBuffer, take_rosout and stop_rosout. No duplicate reader manager. |
| inspector.cpp H3 / Session::stop | disjoint-intent | Stop all four watch readers AND dedicated rosout before shared participant/domain deletion; retain idempotent reset. |
| inspector.cpp H4 / Session::poll intake | disjoint-intent | Ingest all four bounded watches AND independently bounded rosout after discovery. |
| inspector.cpp H5 / poll JSON | disjoint-intent | Emit ordered watches, legacy root projection and rosout before the single final JSON close; preserve both schemas. |
| jni.cpp H1 / JNI operations | disjoint-intent | Keep watch/removeWatch and clearRosout, each under the same global mutex. |
| MainActivity.java H1 / successful session start | disjoint-intent | Reset applied watch identities AND initialize workerRosoutEpoch for the new session; both lifecycle fences survive. |
| MainActivity.java H2 / queued paint | disjoint-intent | Render extra watches through immutable captured frames AND rosout only when the captured pre-poll worker epoch equals current desired/applied epochs. |
| subscribe_failure_test.cpp H1 / JSON helpers, live | disjoint-intent | Keep 04 root-depth numeric parser and empty-rosout positive control; retain 06 primary isolation and additionally exclude nested rosout from legacy text checks so either nested schema cannot mask regression. |
| subscribe_failure_test.cpp H2 / recovered error | disjoint-intent | Require live(session, loopback) (04 empty-console assertion) AND isolate primary when asserting recovered error empty (06). |
| subscribe_failure_test.cpp H3 / restart domain | same-question-different-answer + disjoint-intent | Domain question: choose 04's test_domain (71 for loopback, 67 for fixture), not 06's hardcoded 67; keep 06 primary isolation for reset assertions. This prevents accidental domain-67 reuse by the isolated loopback test. |

## Authorized semantic companions (outside markers)

- `tests/multiwatch_wire.cpp`: test-only `clear_rosout` command calls the actual
  native API. It is not a production publisher or fault-injection control.
- `tests/multiwatch_wire.py`: actual rclpy Log writer on isolated domain 71;
  sustained log advancement during mismatch/failure/removal/reassignment, four
  healthy telemetry counter deltas and unchanged generations during log clear,
  resumed new logs and complete stop assertions. Existing wire/memory/rate/QoS
  assertions remain intact.
- `tools/test.sh`: key fixture images by all three Docker input contents; retain
  rosout-first fresh fixture per RMW, both report files, mounts, labels and
  nonempty CTest gates; retain 16-KiB passed-test output so measurements survive.
- `tests/test_tools.py`: regress complete fixture image inputs and materialize
  those inputs in fake transport roots; real inner shell/CTest negative paths
  and all original runner assertions remain.
- `app/src/main/cpp/CMakeLists.txt`: normalize Python test script locations to
  the mounted tests root, preserving every native target, label and deadline.
- `docs/MULTIWATCH.md`, `docs/ROSOUT-CONSOLE.md`, this audit: replace stale
  readiness statements with actual combined gates, bounds and measurements;
  distinguish shared 64-KiB domain sample cap from retained rosout budgets.

Other auto-merged files retain both parents' additions (IDL Log/Time, model
ring, Java bridge/console and lifecycle tests, resources, rosout native targets,
fixture publisher and common build/provenance orchestration). No drive-by
production refactoring is included.

## Evidence and readiness

See [MULTIWATCH](MULTIWATCH.md) for exact measured intake/CPU/RSS and test totals.
Ignored `build/reconcile04-evidence/` retains RED/GREEN, final fixture reports,
JVM/lint rerun, source scan and final clean-source build verification. The exact
clean merge SHA, APK hash, identity and signature are artifact-derived from
`build/artifacts/app-debug.json`, never guessed from a branch name.

**Not ready for integration merge:** 05 is absent. The parent must incorporate
and reconcile 05 after its integration merge, review this merge independently,
then run affected/full gates and require official CI green. No push/PR update or
merge into integration/main is performed here. No device/ADB, robot/LAN publisher,
VPN, firewall, physical RF/IP/soak/battery or 16-KiB execution is performed.
