# Live numeric snapshot plots — increment 07

## Integrated scope (04 / 05 / 06 / 07)

The original increment-07 component was based on `e71bdf3` and supported only
primitive samples from one reader. This reconciliation incorporates integration
`d1f7a3d` (including 04 rosout, 05 structured messages and 06 independent watches)
by merge, without rewriting the published feature history.

Each of the four real native readers now exposes typed numeric paths, received
monotonic timestamps and DDS source timestamps. Supported numeric types are
`Int32`, `Int64`, `Float32`, `Float64`, `Vector3`, `Quaternion`, `Twist`, `Pose`
and `Odometry`. All **87** Odometry numeric leaves are selectable, including both
36-element covariance arrays and the two header stamp members. String/Bool
remain inspectable but never become numeric traces by parsing their text.

Choose Watch 1–4 and a supported topic in Explorer, wait for fresh samples, then
select the desired **Watch + path checkboxes in the plot panel**. Tapping a field
in the structured inspector selects it for inspection only; that is not a plot
subscription. Up to four series can overlay fields from one or several watches,
including independent readers of the same topic. Local series selection/removal
and plot pause do not create readers, change QoS, restart DDS or publish data.
The dedicated `/rosout` reader remains independent of all four watches.

The native poll drains DDS but returns only each reader's **latest** sample.
Charts record at most one latest-sample point per reader per approximately
500 ms poll, not every taken message. Count jumps are not interpolated into
invented samples. This is a snapshot plot, not a lossless recorder or source-rate
measurement. Visible refresh remains approximately 1.5 s, independent of intake.
Pause freezes a bounded display copy while the controller keeps ingesting.

## Bounded histories and presentation

- At most four selected series, each with a default 600-point / 30-second ring.
  `PlotHistory` rejects invalid budgets (maximum configurable 2048 points /
  300 seconds). Eviction uses both count and receive-time window.
- Controller: four watch identities and at most **192 numeric fields per watch**
  (768 total), with bounded watch/path/unit lengths. This covers every numeric
  leaf of the current fixed typed schemas; the old 64-field limit silently
  dropped the tail of Odometry and is no longer used. Unsupported/custom dynamic
  schemas are not admitted by this budget.
- Each native reader retains the existing 64-sample intake/cache budget; DDS
  admission remains 64 KiB serialized CDR per sample before deserialization.
  These are not a total-process memory or arbitrary-rate denial-of-service bound.
- Removing/replacing a reader, requesting new QoS, subscription errors or stale
  state retire only that reader's fields and selected traces. Healthy watches
  continue ingesting. Recovered/replaced readers need explicit series reselection.
- Global session/lifecycle reset clears retained points and paused copies. Reader
  and session epochs prevent queued old data from becoming a fresh trace.
- Removing one paused series filters only that trace; surviving traces stay frozen
  even while their live histories advance. Each refresh acknowledges only the
  revision observed at entry so a concurrent reset cannot be swallowed.
- NaN, either infinity, missing selected fields and invalid typed values are gaps
  (`null`), never zero. Lines break at gaps, receive intervals over 3 seconds and
  backwards DDS source timestamps. Receive-clock regression clears that ring.
- X is relative **receive-monotonic** seconds (zero = latest point), not wall clock,
  DDS source time or ROS header time. Y shares a raw-value auto-scale; different
  units are not converted or asserted to be dimensionally comparable.
- Units come only from an explicit declaration. Current projections use `null` /
  “unidade desconhecida”; a member named `x` does not establish metres.
- Plot math uses binary64. Int64 remains exact in native JSON transport; plotting
  may round values outside ±2^53. Float64 uses 17 significant digits, never the
  formatted echo. Canvas scaling normalizes before subtraction to keep
  ±Double.MAX_VALUE finite.

## Additive native / JNI contract

Existing entry points and snapshot fields are preserved. The root remains
**exactly the primary (slot zero) projection**, with `readerGeneration` at root
and `generation` per watch. Root and each of `watches[0..3]` expose:

```json
{
  "receivedMonotonicNs": 123456789,
  "sourceTimestampNs": 987654321,
  "numericFields": [
    {"path":"pose.pose.position.x", "unit":null, "state":"finite", "value":42.5}
  ]
}
```

This is a schema illustration, **not captured ROS evidence**.

