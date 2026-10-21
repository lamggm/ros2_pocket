# Structured inspection — increment 05

Closed decoder set: `geometry_msgs/msg/Vector3`, `Quaternion`, `Twist`, `Pose`
and `nav_msgs/msg/Odometry`, plus the original six std_msgs primitives. Generated
Cyclone descriptors come from real ROS-compatible IDL, not offsets inferred from
CDR bytes. Point, Time, Header and covariance wrappers are dependencies only;
their standalone topics remain unsupported. No dynamic/custom decoder, ROS
publisher, RPC, network-policy change or bridge was added.

## Additive native/JNI contract for watches and plots

`Session::poll()` retains all prior fields. After reconciliation with 06, every
entry of the four-element `watches` array owns an independent `message`, initially
JSON `null`; the top-level `message` remains exactly the slot-zero projection.
All eleven decoders work in all four slots. After intake each tree contains:

```json
{
  "schemaVersion": 1,
  "type": "geometry_msgs/msg/Vector3",
  "root": {"path":"", "kind":"object", "children":[
    {"path":"x", "kind":"number", "display":"1.25", "value":1.25, "numberState":"finite"},
    {"path":"y", "kind":"number", "display":"NaN", "value":null, "numberState":"nan"},
    {"path":"z", "kind":"number", "display":"+Inf", "value":null, "numberState":"positive_infinity"}
  ]},
  "numericPaths": ["x", "y", "z"],
  "truncated": false,
  "limits": {"maxStringBytes":512, "maxNodes":192, "maxSummaryBytes":8192}
}
```

- Root path is empty. Object members use ROS field names and dots; fixed arrays
  use zero-based `[index]`, e.g. `pose.pose.position.x`, `twist.twist.linear.z`,
  `pose.covariance[35]`. Paths are case-sensitive and stable, independent of
  value, UI refresh, freshness, publisher GUID and localization. Order is IDL
  declaration order, then array index. Scope selections to topic **and type**.
- `kind` is `object`, `array`, `number`, `string` or `boolean`. Containers own
  `children`; leaves own `display` and `value`. All and only number leaves occur
  in `numericPaths`, including nonfinite values and Header integer fields.
  Boolean/string fields are not numeric selections.
- Floating `numberState` is `finite`, `nan`, `positive_infinity` or
  `negative_infinity`. Nonfinite `value` is JSON null, with display `NaN`, `+Inf`
  or `-Inf`; it must never become zero. Finite doubles use round-trip precision
  and the classic locale. Integers are exact decimal JSON tokens and exact
  display strings (including Int64 minimum); consumers must not force Int64
  through a lossy JavaScript/double conversion if exact integers are needed.
- `header.stamp.sec` and `header.stamp.nanosec` are untouched **source** fields,
  not Android time, DDS reception time or the monotonic sample-age clock.
  `header.frame_id` and `child_frame_id` are source UTF-8, including supplementary
  Unicode. Covariance arrays each contain exactly 36 entries, not a sequence or
  an inferred matrix. No frame transform, unit conversion or quaternion
  normalization is performed.
- Tree string leaves are capped at 512 bytes on UTF-8 boundaries. String leaves
  and the envelope explicitly report `truncated`. The legacy primitive String
  `last` still permits 8192 bytes, now without splitting a multibyte character.
  Closed fixed schemas contain fewer than 192 nodes; there are no unbounded
  message sequences. Only the latest tree is retained, not message history.
  These retained/presentation bounds are distinct from the DDS wire limit below;
  DDS reader sample-count resource limits remain unchanged.
- `message` is cleared on reader replacement/creation failure, Stop and session
  reset. An unsupported subscription follows the existing conservative contract:
  it reports an error and preserves the old reader, not a fake decoded value.
  Such a snapshot is not fresh and its subscription state is error. Consumers
  must honor error/readerActive/fresh/age and generation fences, not infer
  liveliness from the presence of a tree. Successful resubscription recovers.
- JNI still transports byte arrays decoded using StandardCharsets.UTF_8, not
  modified UTF-8 `NewStringUTF`.

## DDS wire admission versus retained bounds

Every production `Session::start()` configures Cyclone DDS
`Domain/Internal/MaxSampleSize` to **64 KiB (65,536 serialized CDR bytes per
sample)**. This domain-wide ceiling covers telemetry, dedicated `/rosout`, graph
and discovery traffic; it is not a per-string length or a JSON/JNI size limit.
Samples **larger** than the ceiling are discarded by middleware with a warning,
not partially decoded or counted as fresh intake. A subsequent regular sample
on the same reader can recover normally, without a new session/subscription.
Oversized rejection currently has no app-specific UI counter.

