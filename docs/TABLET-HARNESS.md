# Scoped physical-tablet harness

This is functional bench automation, **not final v0.2 qualification**. The Android
application remains a read-only DDS inspector. Only isolated synthetic bench
publishers may be started/stopped by the regression script.

## Prerequisites and scope

- Read `AGENTS.md`. Use one operator and the authorized DEV tablet only.
- Discover the current ADB transport on the build/bench host and independently
  confirm the physical device. Set `POCKET_TABLET` explicitly; no default device.
- Independently verify the physical serial, expected model, DEV label, installed
  APK/certificate and package UID in the operator preflight. The generic helper
  checks transport, a nonempty model, user 0 and exact package presence; it does
  not by itself establish all those manual identity bindings. Ordinary focus
  checks match package/user; the separate popup path verifies its owner UID.
  Lifecycle commands explicitly use `--user 0` and recheck the current user.
- Secure keyguard, foreign focus, unknown popup ownership, ambiguous targets and
  transport failures block the harness. Unlock manually. Never dismiss a secure
  keyguard or interact with VPN, Settings, notifications or unrelated apps.
- Keep VPN/Tailscale and host networking policy unchanged. These tests do not
  toggle Wi-Fi or change the tablet IP. Such tests require separately verified
  independent recovery and explicit disruption approval.
- Reserve domains 67/68 for this synthetic bench. Never use a robot domain.
- Provide an explicit, already-installed trusted ROS Jazzy image containing
  rclpy/std_msgs and both RMWs. No implicit image pull is performed.

```bash
# Values must come from your own verified bench, not a copied private endpoint.
export ADB=/path/to/android-sdk/platform-tools/adb
export POCKET_TABLET="$AUTHORIZED_ADB_TRANSPORT"
export POCKET_ROS_IMAGE="$VERIFIED_LOCAL_JAZZY_IMAGE"
# Optional: verified physical interface for only the owned Cyclone publisher.
export POCKET_FIXTURE_INTERFACE="$VERIFIED_LAN_INTERFACE"
# Optional: use the existing app peer setting if multicast discovery is unavailable.
# Leave unset/empty for automatic discovery; no private address is a source default.
export POCKET_BENCH_PEER="${VERIFIED_PEER_IPV4:-}"
python3 tests/tablet_regression.py
```

The report records the selected `connectionConfig` (automatic or explicit IPv4
peer). Explicit-peer success does **not** qualify automatic multicast discovery.
The optional peer is validated before device interaction and uses the existing
app field; message/QoS/ownership oracles and VPN/network policy are unchanged.

Do not precreate a fixed-name container for this command. It creates a unique
run-scoped fixture, applies resource/deadline limits, mounts the publisher script
read-only and labels ownership. String/Float64 topics are ROS-remapped under
`/pocket_test/harness_<run-nonce>/`; the node name also includes the nonce.
The generic publisher source and the isolated CI fixture are not rewritten.
Cleanup verifies owner, mounted source and domain, resolves an immutable container
ID, then stops and confirms removal of that ID. An inspection/daemon failure is
not proof of removal. App disconnect is attempted independently even if fixture
cleanup fails, then confirmed through bounded fresh button/status observations.
An ignored tap or unknown button state is a cleanup failure; errors are retained.

## Hierarchy handling and interaction

Each capture requires fresh successful UIAutomator output and app-focus checks
before/after capture. Raw XML is transiently spooled inside a new shell-private
`/data/local/tmp/ros-pocket-<nonce>/` directory with mode 0700, never shared external
storage. The file and directory are removed in cleanup; cleanup failure prevents
a successful capture. Empty/malformed/oversized/stale output is not reused.

A hierarchy containing any foreign-package node is rejected as a whole. No
foreign node text is copied into the retained hierarchy or diagnostic error.
Only admitted app-owned nodes are persisted locally. This is guarded UIAutomator,
**not a hermetic package-only accessibility collector**: capture/focus cannot be
made atomic against an external user/window switch. Do not claim that foreign
bytes can never enter the transient private buffer. Stop if the surface changes.

## Bounded fresh recapture after proven postwrite exit 137

This is an explicit **capture recovery policy**, not a root-cause fix, a transport
retry policy, or permission to ignore exit 137. The underlying intermittent
Samsung/UIAutomator termination cause remains **unknown**. Completion output
alone does not make a failed capture acceptable; prior failed physical invocations
remain failed and are not retroactively qualified by this policy.