Numeric field order/path/value follows the 05 typed field tree / `numericPaths`.
The projection reuses exact typed JSON number tokens, with raw typed finite-state
classification; it never parses `last`, field display strings or localized text.
`state` is `finite`, `nan`, `positive_infinity` or `negative_infinity`, and
nonfinite values use JSON `null` rather than invalid NaN/Infinity tokens.

`receivedMonotonicNs` is native steady-clock time when a valid sample was taken
and decoded. Re-polling an idle reader does not mint timestamps or count.
`sourceTimestampNs` is DDS `source_timestamp`, not the ROS header stamp, and is
not assumed to share a clock/epoch with receive time. Both are `null` before
receipt and after teardown; `numericFields` is empty for nonnumeric types and
inactive readers. Source reset never moves the monotonic X axis.

## Desired / applied / native identity fencing

`PlotController.Frame(watch, epoch, sequence, received, source, fields)` remains
the small component input, not a substitute for native reader management.
The Activity ingests on the ordered DDS worker, before display rate limiting.

- Capture immutable desired requests before connection-policy identity can block;
  capture applied request versions before polling. Acceptance rechecks both under
  the watch-slot monitor, using lock order policy → slots → controller.
- Native subscribe completion acknowledges the request actually passed to native,
  never a newer desired request. A request immediately retires its slot's series,
  even while an older poll or subscribe is blocked.
- Validate slot, topic and wire type against the desired/applied request. A newly
  applied same-topic request cannot admit an older native reader generation.
  Reject obsolete native generation before it can retire a newer healthy trace.
- Deduplicate sample count per slot/reader identity, not topic name. Advancing
  primary policy generation alone does not clear unrelated watch histories;
  session generation changes do clear all retained samples.
- Pending/failing/removed/stale readers cannot resurrect their fields from queued
  snapshots. Stop/disconnect reject late intake. No time/value is reconstructed
  from echo text, age, Hz, UI paint or a mock clock.

## Validation and evidence boundaries

Recovery evidence lives in ignored `build/plots-integration-evidence/` on the
authorized Ryzen worktree. Earlier focused native and Java RED/GREEN logs remain
separate from final wrapper runs; they are not reused as fresh full-suite counts.

Real isolated `plot_multiwatch` uses separate rclpy and native Session processes
under each Jazzy RMW, Fast DDS and Cyclone DDS. It checks four simultaneously
receiving structured topics plus advancing `/rosout`, all 11 supported descriptor
types in each of four slots (**44 slot/type cases per RMW**), exact Int64 transport,
all Odometry leaves, nonfinite values, Unicode frames, distinct source/header/
receive clocks, idle timestamp stability, QoS mismatch/recovery, actual topic
creation failure/recovery, removal and teardown. Healthy slot counts/timestamps
and log sequence must advance throughout local failures. This proves native
wire-to-snapshot integration; it is not Android-on-device evidence.

Robolectric tests exercise the real Activity/controls and host Canvas using an
explicit JNI double: all four selectable projections, pause/intake independence,
late blocked-poll/request interleavings, primary removal/QoS, subscribe completion
races, topic/type/generation rejection and lifecycle invalidation. They do not
pretend that mocked JNI is DDS traffic. The official fixture wrapper additionally
preserves the 04/05/06 console, typed, wire-bound, multiwatch and connection gates.

Commands (toolchains/caches must already be present):

```bash
export JAVA_HOME="$HOME/.local/greenbot-android/jdk-17"
export ANDROID_HOME="$HOME/.local/greenbot-android/android-sdk"
export POCKET_ROBOLECTRIC_JARS="$HOME/.cache/ros2-pocket/robolectric"
export GRADLE_USER_HOME="$HOME/.gradle"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
unshare -Urn bash -c 'ip link set lo up; bash tools/build.sh --offline'
bash tools/test.sh --fixture
```

The offline wrapper's session tests see only private loopback. Fixtures use
unique disposable Docker `--network none` containers, the worktree's own binaries
and exact library prefix. Fixture-image identity covers Dockerfile, publisher and
discovery configuration; no libraries are borrowed from other worktrees.
Outputs remain `app/build/outputs/apk/debug/app-debug.apk` and
`build/artifacts/app-debug.json`. Metadata derives package/version, ABI, hash and
debug certificate from the artifact and records clean commit or dirty source
snapshot truthfully. Build source snapshots must match before/after execution.

