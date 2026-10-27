"""Real catchable signals at filesystem mutation boundaries, not tracer exceptions.

Metadata here is SYNTHETIC protocol input, never Android acceptance evidence.
"""
import json
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import artifact_metadata as metadata


def pair(digit):
    return (dict(sha256=digit * 64, source=dict(snapshot_sha256='b' * 64)),
            dict(target_sha256=digit * 64, target_source_snapshot='b' * 64))


SIGNAL_WORKER = '''import json, signal, sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
import artifact_metadata as metadata
out = Path(sys.argv[2])
side = out.with_name(out.stem + '-androidTest.json')
mode = sys.argv[3]
real = Path.replace
fired = False
def replace(path, target):
    global fired
    if mode in ('rollback', 'rollback-after'):
        if target == out:
            raise OSError('candidate commit fault')
        if mode == 'rollback' and path.name == 'sidecar' and not fired:
            fired = True
            signal.raise_signal(signal.SIGINT)
    result = real(path, target)
    if not fired and ((mode == 'sidecar' and target == side)
                      or (mode == 'candidate' and target == out)
                      or (mode == 'rollback-after' and path.name == 'sidecar')):
        fired = True
        signal.raise_signal(signal.SIGINT)
    return result
Path.replace = replace
metadata.publish_metadata(out, json.loads(sys.argv[4]), json.loads(sys.argv[5]))
'''


