# Artifact metadata publication and evidence admission

This is a local Linux filesystem protocol, not release publication or physical
qualification. Neither a metadata JSON nor a synthetic validator test proves a
build, installation, measurement or operator acceptance.

## Actual candidate bytes

`qualification.py` reinspects the actual candidate ZIP/ELF capability for both
supported schemas on **every passed gate recording**, including host tests and
installed-APK evidence, and in direct structured-evidence validation, soak
analysis and final evaluation. Schema 1 requires an exact ordinary OFF record;
schema 2 requires the explicit diagnostic ON policy and per-ABI native hashes.
Deleting diagnostic JSON fields cannot downgrade an unchanged ON binary.

Pass `--apk` explicitly. The CLI compatibility fallback resolves `metadata.apk`
relative to the metadata JSON directory; direct Python APIs resolve it locally
(as a path relative to the caller's working directory, if not absolute). An
explicit path always wins; a missing/changed explicit path never falls back to
another APK. Missing bytes, hash/size drift, ABI ambiguity and unknown capability
fail closed. Recording additionally retains exact report/metadata snapshot
binding; the public CLI retains current-source provenance comparison. Final
readiness also requires clean source and every separate gate/attestation.

`new_report` is intentionally plan-only: all gates are pending and have no
evidence. Recording pending/failed/blocked status need not establish a usable
binary. A rejected public record does not rewrite the original report. This fix
closes premature **evidence recording**, not a previously demonstrated complete
`ready=true` bypass: final evaluation already rejected the downgraded binary.

## Candidate and named instrumentation sidecar

The names remain `candidate.json` and `candidate-androidTest.json`. Before
publishing a pair, `validate_metadata_pair` requires both
`target_sha256 == candidate.sha256` and
`target_source_snapshot == candidate.source.snapshot_sha256`. The same source
snapshot with different APK bytes is **not** a match.

Publication:

1. Acquire an exclusive advisory `flock` on the parent directory. All cooperating
   publishers in that directory serialize, including across processes.
2. Serialize with the shared JSON bounds (64 containers / 4 MiB per document),
   stage in exclusively created, unpredictable same-directory files, flush and
   fsync each staged file before changing either destination.
3. Preserve the prior sidecar inode using a non-following hard link inside an
   exclusive mode-0700 `.candidate.json.recovery-*` directory. A destination
   symlink is preserved as a symlink, never followed for writing.
4. Replace the sidecar, then replace the candidate as the final commit operation.
   Catchable failures (including `KeyboardInterrupt` from SIGINT) cover **both**
   replacements. A rename can succeed before its Python call raises: compare the
   current destination's non-following device/inode identity with the staged
   candidate, not a flag assigned after return or JSON equality. If committed,
   leave the coherent new pair intact. Otherwise restore the saved sidecar inode
   (or remove the newly created sidecar if none existed), but only if the staged
   sidecar actually replaced its destination. A failed pre-rename operation must
   not trigger an unnecessary restoration. Ordinary failures clean owned temps.
5. Retain the backup by default throughout mutation/rollback; authorize disposal
   only after confirming commit or completing restoration/no-change inspection.
   If commit-state inspection or restoration fails with an I/O error, retain the
   original, report the exact recovery directory and fail. A SIGINT interrupting
   restoration propagates without discarding that backup. Cleanup I/O failure
   also fails the call, but the directory lock is still closed; recovery material
   is never removed merely because an exception is unwinding.

**Two replacements are not atomic as a pair.** `read_metadata_pair(path)` is the
named-sidecar compatibility adapter: it holds a shared directory lock while
loading bounded JSON and checks **both** bindings before returning either
object. It rejects missing members and symlinks. This adapter validates pairing,
not the entire APK, signer, source tree or instrumentation evidence contract;
those consumers must still perform their own full validation and actual-byte
inspection. There is no other in-tree automatic named-sidecar qualification
consumer; merely loading two JSON files by filename is not admission.

Uncoordinated readers can observe a mixed pair during publication and must
validate both bindings themselves; using the adapter avoids that window with
cooperating writers. Advisory locks do not protect against hostile or bypassing
in-place writers. Do not mutate committed metadata in place.

## Interruption and recovery

For one catchable SIGINT after the real sidecar rename, the old pair is restored;
if restoration is itself interrupted before its rename, the backup remains for
explicit recovery. After a real candidate rename, the new pair stays committed
regardless of an exception before the call returns. Interruption after the real
rollback rename leaves the restored old pair usable (an empty recovery directory
may remain). No signal handler is replaced, ignored or deferred; the protocol
also works for cooperating threaded callers under the same directory lock.

Repeated signals can interrupt inspection, restoration or cleanup again; do not
assume automatic completion or automatic removal of leftovers. Preserve and
inspect the exact recovery directory, validate the current pair, then use the
explicit API only with a matching backup. An inspection I/O failure may retain
an unused old backup alongside a coherent new pair; recovery correctly rejects
that old backup. A cleanup failure can leave owned `.part` files or unused backup
directories, not a claim of success. This is not protection against noncooperating
writers changing destination inodes, SIGKILL, arbitrary interpreter failure or
crashes during recovery; process-death leftovers require the checks below.

Process death between sidecar and candidate replacement can leave a mismatched
pair. The reader then fails closed; the old sidecar remains in the recovery
directory. `recover_metadata_pair(candidate_path, exact_recovery_directory)`
locks the directory, checks the saved sidecar against the **current** candidate's
hash and source, and restores it only on a match. It never guesses among backups
or rolls an already committed new candidate back to an old sidecar. A restoration
failure retains the backup. Recovery rejects symlink backups; symlink sentinels
are not trusted metadata. Review and preserve leftovers before manual cleanup.

For a first publication interrupted without any old candidate/sidecar, there is
no prior usable pair to recover: admission remains blocked; regenerate a complete
pair from verified inputs. A crash after candidate replacement may leave a
coherent new pair and an unused old backup; an old backup cannot override it.
Unreferenced `.part` files from process death may be removed only after confirming
no writer owns them. The publisher never blindly reaps recovery directories.

Staged-file fsync does **not** establish power-loss durability for directory
entries. This protocol offers recoverable exception/process-interruption handling
on a local filesystem, not a distributed transaction, power-failure guarantee or
cross-filesystem atomicity. It requires directory `flock` and same-filesystem
hard-link/rename support; unsupported filesystems fail rather than waive checks.

## Regression scope

Tests cover all passed recording branches and direct installed evidence APIs,
missing/changed bytes, explicit-path precedence, unchanged ON/schema-1 downgrade,
ordinary OFF and explicit diagnostic ON controls. Publication tests cover both
staging writes, sidecar commit and actual candidate commit with destinations
present/absent; external symlink sentinels; serialization limits; same-source
but different-byte bindings; process exit at the commit boundary; restoration
failure and explicit recovery; and concurrent cooperating readers/writers across
threads and processes. Real subprocess SIGINT regressions cover post-sidecar,
pre/post-rollback and post-candidate rename, with first-publication controls;
they assert a coherent surviving pair or validate and actually recover the
matching backup using the documented API, not merely count leftover files.
Post-rename OSError, commit-inspection/cleanup I/O errors, identical candidate
JSON, private staging modes and hard-link identity have separate controls.
Synthetic attestations are validator controls only.