Remaining gates: independent review, final published-head CI and (only after
review/authorized merge) exact integration push CI; physical tablet readability,
loading/lifecycle/loss/recovery and four-watch plot soak with predeclared
CPU/memory/battery budgets; explicit operator acceptance in increment 08.
No ADB/install, robot/LAN production traffic, VPN/firewall/access changes,
production signing, release publication or `integration → main` merge is part of
this increment. Synthetic bounded histories and native fixture measurements are
not physical endurance qualification.

## Increment 14 — phase fixture and qualification harness

The dedicated `tests/device_plot_fixture.py` publishes two **2 Hz** Jazzy
Float64 topics in a fresh `/plot_<32 hexadecimal UUID>` namespace. Phases are
finite nonzero, NaN, positive/negative infinity, source stop/return and a sustained
window run. Each phase must last at least six seconds; a short nonfinite burst
that the ordinary 500 ms worker could miss is not a passing control. The second
publisher stays finite and advancing throughout primary-source failures.

`tests/device_plot_writer.cpp` is an **additional separate Cyclone DDS writer**
for explicit `dds_write_ts` reset/forward-jump stimuli. The rclpy API does not
supply a DDS source-timestamp override: changing a ROS header would not test this
contract. `/a` and `/b` prove Jazzy Fast DDS/Cyclone interoperability; `/stamped`
proves real DDS source-clock behavior with a Cyclone producer. These are distinct
claims. No writer is compiled into or exposed by the Android product.

### Host reproduction (no Android/device access)

Reuse the pinned toolchain read-only; generate/build only in this worktree.
`tools/test.sh --portable` prepares `build/native-host/multiwatch_wire` and the
real `plot_numeric` test. The latter is an in-process DDS numeric control, not
separate-process Jazzy evidence. Build the optional stamp writer with:

```bash
export POCKET_TOOLCHAINS=/absolute/path/to/pinned/toolchains
bash tools/test.sh --portable
cc -c app/src/main/cpp/generated/PocketTypes.c \
  -I"$POCKET_TOOLCHAINS/host/include" -o build/plot-types.o
c++ -std=c++17 tests/device_plot_writer.cpp build/plot-types.o \
  -Iapp/src/main/cpp/generated -I"$POCKET_TOOLCHAINS/host/include" \
  -L"$POCKET_TOOLCHAINS/host/lib" -Wl,-rpath,"$POCKET_TOOLCHAINS/host/lib" \
  -lddsc -o build/plot-stamped-writer
python3 tests/run_plot_host.py --image EXISTING_IMAGE_ID \
  --toolchains "$POCKET_TOOLCHAINS" --out "$PWD/build/plot-dds-run"
```

The runner never pulls an image. It pins the resolved image ID and launches each
RMW in its own uniquely named, nonroot, read-only **`--network none`** container
with resource limits, an absolute process deadline and removal/readback of that
exact ID. The exact pinned host ddsc prefix precedes ROS's library path; otherwise
sourcing Jazzy can silently replace the tested library. The existing pinned
`tests/fixture.Dockerfile` image is sufficient; the new phase fixture is mounted
from this checkout, not copied into the generic CI publisher.

`plot_phase_host.py` polls the **real production Session** at 500 ms, retaining
raw JSONL and publication events. Every nonfinite phase must reach at least five
distinct native cumulative counts. The window phase runs 34 real seconds. It also
checks idle timestamp stability, a continuously advancing healthy reader,
source-reset/jump with monotonic receipt, removal and reassignment. Source count
jumps are not expanded into points.

With those actual records, run the opt-in native host Canvas replay:

```bash
export POCKET_PLOT_DDS_RECORDS="$PWD/build/plot-dds-run"
# Select JDK 17, the existing SDK, a populated explicit GRADLE_USER_HOME,
# and POCKET_ROBOLECTRIC_JARS as described above; keep <=2 Gradle workers/1 GiB.
./gradlew --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1g \
  :app:testDebugUnitTest --tests '*Plot*Test'
# Restore the full suite after the filtered run; parse nonempty XML reports.
./gradlew --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1g \
  :app:testDebugUnitTest :app:lintDebug --rerun-tasks
```

