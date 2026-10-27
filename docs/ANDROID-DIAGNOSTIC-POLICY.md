# DEV DEBUG diagnostic contract v1 (metadata/report/soak schema 2)

**Source implemented and buildable; physical Android DDS, vendor OS transport,
functional acceptance and soak remain UNVERIFIED.** Host doubles, Linux JNI and
an assembled instrumentation APK do not close those gates.

## Artifact boundary

Ordinary `bash tools/build.sh --offline` explicitly compiles OFF and emits strict
metadata v1 `test_hooks: false` (boolean only). Native CMake defaults remain OFF.
All nondebug APKs unconditionally require OFF regardless of the diagnostic flag.
The no-output Gradle guard inspects every actual APK artifact/ABI even when direct
packaging is UP-TO-DATE. Duplicate ZIP entries and an ABI missing its inspector
are rejected. This does not authorize release signing or publication.

Opt in with `bash tools/build.sh --offline --diagnostic`. It builds native ON,
passes `-PpocketDiagnostic=true`, assembles the candidate and separate framework
instrumentation APK, and verifies both. Direct debug packaging without that
explicit flag rejects ON. Unknown markers/policies are not arbitrary labels.

Schema 2 requires DEV package `com.lamggm.rospocket.dev`, debuggable/debug signer,
`test_hooks: true`, policy `ros-pocket-dds-fault-v1`, and one binary-derived
`native_libraries` entry per actual APK ABI: path, SHA-256, boolean capability and
exact policy. The ELF must carry exactly one ON marker and one
`POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1` marker, with no OFF marker.
`test_hooks` describes compiled support and remains true when disarmed.

`build/artifacts/app-debug-androidTest.json` binds the distinct test APK hash,
package, exact runner/target, signer and target source/APK hash. It cannot contain
replacement native libraries. Qualification init/evaluate re-inspects the actual
diagnostic APK, including native hashes/package/debug/signature/source; installed
metadata must equal that entire snapshot except the local APK filename.

**Keep exactly the same candidate APK bytes across functional and soak phases.**
Stop instrumentation, verify owned drain, restart the SAME installed candidate in
a fresh ordinary process, and independently reverify installed identity. No
ON-test → OFF-final evidence transfer, copied passes or edited legacy metadata.

## Ownership, queue and lifecycle

Only the debug source set declares the mutable JNI controls, and those JNI
symbols are compiled only ON. Nothing registers a component, intent command,
network listener, preference or file-based fault controller. The lease contains
accidental same-process use; it is not a security boundary against compromised
in-process code.

A ticket binds owner, exact new immutable WatchSlots request, lifecycle/config,
policy, deadline, lease and unique monotonic nonce. At ticket creation it also
copies the expected native session, current slot generation and exact next
request from a fresh authoritative prior ordinary-worker capture for that
owner/lifecycle/config. UI ticket creation never fetches JNI identity. Absent,
pending, expired or owner-invalidated captures reject creation; same-worker
admission compares the immutable identities with native state and rejects any
change, never retargeting a reconnect or automatic apply. The ACQUIRED handle
binds those identities and the attempted slot generation. Acquisitions reject duplicates and reused nonces. Only
that acquired handle may cancel; rejected acquisition owns nothing. Old finally,
owner cleanup or lease end cannot cancel a replacement. Capture the actual DDS
receipt before cleanup; false cancellation after consumption is legitimate when
authoritative native state is empty. Real calls use invalid topic name or reader
parent 0, never fabricated return codes.

MainActivity retains its single process-wide DDS_WORKER, real JniApi/Session and
ordinary subscription completion/Views. No arm task queued separately from its
subscription; no global bridge swapping. Policy → WatchSlots → diagnostic
controller is the control lock order. Do not hold the controller while acquiring
policy/slots. UI/native-owner/config/generation fences remain in force.

INACTIVE → ACTIVE → CLOSING → INACTIVE uses monotonic lease epochs. Closing first
rejects tickets and revision-fences the cache, cancels its pending ticket, then
queues a barrier on DDS_WORKER. Queued work rechecks ACTIVE; running work finishes
and owns cleanup. Only verified empty arm/ticket/operation and authoritative
native lease end permits INACTIVE. Failure/timeout/rejected executor remains
CLOSING/UNKNOWN (never safe false). Stop cancels that owner's ticket/arm but does
not end the process instrumentation lease. Stale owner/epoch cleanup is inert.

## Read-only evidence

The existing ordinary worker poll is followed by read-only native state, never
another DDS poll. Diagnostic revision is captured **before** polling, checked at
publication and again at cache-only QA export. Mixed QA/diagnostic capture,
session/lifecycle/policy, malformed state or age over 2 seconds is invalid.
Exports include actual compiled capability/contract/armed state and native
revision, Java lease/ticket/operation, process UUID/PID/starttime ticks, worker
capture sequence/time and request identities. Stable healthy revision is allowed;
worker capture sequence/time must advance.

