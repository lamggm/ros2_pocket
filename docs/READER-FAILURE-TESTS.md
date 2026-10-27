# Native reader-failure regression scope

## Compiled capability and request contract

Cyclone DDS is pinned to 0.10.5 commit
`2cdd114cbd18340c606573b4cc8dc20cc161ec5a`. The seam changes actual call
arguments; it does not synthesize DDS results. `POCKET_ENABLE_TEST_HOOKS`
defaults OFF, and ordinary `tools/build.sh` explicitly builds Android with OFF.
An explicit DEV DEBUG diagnostic mode and separate test APK are now implemented:
[versioned diagnostic contract](ANDROID-DIAGNOSTIC-POLICY.md). No external trigger
is registered; nondebug binaries have no mutable fault-control JNI symbols.

The test-only API requires the expected **session generation, current target
slot generation, and next watch-request identity** returned by
`reader_identity_for_test(slot)`. All three must still match at arm time. Slots
must be 0–3, stages must be `topic` or `reader`, and the target must have a live
reader with actual intake. Duplicate arms, stale/replaced identities and invalid
arguments are rejected without modifying an existing arm. The request identity
is a session-object monotonic counter, incremented for every `watch`/`subscribe`
entry, including invalid requests; polls/removals do not increment it.

An arm is detached **at entry to the next watch request, before validation**.
Only that request may inject, and only if its slot/session/attempted generation/
request all match. An invalid slot, missing participant, unsupported descriptor,
bad topic, other-slot request, or natural topic failure before the reader call
cannot leave a pending hook for a later request. Removing the target cancels;
removing another slot preserves the arm. Acquisition additionally requires a
lease epoch and unique monotonic nonce, returns an ownership handle, and explicit
cancellation requires that complete handle (including attempted generation).
Duplicate/rejected acquisitions own nothing; old cleanup cannot cancel a new
handle. Stop/restart/destruction clears
all pending state. A stopped session cannot be armed.

The bounded JSON receipt is for that one attempted request, not an unscoped
`stage:-3` string. It contains `session`, `slot`, `targetSlot`,
`attemptedGeneration`, `request`, `stage`, `topicApi`, `topicName`,
`topicArgumentOverridden`, `topicReturn`, `readerApi`, `readerParent`,
`readerArgumentOverridden`, and `readerReturn`. API/return fields are null if not
reached. Topic names are recorded only after the existing 1024-byte validation.
The topic seam uses `rt/pocket_test/invalid topic`; the reader seam uses parent
`0`. The receipt records **both** actual calls: topic `-3`/reader not reached,
or positive topic entity/reader `-3` on the pinned runtime. Polls retain it;
every subsequent watch attempt, removal, successful cancellation/arm, or session
reset clears it (a newly designated attempt can replace it with its own receipt).

## Actual host verification

`reader_failure_hook` uses a real loopback session and a separate DDS participant
writer, in a private network namespace. Its JSON verifier structurally checks
**both root primary projection and `watches[0]`**, their session/generations,
inactive/empty/null/zero telemetry, error persistence, and three genuine polls
for **each** failure. Both recoveries require a replacement reader and two new,
advancing actual fixture sequences, counts and monotonic intake timestamps.
The exact verifier also rejects a semantic mutant with active primary/count 7
and inactive extra slots, and mutations of every asserted primary field.
Cancellation/rejection tests exercise prevalidation, target/other-slot removal,
cross-slot requests, explicit cancellation, stale identities and natural topic
failure before the armed reader stage. These are host tests, not physical data.

`tests/nondebug_packaging_test.py --evidence <private-build-directory>` is an
explicit offline integration test, not part of fast Python discovery. Run it
inside `unshare -Urn` with loopback up and the documented JDK/SDK environment.
It exercises real AGP 8.5.2 unsigned release APK directory outputs with native
CMake OFF/ON/OFF in the same cache, packaged byte equality, OFF acceptance,
ON/unknown/missing rejection, an UP-TO-DATE direct package invocation, and
resource/bundle tasks that must not be mistaken for APK tasks.
`tests/native_hook_capability_test.py --evidence <private-build-directory>` also
checks OFF/ON/OFF in one host CMake cache, actual marker/symbol exclusion, and
compilation rejection of all four test-only APIs when OFF. Fixtures stay
private and are not candidates. No release signer is configured or used.

The Gradle guard uses actual Android variants, `SingleArtifact.APK` and
`BuiltArtifactsLoader`. Every APK listed by the variant is inspected, including
all its packaged `librospocket.so` ABIs. The actual package provider finalizes to
the guard; assemble depends on it. The guard has no outputs, so a cached or
UP-TO-DATE packaging task cannot skip binary inspection. Debug APKs still pass
through the independent normal-candidate metadata policy below.

## Normal candidate metadata / legacy migration

Schema version 1 now requires the **explicit JSON boolean** `test_hooks: false`
for normal, noninstrumented candidates and installed-APK evidence. True, missing,
null, numeric zero and strings are invalid, not equivalent to false. The field
comes from inspection of the APK's actual native capability marker, not a Gradle
property. Qualification checks it before reports, soak analysis or readiness;
all predeclared soak budgets, exact evidence bindings and required gates remain
unchanged. Unit controls deliberately use false and are synthetic only.

Legacy v1 JSON without this field has unknown capability and is rejected.
Migration is **fresh binary inspection** with `tools/artifact_metadata.py` of the
exact candidate and installed APK, followed by a new bound qualification report
and rerunning affected gates. Do not insert false into old metadata, rewrite old
reports/evidence, or infer capability from a historical build command. Old APKs
without a recognized marker cannot migrate by metadata editing; rebuild and
inspect a new binary. Hook-enabled DEV DEBUG candidates require the explicit
[schema-2 diagnostic policy](ANDROID-DIAGNOSTIC-POLICY.md), not a v1 reinterpretation.
Hardware/soak qualification remains unverified, with the same APK required.

## Not yet qualified

The separate Android instrumentation/test APK now compiles against the actual
candidate Activity/JniApi/Session. A separate Linux JNI gate exercises real DDS
primary/extra failures while other watches and `/rosout` advance. Robolectric
controller/Activity tests are doubles. None proves Android native execution.
No tablet, ADB/device, LAN, VPN, firewall, physical Wi-Fi/IP recovery, soak or final
candidate acceptance was operated by this implementation. Vendor OS transport
positive-control validation, independent review and physical execution remain
hard gates. See the diagnostic contract for exact scope and reproduction.