`PlotDdsReplayTest` feeds those unmodified records through production
`PlotController`/`PlotHistory`/`PlotView` and Robolectric **native graphics**,
checking real receive-window eviction, gaps, retirement/reselection, reader
identity, pause pixels with continuing intake, selective paused removal and
completed-draw provenance. Reset may preserve selected empty controls. The
single frame that explicitly reselects returned slots 0/2 may also be empty;
this test-local selection transition is not claimed as an applied native sample.
Only those exact slots on that frame, the primary pending slot, and stopped
controls are exempt from populated-data checks. None of these control frames
counts as data-bearing evidence. Subsequent return observations are drawn and
must provide multiple populated, provenance-bound completed frames. It saves phase chart PNGs
beside the input records.
This is a real-DDS-record replay plus host Android Canvas, **not** the Android
worker/JNI or physical rendering. Without the opt-in environment it explicitly
skips; a filtered/empty/skipped report is not DDS-Canvas acceptance. The host
campaign's full Java XML scan permits exactly one intentional exception:
`com.lamggm.rospocket.RealJniSessionTest`/
`realJniLeaseFailureReceiptPollRecoveryAndSurvivors`, skipped with the exact
message `Explicit isolated host JNI gate only`. That case is exercised by the
parent Linux JNI gate; this campaign does not claim JNI execution. The
`PlotDdsReplayTest` report remains fully unskipped and must contain exactly one
`bothRealRmwStreamsTraverseModelWindowAndCompletedCanvas` case; its helper unit
case is also retained.

`PlotQualificationTest` additionally proves exact **599 → 600 → 601** retention
on each of four rings, inclusive 30-second boundary and cross-ring eviction,
actual Canvas absence of lines through receive/source breaks, and two-series
pause/resume pixels. Its timestamps are deliberately synthetic model inputs.
The 600 boundary is **not** claimed on the 2 Hz device: a 30-second latest-snapshot
window should hold only roughly 60 observed points, never 600 fabricated ones.

A behavioral RED exposed a paused same-name replacement race: retirement and
reselection could complete before refresh, leaving an old frozen reader copy
because its string ID was selected again. `PlotView` now matches the immutable
series provenance against the current selected history, filtering the retired
copy while retaining other paused traces. The QA12 schema/export paths are
unchanged. A resume displays only the newly selected current reader.

### Opt-in physical campaign, prepared but NOT executed in this increment

`python3 tests/tablet_plot_acceptance.py --help` is safe without ADB configuration.
The `run` command requires an explicit serial, `--authorize-device`, actual
candidate APK, domain, run namespace and **immutable existing fixture container
ID**. It uses the increment-10 guarded client for user/package/focus/IME/private
hierarchy capture and bounded navigation. No install, emulator, network-policy
change or fixture creation is performed by this CLI.

The separately authorized bench owner must start the dedicated publisher with
its actual Docker `Config.Cmd` including all of:

```text
python3 /fixture/device_plot_fixture.py --domain <domain>
--namespace /plot_<uuid> --control /tmp/plot-phase --duration 600
--stamped-writer /fixture/plot-stamped-writer
```

Set label `ros-pocket.plot-run=/plot_<uuid>`. Mount the exact fixture Python file
and locally verified `build/plot-stamped-writer` at those paths, the pinned ddsc
prefix read-only, and provide writable `/tmp`, a deadline and resource limits.
The CLI verifies command/label/running state and both executable/source hashes
before any phase write or shutdown. A physical LAN fixture requires separate
network authorization; this software round ran **only network-none containers**.

```bash
python3 tests/tablet_plot_acceptance.py run --authorize-device \
  --serial "$AUTHORIZED_SERIAL" --namespace "$RUN_NAMESPACE" --domain "$DOMAIN" \
  --candidate-apk "$FROZEN_APK" --fixture-container "$OWNED_CONTAINER_ID" \
  --out "$PRIVATE_EVIDENCE"
# Optional --peer is the existing app peer setting; unset means automatic mode.
```

The campaign verifies installed APK SHA-256, navigates source-derived selectors
from both viewport directions, explicitly configures best-effort/volatile,
selects three numeric series, records fresh worker/export fences and completed
Canvas statistics, captures/crops the actual chart, checks frozen pause pixels
with advancing counters and changed resume pixels, both paused removal controls,
stale retirement/no automatic resurrection, reset/jump, real window eviction and
reassignment. Cleanup independently disconnects the guarded app and gracefully
stops the verified fixture with state readback; failures remain failures.

Host tests include viewport-omitting injected transports and a fully synthetic
campaign orchestration control. Those are **not physical evidence**, even when
they exercise the actual public campaign function. Physical results deliberately
retain `physicalAccepted: false`: screenshot/readability review, exact integrated
candidate review/CI and explicit operator acceptance remain separate. The
30-minute official resource/battery soak is not replaced by this functional run.