Only the actual subprocess-result boundary can classify the special failure:
positive return code `137` (not negative `SIGKILL`), empty stderr, the exact argv
`[ADB, "-s", serial, "shell", "uiautomator", "dump", path]`, and exact stdout
`UI hierchary dumped to: <path>\n` (including the tool's spelling and newline).
The configured ADB, explicit valid serial, and that command's own path must match;
the path must match `/data/local/tmp/ros-pocket-[0-9a-f]{32}/hierarchy.xml`.
No prefixes, suffixes, alternate paths or additional command arguments qualify.
Nonzero stdout is never returned as success or included in exception/user logs.
Injected host transports remain explicitly `synthetic_transport_test` evidence.

The failed attempt stays `status: failed`, with `failureClassification:
postwrite_uiautomator_137` and `exitCode: 137`. Its XML is **never read, parsed,
retained, or used for acceptance**, even if complete. First remove its private
file and directory and persist its failed metadata. Only then may `Client.dump`
start **at most one** separate capture in a distinct new UUID-private directory;
a repeated directory UUID fails closed. The new record links `recoveryOf` to the
failed attempt without editing that earlier record. Cleanup failure sets
`cleanupFailed`, preserves the original error and prevents recapture.

Recovery must independently exit zero, emit the exact complete confirmation for
its new path, supply fresh valid bounded nonempty XML containing only the target
package, pass the ordinary pre-capture/post-read focus guards, and complete cleanup.
`allow_popup` stays explicit at every focus check and never grants foreign popup
ownership. Freshness is the new private path plus successful explicit capture,
not acceptance of cached XML or a timestamp inferred from its contents. A second
137 is terminal; there is no third capture. Foreign focus, mixed packages,
malformed/empty/missing/stale XML, timeouts, other errors, other-command 137 and
arbitrary `RuntimeError` messages never trigger recovery. QoS/ownership, peer,
serial and lifecycle policies are unchanged. Host-only tests cannot establish
physical recovery reliability or final qualification; independent review after
import and separately authorized physical validation remain necessary.

Selectors prefer resource ID, then content description, then text. ROS identifiers
retain exact case. AllCaps button cosmetics do not change topic identity. Hide
and verify a shown IME with at most one Back before gestures; await dismissal
without a blind second Back. Empty peer/Explorer fields accept only blank text
or their exact known application hint-as-text representation, not arbitrary
nonempty readback. Navigation uses fresh observed ScrollView bounds,
overlapping bounded swipes and a separate budget from data observation. Counter
observations after the message search upward; post-reconnect/relaunch topic rows
search downward, and QoS restoration searches upward from counters. Failure to
reach a target is `harness_blocked`, not a product reception timeout.

## Oracles and evidence

- Compare the entire Wi-Fi IPv4 address, never a substring/prefix.
- Parse complete state/counter tokens: `DESCONECTADO` is not `CONECTADO`, and
  ten compatible publishers is not zero.
- Require a new increasing String sequence after Home, RMW replacement, QoS
  restoration and process restart; a cached `RECEBENDO` label is insufficient.
- The incompatible-QoS negative control is not a one-frame `AGUARDANDO` pass:
  read back the Reliable checkbox and current applied QA request, then bind the
  exact advertised `best_effort` writer GID/node to the owned fixture's GID.
  Require zero cumulative intake/matches on the same PID/session/request/reader,
  fresh advancing QA captures spanning >=5 s in the Android clock.
- Read fixture history BEFORE the QA export. Once a >=5 s producer interval
  qualifies, freeze that interval and remember the following QA export time.
  Pass only on another coherent zero-intake worker capture strictly newer than
  that export, with the same reader identity and >=5 s of QA capture span.
  An export of a newer-looking but pre-fence cached capture cannot close the
  interval. This ordered closing fence covers slow Docker/ADB reads without
  comparing Java timestamps directly to host timestamps.
- Only the owned best-effort bench fixture opts into `POCKET_QOS_HEARTBEAT`.
  After each completed String publication it logs sequence, Linux monotonic
  timestamp and writer identity. Read a bounded 128-event history through the
  verified immutable container ID; reject missing events, regressions, foreign
  identity, stale/future timestamps and any actual publication gap >1.5 s in
  the observed window. Require >=5 s of real publication events, not merely
  five seconds of host polling. Fixture and harness share the Linux host clock;
  Android `java.nanoTime` is used only for advancing captures, never compared
  to host time. This controlled-bench provenance is not DDS authentication.
- Missing/malformed QA decision fields invalidate/reset the observation. Never
  coerce booleans into integer counts or stitch an interval across a reader reset.
- Float64 resumption requires finite numerical advancement, not cosmetic text
  changes. Host Float64 equivalence and exact signed-Int64 helpers are separately
  tested; this basic physical script does not qualify Int64 in every watch.
- Wrong-domain acceptance observes an explicit empty filtered fixture list in
  fresh hierarchies, including a second observation after the bounded interval.
  Absence from a viewport alone is not proof of isolation.
- `tablet_accept.py` is only the standalone LAN-address/discovery check. It needs
  the app already connected and a separately controlled generic chatter fixture;
  it neither starts that publisher nor establishes final device acceptance.
- QA v1 decoding is metadata parsing only. `valid` does not mean live, rendered,
  disarmed, or soak-ready. See `DEVICE-QA-CONTRACT.md`.

Host doubles are explicitly synthetic and never physical evidence. Run them with:

```bash
python3 -m unittest discover -s tests -p test_tablet_harness.py -v
```

The existing portable/build and public CI commands discover `test_*.py`, including
these negative-control tests, without an ADB device or Docker bench operation.
Retain each failed/incomplete device invocation separately. Results and dumps
remain under ignored `build/`; they are not public CI artifacts. Bind a physical
invocation to its actual installed APK/certificate, harness source hashes and
preflight state before using it as qualification evidence. A dirty harness, older
APK, short functional run or synthetic control cannot release a different
candidate, satisfy the 30-minute soak, or imply operator acceptance.
