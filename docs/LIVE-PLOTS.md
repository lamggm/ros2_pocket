# Live numeric plots — increment 07

## Delivered now, and integration still pending

This branch is based on `e71bdf3` (increments 01–03). Increment 05's structured
field contract and increment 06's concurrent watch contract were **not integrated**.
No files from their dirty worktrees were consumed. Therefore structured ROS
messages / four simultaneous real ROS topic plots are **not end-to-end accepted**.

The reusable Java history/controller/Canvas component supports four overlaid
series and typed field paths. The Activity wires the **existing single reader**
to real native numeric primitive samples (`Int32`, `Int64`, `Float32`, `Float64`),
using the additive snapshot contract below. String/Bool are not numeric series.
Select a numeric topic, wait for a fresh message, then select its `data` checkbox.
Selection/removal/pause do not subscribe, publish, or restart DDS. There is no
publisher, reader manager, structured decoder, persistence, export or bridge here.

The current native poll drains DDS but returns only its **latest** sample; charts
record at most one latest-sample point per 500 ms poll, not every taken message.
A count jump is not reconstructed/interpolated into invented samples. This is
explicitly a *snapshot plot*, not a lossless recorder or source-rate measurement.
Visible refresh remains approximately 1.5 s, independent of intake; pausing
freezes a bounded display copy while the controller keeps ingesting.

## Bounded histories and presentation

- At most four selected series, default 600 points / 30 seconds per series.
  `PlotHistory` rejects invalid budgets (maximum configurable 2048 / 300 seconds).
  Each ring is a bounded deque; eviction uses both count and receive-time window.
- Controller: at most four active watch identities, 64 fields per watch, bounded
  identifiers/path/unit lengths. `retire(watch)` frees that watch's budget without
  clearing other histories; global `reset(epoch)` fences old session callbacks.
- The paused copy is immutable and bounded by the same series/count limits.
  Reader/session/error/stale/lifecycle invalidation also clears a paused copy.
  Selecting/removing series is local; removals remain available for inactive sources.
  Removing one paused trace filters only that trace from the frozen copy; the
  other traces remain frozen until resume. Each refresh acknowledges only the
  invalidation revision it observed, so a concurrent reset is not swallowed.
- NaN, either infinity, missing fields and invalid typed values are gaps (`null`),
  never zero. Lines break across explicit gaps, receive intervals over 3 seconds
  and backwards source timestamps. Receive-clock regression clears that ring.
- X is relative **receive-monotonic** seconds (zero = latest received point), not
  wall-clock/header/source time. Y is a shared raw-value auto-scale; overlays of
  different units are not converted or claimed dimensionally comparable.
- Units are only the adapter's explicit declaration; primitives use `null` /
  “unidade desconhecida”. A field named `x` does not imply metres.
- Plot math uses binary64. Int64 remains exact in the native JSON number, but its
  plotted binary64 conversion may round integers outside ±2^53. Float64 values
  are serialized with 17 significant digits, not parsed from the formatted echo.
  The scale normalizes before subtraction so ±Double.MAX_VALUE stays finite.

## Additive native/JNI snapshot contract

Existing snapshot fields and JNI entry points are unchanged. `poll()` adds:

```json
{
  "receivedMonotonicNs": 123456789,
  "sourceTimestampNs": 987654321,
  "numericFields": [
    {"path":"data", "unit":null, "state":"finite", "value":42.5}
  ]
}
```

The example illustrates the schema only, **not** captured ROS evidence.

`receivedMonotonicNs` is the native steady-clock time when the latest valid sample
was taken/decoded. It changes **only on a received sample**, not each poll.
`sourceTimestampNs` is that sample's DDS `source_timestamp`, **not a ROS header**
and not assumed to share a clock/epoch with receive time. Both are JSON `null`
before receipt and after reader teardown. Source resets never move the X axis.
`numericFields` is empty before receipt, for nonnumeric types and after teardown.

`state` is `finite`, `nan`, `positive_infinity` or `negative_infinity`. `value` is
an actual typed JSON number for finite values and `null` otherwise. No invalid
JSON NaN/Infinity tokens are emitted. Numeric primitives have path `data` and
unknown unit. Int64 is serialized as an integer, without a double round-trip.

