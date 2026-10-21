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