The ROS IDL is unchanged: Odometry's `header.frame_id` and `child_frame_id`, and
ROS String/Log fields, remain standard unbounded strings for interoperability.
A valid ROS message can therefore be outside this inspector's admission scope.
64 KiB is a deliberate prototype tradeoff: it accommodates the fixed Odometry
schema plus ordinary frame IDs, our approximately 30 KiB long-frame regression,
10 KiB String and long rosout fixtures, and a 60 KiB-frame positive control,
while rejecting oversized wire strings *before* application clipping. Very
large ROS graphs/discovery announcements and otherwise valid String/Log payloads
above this ceiling are also out of scope. Change the ceiling only with a renewed
memory budget and isolated regression evidence, not by bounding the ROS IDL.

Verified against the pinned upstream Cyclone DDS **0.10.5**, commit
`2cdd114cbd18340c606573b4cc8dc20cc161ec5a`:

- `src/core/ddsi/include/dds/ddsi/ddsi_cfgelems.h`, `MaxSampleSize`: supported
  `Internal` memsize setting, default `2147483647 B`, maximum serialized CDR
  size forwarded in either direction; oversized samples discarded with warning.
- `src/core/ddsi/src/q_receive.c`, `handle_Data` and `handle_DataFrag`: compare
  `sampleinfo->size` with `config.max_sample_size` and call `drop_oversize` before
  the normal delivery/fragment-reassembly branch. This is the load-bearing
  receive-side protection, not just a producer-side limit.

This limits an individual admitted serialized sample, **not total process memory**:
DDS still has packet buffers, endpoint/participant state, multiple samples/readers,
application trees and JSON copies. Existing sample-count limits, bounded retained
strings and rosout ring budgets remain separate protections. It is not a general
DoS defense, traffic-rate cap, allocator measurement or SROS2 authentication.

`wire_sample_bounds` is a required `fixture;structured;security` CTest gate. In
each existing disposable `--network none` fixture (Fast DDS and Cyclone RMW), a
real rclpy ROS publisher and a **separate native production Session process**
exchange RTPS. The test admits regular and sub-ceiling (60 KiB frame) Odometry,
rejects a >128 KiB sample for each frame string with unchanged intake counts,
then admits normal Odometry on the same reader/generation. It also requires
Cyclone's oversize diagnostic and successful ordinary String plus dedicated Log
intake afterward. Existing Unicode/truncation, String and first-burst rosout
regressions remain required and unchanged. No host/LAN publisher is started.

## Android presentation

The native hierarchy is rendered with local numeric selection controls. Selecting
or changing a field never creates/replaces a reader. Selection survives value
refreshes; it is cleared on type/inactive/session changes. The main connection
policy and pending-reader fencing remain authoritative. Stale trees are muted
and explicitly marked as the last value; error/pending/offline states clear the
old tree. Unsupported schema versions are not rendered as supported data.

Presentation visits at most 192 nodes, depth 8 and 512 code points per leaf
label (paths 160 characters), with an explicit limit notice. Even malformed null
children consume the traversal budget. There are four independent bounded text inspectors, explicitly labeled by slot
and generation, not plots or an arbitrary dynamic IDL browser. The primary
inspector always says Watch 1, even when editing another watch. Pending request
frames are captured before polling; old queued trees cannot cross a replacement,
QoS, removal, clear or lifecycle boundary. See [merge audit](MERGE-05-06.md).

## Verification handles

Use the existing gates (no additional workflow job or duplicate gate):

```sh
bash tools/test.sh --portable
bash tools/test.sh --fixture
bash tools/build.sh --offline  # warmed caches, JDK17/SDK as documented in README
```

- `structured_core`: portable native tree/primitive/integer/unsupported contract.
- `structured_*` fixtures: real Jazzy Python typed publisher and **separate native
  process** on private loopback inside disposable `--network none` containers,
  once for Fast DDS and once for Cyclone DDS. Readers are not host/LAN publishers.
  Tests check all five new types, all six primitives, advancing intake/source
  stamps, UTF-8 frames, both entire covariance arrays, exact Int64, NaN/Inf,
  truncation boundaries, unsupported rejection, failed creation and recovery.
- `StructuredMessageViewTest`: actual host Android framework JSON/Views through
  Robolectric; local field selection, freshness/lifecycle, schema rejection and
  bounded rendering. This does **not** prove native loading on a tablet.
- Required CTest label gates keep `--no-tests=error`. Fixture image identity hashes
  Dockerfile + publisher + loopback config; scripts are mounted read-only at
  normalized paths. Passing JUnit output retains compact real-value evidence.

This increment is committed locally for integration review. Earlier development
builds record base HEAD **plus dirty state and source-content snapshot**; only
builds from an unchanged clean tree identify an exact clean source commit. Local APK/test evidence does not mean GitHub CI ran. No tablet/ADB,
physical DDS/Wi-Fi, RF outage, IP-change, endurance or 16-KiB-device acceptance was
performed here; those remain qualification work for the integrated candidate.
