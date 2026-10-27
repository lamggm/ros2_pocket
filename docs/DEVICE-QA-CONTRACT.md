# Debug device QA metadata contract — v1

This is a **read-only local diagnostic**, not a robot interface, telemetry service or release acceptance. It projects the existing DDS worker's latest poll. Exporting does not poll DDS, create readers, change subscriptions or refresh the chart. No HTTP server, exported provider, intent command or extra native session is added.

## Transport and framing

For the authorized DEV device/package only:

```sh
adb -s "$SERIAL" shell dumpsys activity \
  com.lamggm.rospocket.dev/com.lamggm.rospocket.MainActivity --pocket-qa-v1
```

The selected Activity's dump contains exactly one frame:

```text
ROS_POCKET_QA_V1_BEGIN
<one compact JSON object, schemaVersion = 1>
ROS_POCKET_QA_V1_END
```

These lines describe the framing; the placeholder is **not sample evidence**. Android adds its own dump headers. Select the exact component, require one complete frame, reject truncation/duplicates, and retain failed reads separately. The consumer must set its own ADB timeout (at most 5 s), input-byte bound and JSON depth/type validation. Do not reuse previous output when a command fails.

The command is accepted only as the single exact argument in **debug builds**. Release returns `ROS Pocket QA unavailable`, without a frame. Unknown/extra arguments follow normal Android dump handling; they cannot arm test controls. ADB authorization remains the access boundary. This is not a network-accessible endpoint.

The JSON is metadata-only and limited to **24,576 UTF-8 bytes**. Native snapshots, echo/structured values, numeric sample values, topic/type names, complete error strings, logs, peer addresses and credentials are not exported. Slot/request/reader generations identify readings without publishing their names. Do not commit raw device evidence.

## Root object

- `schemaVersion`: integer `1`. Any incompatible format requires a new version/token.
- `valid`: boolean. All four slots are structurally coherent with the captured requests, applied/native generations and running native session, and capture age is within the limit. **Not** equivalent to receiving, four active watches, chart visibility, no errors or qualification readiness.
- `clock`: `java.nanoTime`; all `*MonotonicNs`, `captureAgeNs`, `drawAgeNs`, and `windowNs` fields are nanoseconds. These are monotonic process observations, **not** wall time or ROS source timestamps. Do not compare Java nanoTime with `elapsedRealtime` or host time; suspend semantics differ.
- `capturedMonotonicNs`: worker timestamp taken before the existing poll. `exportedMonotonicNs`: timestamp at dump. `captureAgeNs`: nonnegative difference, otherwise null. `maxCaptureAgeNs`: `2000000000`. A missing, regressed-clock or older capture is invalid; export never retimestamps stale counters as a new capture.
- `lifecycleGeneration`, `policyGeneration`, `sessionGeneration`: integer fences. Missing/invalid native session generation is null and invalidates the sample.
- `watches`: exactly four entries in slot order `0..3` for a captured sample. An invalidated/not-yet-captured sample has an empty array, `valid:false`, no capture/session fields and null capture age. Absence does not mean zero counts.
- `foreground`: Activity-started/current-owner observation. Consumers must also require chart visibility/focus; this flag alone does not prove the Activity is unobscured.
- `applicationId`, `versionName`, `versionCode`, `debuggable`, `pid`: compiled/runtime identity metadata. Version metadata is not a Git/APK identity. Bind independently to the exact installed APK hash/certificate and build provenance; version finalization belongs to increment 15.
- `testHooks`: nested schema-2 coherent cached native/Java diagnostic state from increment 12. A fresh OFF binary reports boolean `capability:false`; explicit diagnostic ON reports `capability:true` and `contract:ros-pocket-dds-fault-v1`, even while disarmed. Missing, malformed, mixed, revision-invalidated or stale state reports `valid:false`/unknown, never safe false. See [the diagnostic policy](ANDROID-DIAGNOSTIC-POLICY.md) for required identities and independent OS proofs. The public watch coherence rules are unchanged.

Invalidation occurs on Stop, explicit disconnect/new connection, native teardown/network loss, and primary/extra request replacement/removal. Epoch checks reject in-flight old polls; native reader fences reject an obsolete generation even after a same-topic QoS request has applied. The next genuine coherent worker poll is required for recovery.

## Per-watch fields

- `slot`: integer `0..3`; identity is the tuple of lifecycle/policy/native session, slot, request/applied version and reader generation. Consumers must reject unexpected reset/change, decreasing counters or mixed identities rather than stitching a continuous run across them.
- `requestVersion`, `appliedVersion`: signed Java long request/applied fences; `-1` is an unapplied version, not an intake value. `pending`: whether the pre-poll request had not applied.
- `readerGeneration`, `count`, `matched`: exact nonnegative JSON integers, or null when unavailable/invalid. Never convert counters through floating point. Values outside signed-long representability are invalid, not approximated.
- `valid`: coherence of this slot. Native rows are checked in their exact slot order and against the actual request's topic/type internally. Consumers must reject `valid:false` even if some diagnostic counts are present.
- `readerActive`, `nativeFresh`: native booleans, otherwise null.
- `receivedHz`: finite nonnegative intake rate, otherwise null. It is not guaranteed publisher frequency.
- `sampleAgeAtCaptureSeconds`: finite native sample age in **seconds**; `-1` means no sample, otherwise nonnegative. It is not automatically advanced in the cache.
- `freshAtExport`: conservative boolean recomputed from original native freshness, sample age plus capture delay, active/matched/nonzero intake, no error, coherent slot and nonexpired capture. A cached `receiving` state alone is insufficient.
- `state`: bounded enum `inactive`, `waiting`, `receiving`, `stale`, `error`, `pending`, `unavailable`, or `unknown`. Unknown native strings are not exported. An error is retained as a boolean/state, not its potentially private text.
- `hasError`: boolean for captured subscription/native row error. A valid snapshot may report an error; it must not pass a healthy-intake gate.
- `requestedReliable`, `requestedTransientLocal`: requested QoS booleans, not proof that a writer matches.