class PublicationInterruptionTests(unittest.TestCase):
    def check_signal(self, mode, existing=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'candidate.json'
            old, old_test = pair('a')
            new, new_test = pair('c')
            if existing:
                metadata.publish_metadata(out, old, old_test)
            args = [sys.executable, '-B', '-c', SIGNAL_WORKER, str(ROOT / 'tools'),
                    str(out), mode, json.dumps(new), json.dumps(new_test)]
            result = subprocess.run(args, capture_output=True, text=True, timeout=10)
            self.assertEqual(-signal.SIGINT, result.returncode, result.stderr)
            self.assertIn('KeyboardInterrupt', result.stderr)
            self.assertFalse(list(root.glob('.*.part')))
            if not existing and not out.exists():
                # No previously usable pair: never admit a newly orphaned sidecar.
                with self.assertRaises(OSError):
                    metadata.read_metadata_pair(out)
                print(json.dumps(dict(signal_case=mode, existing=False,
                                      returncode=result.returncode, outcome='absent-blocked')))
                return
            recovered = False
            try:
                candidate, instrumentation = metadata.read_metadata_pair(out)
            except (ValueError, OSError):
                # Existence is not recovery: prove both bindings and actually use
                # the public API to restore the backup matching the survivor.
                backups = list(root.glob('.candidate.json.recovery-*/sidecar'))
                matching = []
                for backup in backups:
                    try:
                        metadata.validate_metadata_pair(json.loads(out.read_text()),
                                                        json.loads(backup.read_text()))
                        matching.append(backup)
                    except ValueError:
                        pass
                self.assertEqual(1, len(matching), 'lost coherent pair and matching recovery backup')
                metadata.recover_metadata_pair(out, matching[0].parent)
                recovered = True
                candidate, instrumentation = metadata.read_metadata_pair(out)
            expected = (new, new_test) if mode == 'candidate' else (old, old_test)
            self.assertEqual(expected, (candidate, instrumentation))
            print(json.dumps(dict(signal_case=mode, existing=existing,
                                  returncode=result.returncode,
                                  outcome='coherent-new' if candidate == new else 'coherent-old',
                                  recovered=recovered), sort_keys=True))

    def test_sigint_after_real_sidecar_rename(self):
        self.check_signal('sidecar')

    def test_sigint_during_rollback_retains_matching_recoverable_backup(self):
        self.check_signal('rollback')

    def test_sigint_after_real_candidate_rename_keeps_new_pair(self):
        self.check_signal('candidate')

    def test_first_publication_sigint_after_sidecar_is_not_admitted(self):
        self.check_signal('sidecar', existing=False)

    def test_first_publication_sigint_after_candidate_keeps_new_pair(self):
        self.check_signal('candidate', existing=False)

    def test_sigint_after_real_rollback_rename_keeps_old_pair(self):
        self.check_signal('rollback-after')

    def test_cleanup_io_failure_always_closes_directory_lock(self):
        import os
        from unittest import mock
        for boundary in ('temporary', 'backup-directory'):
            with self.subTest(boundary=boundary), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                out = root / 'candidate.json'
                metadata.publish_metadata(out, *pair('a'))
                real_open, real_close = os.open, os.close
                real_unlink = Path.unlink
                opened, closed = [], []
                def open_directory(path, flags, *args, **kwargs):
                    fd = real_open(path, flags, *args, **kwargs)
                    if flags & os.O_DIRECTORY:
                        opened.append(fd)
                    return fd
                def close(fd):
                    closed.append(fd)
                    return real_close(fd)
                def unlink(path, *args, **kwargs):
                    if boundary == 'temporary' and path.suffix == '.part':
                        raise OSError('cleanup fault')
                    return real_unlink(path, *args, **kwargs)
                import shutil
                real_rmtree = shutil.rmtree
                def rmtree(path, *args, **kwargs):
                    if boundary == 'backup-directory':
                        raise OSError('cleanup fault')
                    return real_rmtree(path, *args, **kwargs)
                with mock.patch.object(os, 'open', open_directory), \
                        mock.patch.object(os, 'close', close), \
                        mock.patch.object(Path, 'unlink', unlink), \
                        mock.patch.object(shutil, 'rmtree', rmtree):
                    with self.assertRaisesRegex(OSError, 'cleanup fault'):
                        metadata.publish_metadata(out, *pair('c'))
                # Preserve leaked-fd evidence as a failure, but release it so this
                # negative control cannot strand a later test or another process.
                leaked = [fd for fd in opened if fd not in closed]
                for fd in leaked:
                    real_close(fd)
                self.assertEqual([], leaked, 'cleanup failure leaked the directory lock')
                self.assertEqual(pair('c'), metadata.read_metadata_pair(out))
    def test_oserror_after_real_rename_uses_actual_commit_state(self):
        from unittest import mock
        for existing in (False, True):
            for boundary in ('sidecar', 'candidate'):
                with self.subTest(existing=existing, boundary=boundary), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    out = root / 'candidate.json'
                    side = root / 'candidate-androidTest.json'
                    if existing:
                        metadata.publish_metadata(out, *pair('a'))
                    real = Path.replace
                    fired = False
                    def replace(path, target):
                        nonlocal fired
                        result = real(path, target)
                        if not fired and target == (out if boundary == 'candidate' else side):
                            fired = True
                            raise OSError('after real rename')
                        return result
                    with mock.patch.object(Path, 'replace', replace), self.assertRaises(OSError):
                        metadata.publish_metadata(out, *pair('c'))
                    if boundary == 'candidate':
                        self.assertEqual(pair('c'), metadata.read_metadata_pair(out))
                    elif existing:
                        self.assertEqual(pair('a'), metadata.read_metadata_pair(out))
                    else:
                        self.assertFalse(out.exists())
                        self.assertFalse(side.exists())
                    self.assertFalse(list(root.glob('.*.part')))
                    self.assertFalse(list(root.glob('.*.recovery-*')))

    def test_equal_candidate_json_is_not_proof_that_rename_committed(self):
        from unittest import mock
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'candidate.json'
            candidate, old_test = pair('a')
            new_test = dict(old_test, revision='new instrumentation metadata')
            metadata.publish_metadata(out, candidate, old_test)
            real = Path.replace
            def replace(path, target):
                if target == out:
                    raise OSError('precommit error with equal candidate JSON')
                return real(path, target)
            with mock.patch.object(Path, 'replace', replace), self.assertRaises(OSError):
                metadata.publish_metadata(out, candidate, new_test)
            self.assertEqual((candidate, old_test), metadata.read_metadata_pair(out))

    def test_commit_inspection_io_failure_retains_backup_without_guessing(self):
        from unittest import mock
        for commit in (False, True):
            with self.subTest(commit=commit), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                out = root / 'candidate.json'
                metadata.publish_metadata(out, *pair('a'))
                real_replace, real_lstat = Path.replace, Path.lstat
                inspecting = False
                def replace(path, target):
                    nonlocal inspecting
                    if target == out:
                        if commit:
                            real_replace(path, target)
                        inspecting = True
                        raise OSError('candidate rename call failed')
                    return real_replace(path, target)
                def lstat(path, *args, **kwargs):
                    if inspecting and path == out:
                        raise OSError('cannot inspect commit state')
                    return real_lstat(path, *args, **kwargs)
                with mock.patch.object(Path, 'replace', replace), mock.patch.object(Path, 'lstat', lstat):
                    with self.assertRaisesRegex(OSError, 'recovery required'):
                        metadata.publish_metadata(out, *pair('c'))
                backups = list(root.glob('.candidate.json.recovery-*/sidecar'))
                self.assertEqual(1, len(backups))
                self.assertEqual(pair('a')[1], json.loads(backups[0].read_text()))
                if commit:
                    self.assertEqual(pair('c'), metadata.read_metadata_pair(out))
                    with self.assertRaisesRegex(ValueError, 'binding'):
                        metadata.recover_metadata_pair(out, backups[0].parent)
                else:
                    with self.assertRaisesRegex(ValueError, 'binding'):
                        metadata.read_metadata_pair(out)
                    metadata.recover_metadata_pair(out, backups[0].parent)
                    self.assertEqual(pair('a'), metadata.read_metadata_pair(out))

    def test_rollback_and_cleanup_io_faults_retain_actual_recovery_material(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'candidate.json'
            metadata.publish_metadata(out, *pair('a'))
            # A child bounds the negative control too: the rejected baseline
            # leaks its lock on cleanup failure, which must not hang recovery.
            worker = '''import json, sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
import artifact_metadata as metadata
out = Path(sys.argv[2])
real_replace, real_unlink = Path.replace, Path.unlink
def replace(path, target):
    if target == out or path.name == 'sidecar':
        raise OSError('commit/rollback fault')
    return real_replace(path, target)
def unlink(path, *args, **kwargs):
    if path.suffix == '.part':
        raise OSError('cleanup fault')
    return real_unlink(path, *args, **kwargs)
Path.replace, Path.unlink = replace, unlink
metadata.publish_metadata(out, json.loads(sys.argv[3]), json.loads(sys.argv[4]))
'''
            candidate, instrumentation = pair('c')
            result = subprocess.run([sys.executable, '-B', '-c', worker, str(ROOT / 'tools'),
                                     str(out), json.dumps(candidate), json.dumps(instrumentation)],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(1, result.returncode, result.stderr)
            self.assertIn('cleanup fault', result.stderr)
            backup, = root.glob('.candidate.json.recovery-*/sidecar')
            metadata.validate_metadata_pair(json.loads(out.read_text()), json.loads(backup.read_text()))
            metadata.recover_metadata_pair(out, backup.parent)
            self.assertEqual(pair('a'), metadata.read_metadata_pair(out))

    def test_staged_files_and_backup_are_exclusive_private_inodes(self):
        import os
        from unittest import mock
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'candidate.json'
            side = root / 'candidate-androidTest.json'
            metadata.publish_metadata(out, *pair('a'))
            old_inode = side.stat()
            real = Path.replace
            seen = []
            def replace(path, target):
                if path.suffix == '.part':
                    self.assertEqual(0o600, path.stat().st_mode & 0o777)
                    self.assertFalse(path.is_symlink())
                    seen.append(path)
                if target == out:
                    backup, = root.glob('.candidate.json.recovery-*/sidecar')
                    self.assertEqual(0o700, backup.parent.stat().st_mode & 0o777)
                    self.assertTrue(os.path.samestat(old_inode, backup.stat()))
                return real(path, target)
            with mock.patch.object(Path, 'replace', replace):
                metadata.publish_metadata(out, *pair('c'))
            self.assertEqual(2, len(set(seen)))
            self.assertEqual(pair('c'), metadata.read_metadata_pair(out))
            self.assertEqual({out, side}, set(root.iterdir()))


if __name__ == '__main__':
    unittest.main()