The public QA coherence rules remain unchanged: a failed subscription may yield
null/invalid watch metrics. Instrumentation alone can inspect a bounded immutable
copy (512 KiB maximum, one current copy) of the SAME ordinary poll, only under its
active lease and fresh revision. It checks at least three distinct captures,
actual rendered Views and receipt/session/generation identity, other watches and
new `/rosout` sequences, then two genuinely advancing recovery samples.

## OS evidence and official analyzer

`tools/android_instrumentation.py` implements a bounded AOSP Android16 parser:
`dumpsys activity --proto processes <exact-target-package>`. Root field 1 anchors
ProcessRecord PID/name/UID; root field 3 contains ActiveInstrumentation; its
field 5 → ApplicationInfo field 1 → PackageItemInfo field 2 is the TARGET package.
Running-process and target-process associations also count. Runner component
package alone proves nothing. Arguments and foreign payloads are not retained.
Unknown command text, truncation, missing/duplicate anchors and unsupported
schema fail closed. Parser identity is now
`aosp-android16-qpr2-am-processes-v2`; earlier `...-v1` observations and transport
attestations cannot qualify this contract. APK/source metadata is **not** rebound
by a host-parser change: an installed APK remains attributed to its original
source and bytes.

### Pinned parser subset and emitter semantics

The supported subset uses immutable official AOSP commits, not a guessed vendor
schema or a claim about Samsung firmware ancestry:

