# Structured inspection — increment 05

Closed decoder set: `geometry_msgs/msg/Vector3`, `Quaternion`, `Twist`, `Pose`
and `nav_msgs/msg/Odometry`, plus the original six std_msgs primitives. Generated
Cyclone descriptors come from real ROS-compatible IDL, not offsets inferred from
CDR bytes. Point, Time, Header and covariance wrappers are dependencies only;
their standalone topics remain unsupported. No dynamic/custom decoder, ROS
publisher, RPC, network-policy change or bridge was added.

## Additive native/JNI contract for watches and plots

`Session::poll()` retains all prior fields and adds `message`, initially JSON
`null`. After intake it contains:

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
  Intake bounds and DDS reader resource limits remain unchanged.
- `message` is cleared on reader replacement/creation failure, Stop and session
  reset. An unsupported subscription follows the existing conservative contract:
  it reports an error and preserves the old reader, not a fake decoded value.
  Such a snapshot is not fresh and its subscription state is error. Consumers
  must honor error/readerActive/fresh/age and generation fences, not infer
  liveliness from the presence of a tree. Successful resubscription recovers.
- JNI still transports byte arrays decoded using StandardCharsets.UTF_8, not
  modified UTF-8 `NewStringUTF`.

## Android presentation

The native hierarchy is rendered with local numeric selection controls. Selecting
or changing a field never creates/replaces a reader. Selection survives value
refreshes; it is cleared on type/inactive/session changes. The main connection
policy and pending-reader fencing remain authoritative. Stale trees are muted
and explicitly marked as the last value; error/pending/offline states clear the
old tree. Unsupported schema versions are not rendered as supported data.

Presentation visits at most 192 nodes, depth 8 and 512 code points per leaf
label (paths 160 characters), with an explicit limit notice. Even malformed null
children consume the traversal budget. This is a bounded text inspector, not
multiwatch, plotting or an arbitrary dynamic IDL browser.

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
