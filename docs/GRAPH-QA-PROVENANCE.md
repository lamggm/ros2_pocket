# Graph QA provenance and Android16 process identity

This fix is scoped to the re-review blockers. It does not approve a candidate,
qualify an Android vendor transport, or establish physical rendering/soak results.

## Producer contract

`PlotController.Frame` captures an immutable `PlotHistory.Source` at native
snapshot intake: slot, native session generation and actual reader generation.
The bounded selected history adopts that source; a source change clears its
points before new samples are appended. Each `Series` snapshot retains its own
source independently of later controller resets/replacements. `qaSeries` exports
that captured source, `seriesId` and `fieldPath`, never the mutable current reader
or slot mapping. `PlotView` therefore preserves provenance in both its prepared
copy and its completed-Canvas JSON, including an old or paused copy.

The source-less portable history APIs retain an explicit unknown identity (-1).
They remain useful for geometry tests, but cannot qualify native graph workload.
No sample capacity, time window, nonfinite/gap policy or pause invalidation budget
was relaxed. Source bookkeeping is bounded to four watches/selected series.

The diagnostic consumer validates exact integer/schema fields in **all** drawn,
prepared and intake series. At an exactly matched draw/prepared revision it
requires the complete same ordered series set and identical drawn/prepared
statistics, and binds each selected series/field/slot/session/reader to intake and
current native QA. Intake may advance after preparation; its newest receive time
cannot precede the drawn copy. Two or more deduplicated integer-time points need
at least `pointCount - 1` nanoseconds of span; one point needs equal endpoints.
Empty/missing copies, omitted series, unknown provenance and stale frames fail
closed. A finite nonempty drawn workload remains required for every selected
series; empty/gap-only selections are not silently removed to pass the gate.

## Android16 parser contract

All supported ProcessRecord references (root 1, 2, 7, 9, 10, 11, 15, 16, 18 and
active instrumentation running references) share one PID/name/UID reconciliation
map. Identical repeated references are legitimate; conflicting identity is not.
Root 1/2 still must supply the unique expected target anchor. Recursive pinned
schema/wire/bounds/default checks and duplicate-instrumentation rejection remain.
Unsupported commands/sections never establish absence. Vendor positive/negative
physical transport controls remain an independent, unperformed gate here.

## Regression evidence boundaries

- `tests/GraphProvenanceExports.java` compiles and executes the actual portable
  Java history/controller/QA exporters with **synthetic native rows**, two genuine
  selected series and replacement session/readers. Copied exports must retain
  their original source identity after both replacements.
- `tests/test_graph_provenance.py` consumes the original exported JSON in the
  real Python graph validator using an explicitly **synthetic draw envelope**.
  The official soak analyzer and CLI are exercised with synthetic OS/native
  campaign controls and a declared receive-clock translation; no physical
  instrumentation, draw or received-rate claim follows from these controls.
- The portable exporter control requires the existing Gradle `org.json` test
  dependency cache; cold portable-only environments explicitly skip it, without
  making a real exporter claim. The validation host executes it without skips.
- `GraphProvenanceViewTest` uses actual Robolectric native Canvas and asserts both
  selected traces reach pixels and prepared/completed copies retain the captured
  session/reader before refresh, including display pause. This is host Android
  evidence, not Android NDK/JNI/device execution.
- Rejected-source REDs retain unchanged behavioral oracles. Compilation/setup
  failures are not counted as behavioral REDs. Run-specific logs, XML, exported
  JSON and hashes remain outside tracked source; review/parent originals remain
  untouched.

The ordinary default/nondebug OFF capability policy, diagnostic ticket lifecycle
and the approved L1–L5 work are outside this patch. Linux direct NativeBridge /
shared-executor JNI evidence, if supplied elsewhere, is not Activity-tick/JNI or
physical Android evidence. Independent final review, combined fixtures, device
qualification and operator acceptance remain required.