- [`frameworks/base@45034f0663f960d9ee5fb0a101a4732b71f6e2f4`](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/)
- [`frameworks/proto_logging@cef5253cee341d943c2972775e047ca368e9b1ee`](https://android.googlesource.com/platform/frameworks/proto_logging/+/cef5253cee341d943c2972775e047ca368e9b1ee/)

SHA-256 of the decoded source bytes at those commits:

| Source (relative to its repository) | SHA-256 |
| --- | --- |
| `core/proto/android/server/activitymanagerservice.proto` | `a90b5d6a6d6c410d51f8e17ea5cd59e624db86efd2a4f18b3c3343561791b3a3` |
| `stats/enums/app_shared/app_enums.proto` (proto_logging) | `9d612d70a039535fe85d6ca3f16a3a39f23ee99a54f61b3f7f31b6d50073ca58` |
| `services/core/java/com/android/server/am/ProcessList.java` | `93d5210ae9a4b72e1ea4e82ecc48d369de74bab481bed18cf3ba227e2defa699` |
| `services/core/java/com/android/server/am/UidObserverController.java` | `fae95ce1f61bfb32baf8e25d88bdc7351935797620a04b9aaa12539e5d58b7b4` |
| `services/core/java/com/android/server/am/UidRecord.java` | `27ec9bac34d59064b9788993f5e6a160de195a7ca09e7c8113e8a9e08c065944` |
| `services/core/java/com/android/server/am/ActivityManagerService.java` | `2df3c48c2e8769bf00193c70465e6b85d5208c48e23eac62820c9c083ae95601` |

Unchanged ApplicationInfo/PackageItemInfo/ComponentName/Bundle support remains
from `android-16.0.0_r1`; Duration uses that release's
`core/proto/android/util/common.proto` (SHA-256
`528a3e7ed68b51091e9e87c37041cd64396cd26c7dfe63b8ea47212e61fef8b0`).
These are explicit recursive schemas, never opaque accepted message bytes.

Root 4 and 5 are repeated `UidRecordProto`, with known `ProcessStateEnum`
values 1000–1020 (0/998/999/unknown rejected), nested Duration and sequence
messages, boolean statuses and known change enums. UID identity is required;
repeated UID records within either collection and repeated change flags reject.
The collections are **independent projections**, not identical snapshots:
`ProcessList.writeProcessesToProtoLSP` emits `mActiveUids` (lines 4639–4641);
`ActivityManagerService` emits observer validation UIDs (11520–11521);
`UidObserverController` creates `new UidRecord(item.uid, null)` and updates a
subset of event fields (296–309), then dumps `mValidateUids` (501–505).
`UidRecord.dumpDebug` (230–251) emits each record's own state. Optional presence,
states, background times and sequence values need not agree across collections.
The prior unpublished partial candidate's cross-collection equality expectation
was incorrect; its rejection is not a required safety invariant. UID records
never establish or override a PID/name presence anchor.

Root 6 is `LruProcesses`, whose field 4 repeats `ProcessOomProto`. Its global
counts need not equal a package-filtered list length. Every supported field,
nested record, enum, scalar wire and oneof is checked. Both `proc` (9) and
`adj_source_proc` (13) reconcile with **all** ProcessRecord references, including
root 1/2/7/9/10/11/15/16/18 and instrumentation running processes. They are not
new presence anchors; conflicting PID/name/UID associations still reject.

QPR2 adds `ProcessOomProto.Detail.capability_flags` at field 16: repeated
`ProcessCapabilityEnum` on wire 0, not an int bitmask or packed opaque bytes.
`ProcessList.writeProcessCapabilitiesListToProto` (1288–1295) iterates each set
bit, maps it through `ActivityManager.processCapabilityAmToProto`, and writes
one flag. Explicit supported enum values are 1–9; UNKNOWN=0, out-of-set values,
wrong wires and duplicate flags reject. All three ProcessOom oneofs remain
exclusive even for false/empty values. CPU utilization is fixed32/wire5 and
must be finite. The emitter divides `(100.0 * timeUsed)` by positive
`uptimeSince` (4781–4791); no unsupported [0,1] or [0,100] range is imposed.

Unknown sections/statuses/wires remain unavailable. Existing 1 MiB input,
16,384-fields-per-message and bounded string/identity limits remain enforced.
Tracked regression inputs are deterministic synthetic wire fixtures with invented
PID/UID identities, never copies of private device captures. Replaying captured
positive/negative bytes establishes compatibility only; a fresh physical
positive/negative qualification with this exact parser identity is still required.

No vendor transport is qualified by absence of root field 3. Actual Android16
negative AND OS-visible active-runner positive controls are hard gates. The test
runner's `mode=os-positive-control` stays active for 30 seconds without an app
lease and emits its read-only native inactive state. Parent-operated collection
must refuse it. This mode has NOT been run on a device by this implementation.

The official schema-2 `analyze-soak` requires `os_transport_validation` with the
exact parser/build/API, physical verifier/evidence digest, a normalized negative
control and an active positive control **while app lease is INACTIVE/false** with
collection refused. Every sample requires `diagnostic_proof` with independent
`os_before`/`os_after`, each containing command outcome, bounded input hash/size,
host intervals and independently bracketed PID/UID/starttime reads. Both must
prove target instrumentation absent, with the same PID/starttime/process UUID.

Required causal order on the collector host:
OS-before → fence_read → capture_read → OS-after, total at most 5 seconds.
`export_fence_ns` is a Java export-time fence, NOT compared to host clocks. The
accepted ordinary capture must be newer than that fence with advancing sequence
and capture time. Proof includes the exact fresh QA export/diagnostic projection;
four genuine watch counts must equal the metric counts, stable reader/request/
session identity and fresh visible completed chart draw are mandatory. Missing,
malformed, active, stale, late, mixed or process-changing proof is incomplete.
No SOAK_PLAN budget, duration, cadence, load or battery requirement is relaxed.
The future device collector must populate this mandatory official schema; these
parser/validator controls are not an executed 30-minute collector campaign.

## Reproduce without a device

Use the repository's documented JDK/SDK/toolchains and private network namespace:

```
unshare -Urn sh -ec 'ip link set lo up; exec bash tools/build.sh --offline'
unshare -Urn sh -ec 'ip link set lo up; exec bash tools/build.sh --offline --diagnostic'
unshare -Urn sh -ec 'ip link set lo up; exec python3 tests/diagnostic_packaging_test.py --evidence build/evidence/diagnostic-packaging'
unshare -Urn sh -ec 'ip link set lo up; exec python3 tests/nondebug_packaging_test.py --evidence build/evidence/nondebug-packaging'
unshare -Urn sh -ec 'ip link set lo up; exec python3 tests/jni_diagnostic_test.py --evidence build/evidence/real-jni'
```

The explicit Linux JNI gate loads the real host-built JNI/Session/ddsc and a
separate synthetic producer on its private loopback. It exercises primary and
extra actual topic/reader failures, persistent polls, survivor counters/logs,
recovery and Stop/lease behavior. It is NOT Android DDS execution. Robolectric
Activity/controller tests clearly use doubles and are not transferable evidence.

`ReaderFailureInstrumentation` is compiled into the separate test APK. Its
functional mode uses actual candidate Activity Views/JniApi and four synthetic
`/pocket_diagnostic/watch0..3` topics plus `/rosout` from
`tests/android_diagnostic_fixture.py` (explicit isolated domain 67, 2 Hz).
Parent owns fixture/device operation, both Jazzy RMWs, APK installation/readback,
Android execution, OS transport qualification, full CI, independent source
review, soak, operator acceptance and integration. None is implied by a host
pass or this document.