Missing integers/booleans/rates/ages and malformed native states invalidate the sample. Do not map absent data to zero, false, a previous reading or a publisher-side counter.

## Plot intake versus displayed chart

`plotIntake` is captured with the worker projection **after existing plot ingestion**, without appending any point on export:

- `capacityPerSeries`: `600`; `windowNs`: `30000000000`; `maximumSeries`: `4`.
- `invalidationRevision`: chart-controller invalidation revision, **not** a message sequence or every-append revision.
- `series`: bounded list of ring statistics below. Empty/unselected series do not establish an active chart.

`plotDisplay` is a separate observation taken on the Activity/UI dump path, not falsely made atomic with the older worker capture:

- `observedMonotonicNs`, `paused`, `chartFullyVisible`.
- `lastDrawMonotonicNs` and `drawAgeNs`, null until an actual Canvas draw. A fully-visible rectangle alone does not prove fresh rendering.
- `series`: statistics of the prepared bounded display copy; during pause these can stay frozen while intake advances. They are **not** proof that this copy has reached Canvas.
- `preparedRevision`: increments on refresh; `drawnRevision`: revision recorded on completion of the last nonzero-size Canvas draw (null until then). `drawPending`: whether the prepared copy has not completed a draw.
- `drawnSeries`: immutable metadata of the copy actually used by that completed draw. It is recorded with `lastDrawMonotonicNs`, never synthesized from a later refresh. For rendering acceptance require `drawPending:false`, equal prepared/drawn revisions and nonempty finite `drawnSeries`; do not combine fresh prepared statistics with an earlier draw timestamp.

`chartFullyVisible` requires the actual chart View to be shown, focused, nonzero-sized, and fully inside its clipped global visible rectangle. It is not inferred from the panel's existence, ScrollView position or selected series. IME/other-window overlays and actual screen output remain physical-preflight concerns; keep independent package/focus/IME checks in the harness. A screenshot is still required for visual acceptance.

Each series statistic contains `seriesIndex` (current local list position, **not** a persistent cross-list ID), `slot` (`-1` if no current slot association), `pointCount`, `finitePoints`, `gapPoints`, `breakBeforePoints`, `oldestReceiveNs` and `newestReceiveNs` (null for an empty ring). `breakBeforePoints` includes the initial boundary. Gap points are explicit nonfinite/missing-field points, not missing DDS samples, inferred packet loss or interpolation. List order/removal can change; do not compare only `seriesIndex` across updates. Ring bounds apply on intake; exporting does not age-trim an idle/frozen ring. The graph remains latest-snapshot-decimated, not every-message history.

For the eventual fixed 30-minute soak require all four healthy current counters, stable identities, real expected intake, fresh capture, nonempty finite chart data, unpaused/fully-visible/recently-drawn chart, exact candidate/process identity, verified disarmed instrumentation and the separately measured process/battery budgets. This diagnostic alone cannot make a report ready.

## Stable resource selectors

Existing tags are preserved. Resource IDs are namespaced by `com.lamggm.rospocket.dev:id/` at runtime:

- Watch selectors: `watch_select_0` through `watch_select_3`.
- Watch states: existing `counts` for slot 0; `watch_state_1` through `watch_state_3` for extras. All four retain `watch-state:<slot>` tags.
- Selected editor/removal: `watch_editor`, `watch_remove`.
- Plot panel/pause/actual Canvas: `plot_panel`, `plot_pause`, `plot_chart`.

Selectors do not make an off-screen View accessible. The dump exists specifically so all four counters can be observed while the real chart stays on-screen.

## Verification boundaries

Host Robolectric tests exercise the actual Java Activity/Views/worker queue with an explicit JNI test double: dump side effects, exact integers, slot fences/order, lifecycle/network invalidation, expiry, bounded metadata, selectors and frozen-display/advancing-intake separation. `QaDumpGateTest` runs in debug **and** release variants. Native DDS interoperability remains covered by the existing isolated fixture suites. No synthetic host test is physical-device evidence; physical preflight, soak, RF/IP interruption and operator acceptance remain separate gates.

## Explicit diagnostic DEV migration

See [ANDROID-DIAGNOSTIC-POLICY.md](ANDROID-DIAGNOSTIC-POLICY.md) for metadata/report/soak schema 2, binary-derived capability, coherent cache-only runtime evidence and mandatory independent per-sample OS instrumentation proofs. Ordinary v1 remains explicit boolean false. Diagnostic compiled capability remains true while disarmed. Same candidate APK throughout; no transferred passes. Android/vendor OS positive control and physical/soak acceptance remain **UNVERIFIED**.