The adapter requires a running, fresh, active, receiving reader, no error and no
pending replacement. It deduplicates count per reader identity and rejects old
policy/session/reader epochs. The Activity ingests on the ordered DDS worker,
under the connection-policy fence, **before** checking the visual rate limiter.
Connection changes, errors, reader changes, Stop and disconnect clear histories;
old data is not replayed as freshly received. No timestamp is reconstructed from
`last`, age, Hz, UI paint time or a mock clock.

## Explicit handoff for increments 05 / 06

`PlotController.Frame(watch, epoch, sequence, received, source, fields)` is the
small isolated **component input**, not an already agreed upstream wire schema.
`Field(watch,path,unit,value)` carries typed values; nonfinite/null is a gap.

The future adapter must:

1. Supply actual numeric paths/declared units from 05, never formatted display text.
2. Supply unique reader/watch identities and monotonic receive timestamps from 06.
   All watches share the same monotonic receive clock domain. `epoch` is a global
   ingestion-pipeline generation, not a per-watch sample counter.
3. Serialize intake or retain the synchronized controller, fence obsolete callbacks
   at the reader manager, and retire removed/replaced watches. `retire` is not a
   replacement for 06's reader-generation fence; a late frame must not resurrect it.
4. Use increasing per-reader `sequence` values. Duplicates/out-of-order counts are
   ignored. Send one complete bounded field set per frame; absent selected fields
   append a gap. Empty frames are not synthetic received data.
5. Reset the pipeline epoch on lifecycle/global reconnect, and retire/reselect if
   a field's type/unit metadata changes. No ROS reader is created by this component.
6. Decide whether the source provides every received message or only latest
   snapshots. Do not claim lossless/high-rate plots without a measured intake API.

Four-watch behavior is tested with explicit unit-test frames, **not claimed as
four real ROS receptions**. The existing native fixture independently proves
actual typed DDS→snapshot values, timestamps, precision and teardown semantics.

## Validation and remaining gates

Behavioral RED/GREEN evidence is kept under ignored `build/plots-evidence/`:
bounded model/API introduction, nonfinite/gaps/source resets, controller epochs,
real View/Canvas, Activity wiring/rate independence, paused invalidation/removal,
independent watch retirement, native exact Int64 transport, deterministic reset
interleaving after revision observation, and both paused removal controls while
another trace continues ingesting. Missing-class
compilation REDs are distinct from the later behavioral assertion REDs.

Commands on the authorized Ryzen checkout (JDK/SDK paths are local prerequisites):

```bash
export JAVA_HOME="$HOME/.local/greenbot-android/jdk-17"
export ANDROID_HOME="$HOME/.local/greenbot-android/android-sdk"
export POCKET_ROBOLECTRIC_JARS="$HOME/.cache/ros2-pocket/robolectric"
export GRADLE_USER_HOME="$HOME/.gradle"
export PATH="$JAVA_HOME/bin:$PATH"
# unshare -r changes Java's user.home unless explicitly pinned.
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
unshare -Urn bash -c 'ip link set lo up; bash tools/build.sh --offline'
bash tools/test.sh --fixture
```

The portable/full-build native tests run inside a **private user/network namespace**
with only its own loopback; they cannot reach the host LAN. The standard fixture
runner uses disposable Docker **`--network none`** containers for both Jazzy RMWs.
The complete portable CTest selection was additionally exercised in its own
`--network none` container, including the numeric fixture. That replay only adapted
the copied generated CTest manifest from the host Python 3.14 path to the
container Python 3 path, and used the host UID/GID for its writable report mount.
The initial replay failed on those environment paths/permissions; it was not a
product-test failure. No existing named tablet fixture was touched. Wrappers/CI were not
changed. A first isolated build failed because Java chose `/root/.gradle` after
`unshare -r`; explicit `GRADLE_USER_HOME`/`user.home` fixed the environment only.

Outputs remain the standard `app/build/outputs/apk/debug/app-debug.apk` and
`build/artifacts/app-debug.json`. The metadata reports the dirty source snapshot
and base SHA for uncommitted builds, or the exact clean commit after committing.
Raw RED logs and intermediate build failures are not acceptance results. Debug
artifacts are not releases or production-signed updates. No ADB/install/device
automation, robot or network-policy change was performed.

Pending: reconcile 05/06 contracts, real structured selection and concurrent ROS
watch/plot integration, physical tablet usability/Canvas/loading, source reset
and loss/recovery on real producers, four-watch endurance/CPU/memory/battery and
final integrated-SHA CI/qualification. A bounded synthetic unit test is not a
physical endurance measurement.
