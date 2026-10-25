import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class MetadataTests(unittest.TestCase):
    def module(self):
        path = ROOT / 'tools/artifact_metadata.py'
        self.assertTrue(path.is_file(), 'artifact metadata tool must exist')
        spec = importlib.util.spec_from_file_location('artifact_metadata', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_reads_actual_badging_not_project_defaults(self):
        parsed = self.module().parse_badging("package: name='com.test.dev' versionCode='73' versionName='1.2.3' platformBuildVersionName='15'\nnative-code: 'arm64-v8a'\napplication-debuggable\n")
        self.assertEqual(parsed, {'package': 'com.test.dev', 'version_code': 73,
                                  'version_name': '1.2.3', 'debuggable': True})

    def test_hook_capability_requires_explicit_off_marker(self):
        validate = self.module().validate_hook_capability
        self.assertFalse(validate(b"ELF\0POCKET_TEST_HOOKS=OFF\0"))
        with self.assertRaises(ValueError):
            validate(b"ELF\0POCKET_TEST_HOOKS=ON\0")
        with self.assertRaises(ValueError):
            validate(b"ELF without capability")
    def test_capability_records_require_exact_nul_termination(self):
        validate = self.module().validate_hook_capability
        for diagnostic, marker in ((False, b'OFF'), (True, b'ON')):
            binary = b'\x7fELF\0POCKET_TEST_HOOKS=' + marker + b'\0'
            if diagnostic:
                binary += b'POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0'
            self.assertIs(validate(binary, diagnostic), diagnostic)
            for bad in (binary.replace(marker+b'\0', marker+b'X'),
                        binary+b'POCKET_TEST_HOOKS=',
                        binary+b'POCKET_TEST_HOOKS=\0'):
                with self.subTest(diagnostic=diagnostic, bad=bad), self.assertRaises(ValueError):
                    validate(bad, diagnostic)

    def test_policy_records_include_empty_duplicates_and_unterminated_values(self):
        validate = self.module().validate_hook_capability
        on = b'\x7fELF\0POCKET_TEST_HOOKS=ON\0POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0'
        off = b'\x7fELF\0POCKET_TEST_HOOKS=OFF\0'
        for binary, diagnostic in ((on, True), (off, False)):
            for suffix in (b'POCKET_DIAGNOSTIC_POLICY=\0', b'POCKET_DIAGNOSTIC_POLICY=', b'POCKET_DIAGNOSTIC_POLICY=unknown\0'):
                with self.subTest(diagnostic=diagnostic, suffix=suffix), self.assertRaises(ValueError):
                    validate(binary+suffix, diagnostic)

    def test_diagnostic_policy_is_binary_derived_and_explicit(self):
        validate=self.module().validate_hook_capability
        on=b"\x7fELF\0POCKET_TEST_HOOKS=ON\0POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0"
        self.assertIs(validate(on, diagnostic=True), True)
        for bad in (on+on, on.replace(b'fault-v1',b'fault-v9'), on+b'POCKET_TEST_HOOKS=OFF', on.replace(b'\x7fELF',b'nope')):
            with self.assertRaises(ValueError): validate(bad, diagnostic=True)
        with self.assertRaises(ValueError): validate(on)

    def test_diagnostic_metadata_binds_every_packaged_abi_and_policy(self):
        from unittest import mock
        import tempfile, zipfile, hashlib
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/'diagnostic.apk'
            binary=b'\x7fELF\0POCKET_TEST_HOOKS=ON\0POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0'
            with zipfile.ZipFile(apk,'w') as z:z.writestr('lib/arm64-v8a/librospocket.so',binary)
            responses=["package: name='com.lamggm.rospocket.dev' versionCode='1' versionName='0.1.0'\napplication-debuggable\n",'Signer #1 certificate DN: CN=Android Debug\nSigner #1 certificate SHA-256 digest: '+'ab'*32+'\n']
            with mock.patch.object(m,'output',side_effect=responses),mock.patch.object(m,'source_provenance',return_value={}):
                data=m.collect_metadata(apk,ROOT,'aapt','signer',diagnostic=True)
            self.assertEqual(2,data['schema_version']);self.assertIs(True,data['test_hooks'])
            self.assertEqual('ros-pocket-dds-fault-v1',data['diagnostic_policy'])
            self.assertEqual(hashlib.sha256(binary).hexdigest(),data['native_libraries'][0]['sha256'])
            with zipfile.ZipFile(apk,'a') as z:z.writestr('lib/x86_64/libddsc.so',b'other abi with no inspector')
            with mock.patch.object(m,'output',side_effect=responses),mock.patch.object(m,'source_provenance',return_value={}):
                with self.assertRaises(ValueError):m.collect_metadata(apk,ROOT,'aapt','signer',diagnostic=True)

    def test_test_apk_target_signer_and_distinct_bytes_are_verified(self):
        import tempfile,zipfile
        from unittest import mock
        m=self.module();candidate=dict(schema_version=2,sha256='a'*64,package='com.lamggm.rospocket.dev',source=dict(snapshot_sha256='b'*64),signing=dict(certificate_sha256='c'*64))
        tree='''E: manifest
  A: package="com.lamggm.rospocket.dev.test"
  E: instrumentation
    A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.lamggm.rospocket.ReaderFailureInstrumentation"
    A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)="com.lamggm.rospocket.dev"
  E: application
'''
        signer='Signer #1 certificate DN: CN=Android Debug\nSigner #1 certificate SHA-256 digest: '+'c'*64+'\n'
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/'test.apk'
            with zipfile.ZipFile(apk,'w') as z:z.writestr('classes.dex',b'TEST ONLY')
            with mock.patch.object(m,'output',side_effect=[tree,signer]):result=m.collect_test_metadata(apk,candidate,'aapt','signer')
            self.assertEqual(candidate['sha256'],result['target_sha256']);self.assertNotEqual(result['sha256'],result['target_sha256'])
            for wrong in (tree.replace('targetPackage','unknownTarget'),tree.replace('com.lamggm.rospocket.dev"','other.package"'),tree+tree):
                with mock.patch.object(m,'output',side_effect=[wrong,signer]),self.assertRaises(ValueError):m.collect_test_metadata(apk,candidate,'aapt','signer')
            with mock.patch.object(m,'output',side_effect=[tree,signer.replace('c'*64,'d'*64)]),self.assertRaises(ValueError):m.collect_test_metadata(apk,candidate,'aapt','signer')

    def test_atomic_metadata_cli_replaces_symlinks_without_touching_sentinels(self):
        import tempfile, zipfile, subprocess, sys, json
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); apk=root/'candidate.apk'; test_apk=root/'test.apk'
            with zipfile.ZipFile(apk,'w') as z:
                z.writestr('lib/arm64-v8a/librospocket.so', b'\x7fELF\0POCKET_TEST_HOOKS=ON\0POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0')
            with zipfile.ZipFile(test_apk,'w') as z:z.writestr('classes.dex',b'IO TEST ONLY')
            aapt=root/'aapt'; signer=root/'signer'
            tree='E: manifest\n  A: package="com.lamggm.rospocket.dev.test"\n  E: instrumentation\n    A: android:name(0x01010003)="com.lamggm.rospocket.ReaderFailureInstrumentation"\n    A: android:targetPackage(0x01010021)="com.lamggm.rospocket.dev"\n  E: application\n'
            badging="package: name='com.lamggm.rospocket.dev' versionCode='1' versionName='0.1.0'\napplication-debuggable\n"
            aapt.write_text('#!'+sys.executable+'\nimport sys\nprint('+repr(badging)+' if "badging" in sys.argv else '+repr(tree)+')\n')
            signer.write_text('#!'+sys.executable+'\nprint('+repr('Signer #1 certificate DN: CN=Android Debug\nSigner #1 certificate SHA-256 digest: '+'b'*64)+')\n')
            for p in (aapt,signer):p.chmod(0o700)
            output=root/'metadata.json'; sidecar=root/'metadata-androidTest.json'; sentinel=root/'sentinel'
            sentinel.write_bytes(b'UNCHANGED'); sidecar.symlink_to(sentinel)
            predictable=root/'metadata.json.part'; predictable.symlink_to(sentinel)
            args=[sys.executable,str(ROOT/'tools/artifact_metadata.py'),'--repo',str(ROOT),'--apk',str(apk),'--test-apk',str(test_apk),'--diagnostic','--output',str(output),'--aapt2',str(aapt),'--apksigner',str(signer)]
            result=subprocess.run(args,capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            self.assertEqual(b'UNCHANGED',sentinel.read_bytes())
            self.assertFalse(sidecar.is_symlink())
            self.assertEqual(json.loads(output.read_text())['sha256'],json.loads(sidecar.read_text())['target_sha256'])
            original=output.read_bytes(); sidecar.unlink(); sidecar.mkdir()
            result=subprocess.run(args,capture_output=True,text=True)
            self.assertNotEqual(0,result.returncode)
            self.assertEqual(original,output.read_bytes())
            self.assertEqual(b'UNCHANGED',sentinel.read_bytes())
            self.assertEqual([],list(root.glob('.*.part')))

    def test_staging_and_candidate_commit_failure_preserve_both_outputs(self):
        import tempfile,json
        from unittest import mock
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);output=root/'metadata.json';sidecar=root/'metadata-androidTest.json'
            old=dict(sha256='a'*64,source=dict(snapshot_sha256='b'*64))
            old_test=dict(target_sha256=old['sha256'],target_source_snapshot=old['source']['snapshot_sha256'])
            m.publish_metadata(output,old,old_test)
            before=(output.read_bytes(),sidecar.read_bytes())
            with mock.patch.object(m.os,'fsync',side_effect=OSError('injected staging failure')):
                with self.assertRaises(OSError):m.publish_metadata(output,old,old_test)
            self.assertEqual(before,(output.read_bytes(),sidecar.read_bytes()))
            real_replace=Path.replace
            def fail_primary(path,target):
                if target==output:raise OSError('injected candidate commit failure')
                return real_replace(path,target)
            new=dict(sha256='c'*64,source=dict(snapshot_sha256='d'*64))
            new_test=dict(target_sha256=new['sha256'],target_source_snapshot=new['source']['snapshot_sha256'])
            with mock.patch.object(Path,'replace',fail_primary):
                with self.assertRaises(OSError):m.publish_metadata(output,new,new_test)
            self.assertEqual(before,(output.read_bytes(),sidecar.read_bytes()))
            self.assertEqual([],list(root.glob('.*.part')))

    def test_publication_rejects_unbound_sidecars_without_changes(self):
        import tempfile
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory)/'metadata.json'
            old=dict(sha256='a'*64,source=dict(snapshot_sha256='b'*64))
            bound=dict(target_sha256=old['sha256'],target_source_snapshot=old['source']['snapshot_sha256'])
            m.publish_metadata(output,old,bound)
            side=output.with_name('metadata-androidTest.json');before=(output.read_bytes(),side.read_bytes())
            for field in ('target_sha256','target_source_snapshot'):
                bad=dict(bound);bad[field]='c'*64
                with self.subTest(field=field),self.assertRaisesRegex(ValueError,'binding'):
                    m.publish_metadata(output,old,bad)
                self.assertEqual(before,(output.read_bytes(),side.read_bytes()))

    def test_failure_boundaries_preserve_existing_or_absent_outputs(self):
        import tempfile
        from unittest import mock
        m=self.module()
        old=dict(sha256='a'*64,source=dict(snapshot_sha256='b'*64))
        new=dict(sha256='c'*64,source=dict(snapshot_sha256='b'*64))
        def bound(candidate):return dict(target_sha256=candidate['sha256'],target_source_snapshot=candidate['source']['snapshot_sha256'])
        for candidate_exists in (False,True):
            for side_exists in (False,True):
                for boundary in ('first-stage','second-stage','sidecar','candidate'):
                    with tempfile.TemporaryDirectory() as directory,self.subTest(candidate=candidate_exists,side=side_exists,boundary=boundary):
                        root=Path(directory);out=root/'metadata.json';side=root/'metadata-androidTest.json'
                        if candidate_exists:out.write_text(__import__('json').dumps(old))
                        if side_exists:side.write_text(__import__('json').dumps(bound(old)))
                        before={p.name:p.read_bytes() for p in root.iterdir()}
                        real_replace=Path.replace;real_fsync=m.os.fsync;calls=0
                        def replace(path,target):
                            if target==(side if boundary=='sidecar' else out):raise OSError('injected commit')
                            return real_replace(path,target)
                        def fsync(fd):
                            nonlocal calls
                            calls+=1
                            if calls==(1 if boundary=='first-stage' else 2):raise OSError('injected stage')
                            return real_fsync(fd)
                        with mock.patch.object(Path,'replace',replace if boundary in ('sidecar','candidate') else real_replace),mock.patch.object(m.os,'fsync',fsync if boundary.endswith('stage') else real_fsync):
                            with self.assertRaises(OSError):m.publish_metadata(out,new,bound(new))
                        self.assertEqual(before,{p.name:p.read_bytes() for p in root.iterdir()})

    def test_commit_error_restores_symlink_inode_without_touching_sentinel(self):
        import tempfile,os
        from unittest import mock
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'metadata.json';side=root/'metadata-androidTest.json';sentinel=root/'sentinel'
            sentinel.write_bytes(b'EXTERNAL SENTINEL');out.symlink_to(sentinel);side.symlink_to(sentinel)
            candidate=dict(sha256='c'*64,source=dict(snapshot_sha256='b'*64))
            test=dict(target_sha256='c'*64,target_source_snapshot='b'*64)
            real_replace=Path.replace
            def replace(path,target):
                if target==out:raise OSError('candidate commit')
                return real_replace(path,target)
            with mock.patch.object(Path,'replace',replace),self.assertRaises(OSError):m.publish_metadata(out,candidate,test)
            self.assertTrue(out.is_symlink());self.assertTrue(side.is_symlink())
            self.assertEqual(str(sentinel),os.readlink(side));self.assertEqual(b'EXTERNAL SENTINEL',sentinel.read_bytes())
            self.assertEqual({out,side,sentinel},set(root.iterdir()))

    def test_publication_bounds_reject_before_replacing(self):
        import tempfile
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'metadata.json';out.write_bytes(b'ORIGINAL')
            nested=0
            for _ in range(65):nested=[nested]
            for document in ({'huge':'x'*(4*1024*1024)},nested):
                with self.subTest(kind=type(document).__name__),self.assertRaisesRegex(ValueError,'JSON'):
                    m.publish_metadata(out,document)
                self.assertEqual(b'ORIGINAL',out.read_bytes());self.assertEqual({out},set(root.iterdir()))

    def test_named_sidecar_adapter_rejects_same_source_different_bytes_and_missing_members(self):
        import tempfile,json
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory)/'candidate.json';side=out.with_name('candidate-androidTest.json')
            candidate=dict(sha256='a'*64,source=dict(snapshot_sha256='b'*64))
            bound=dict(target_sha256='a'*64,target_source_snapshot='b'*64)
            m.publish_metadata(out,candidate,bound)
            self.assertEqual((candidate,bound),m.read_metadata_pair(out))
            for field in ('target_sha256','target_source_snapshot'):
                bad=dict(bound);bad[field]='c'*64;side.write_text(json.dumps(bad))
                with self.subTest(field=field),self.assertRaisesRegex(ValueError,'binding'):m.read_metadata_pair(out)
            side.unlink()
            with self.assertRaises(OSError):m.read_metadata_pair(out)
            side.write_text(json.dumps(bound));out.unlink()
            with self.assertRaises(OSError):m.read_metadata_pair(out)

    def test_interrupted_publication_fails_closed_and_retained_backup_recovers(self):
        import tempfile,json,subprocess,sys
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'metadata.json';side=root/'metadata-androidTest.json'
            old=dict(sha256='a'*64,source=dict(snapshot_sha256='b'*64));old_test=dict(target_sha256='a'*64,target_source_snapshot='b'*64)
            new=dict(sha256='c'*64,source=dict(snapshot_sha256='b'*64));new_test=dict(target_sha256='c'*64,target_source_snapshot='b'*64)
            m.publish_metadata(out,old,old_test);before=(out.read_bytes(),side.read_bytes())
            script='''import sys,os,json
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import artifact_metadata as m
out=Path(sys.argv[2]);replace=Path.replace
def interrupted(path,target):
    if target==out:os._exit(73)
    return replace(path,target)
Path.replace=interrupted
m.publish_metadata(out,json.loads(sys.argv[3]),json.loads(sys.argv[4]))
'''
            result=subprocess.run([sys.executable,'-c',script,str(ROOT/'tools'),str(out),json.dumps(new),json.dumps(new_test)],capture_output=True,text=True,timeout=10)
            self.assertEqual(73,result.returncode,result.stderr)
            self.assertEqual(before[0],out.read_bytes())
            with self.assertRaisesRegex(ValueError,'binding'):m.read_metadata_pair(out)
            backups=list(root.glob('.metadata.json.recovery-*'));self.assertEqual(1,len(backups))
            self.assertEqual(before[1],(backups[0]/'sidecar').read_bytes())
            m.recover_metadata_pair(out,backups[0])
            self.assertEqual((old,old_test),m.read_metadata_pair(out));self.assertEqual(before,(out.read_bytes(),side.read_bytes()))
            self.assertFalse(backups[0].exists())
            # An already-committed new candidate must never adopt an old backup.
            recovery=root/'.metadata.json.recovery-synthetic';recovery.mkdir();(recovery/'sidecar').write_bytes(before[1])
            m.publish_metadata(out,new,new_test)
            with self.assertRaisesRegex(ValueError,'binding'):m.recover_metadata_pair(out,recovery)
            self.assertEqual((new,new_test),m.read_metadata_pair(out));self.assertTrue((recovery/'sidecar').exists())

    def test_rollback_failure_retains_original_for_explicit_recovery(self):
        import tempfile
        from unittest import mock
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'metadata.json';side=root/'metadata-androidTest.json'
            old=dict(sha256='a'*64,source=dict(snapshot_sha256='b'*64));old_test=dict(target_sha256='a'*64,target_source_snapshot='b'*64)
            new=dict(sha256='c'*64,source=dict(snapshot_sha256='b'*64));new_test=dict(target_sha256='c'*64,target_source_snapshot='b'*64)
            m.publish_metadata(out,old,old_test);before=(out.read_bytes(),side.read_bytes());replace=Path.replace
            def fail(path,target):
                if target==out or path.name=='sidecar':raise OSError('injected commit/recovery failure')
                return replace(path,target)
            with mock.patch.object(Path,'replace',fail),self.assertRaisesRegex(OSError,'recovery required'):
                m.publish_metadata(out,new,new_test)
            self.assertEqual(before[0],out.read_bytes());backups=list(root.glob('.metadata.json.recovery-*'))
            self.assertEqual(1,len(backups));self.assertEqual(before[1],(backups[0]/'sidecar').read_bytes())
            with self.assertRaisesRegex(ValueError,'binding'):m.read_metadata_pair(out)
            m.recover_metadata_pair(out,backups[0]);self.assertEqual(before,(out.read_bytes(),side.read_bytes()))
            self.assertFalse(list(root.glob('.*.part')))

    def test_cooperating_concurrent_readers_and_publishers_only_admit_bound_pairs(self):
        import tempfile,json,subprocess,sys
        from concurrent.futures import ThreadPoolExecutor
        m=self.module()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'metadata.json'
            def publish(digit):
                candidate=dict(sha256=digit*64,source=dict(snapshot_sha256='b'*64))
                m.publish_metadata(out,candidate,dict(target_sha256=digit*64,target_source_snapshot='b'*64))
            publish('a')
            script='''import sys
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import artifact_metadata as m
out=Path(sys.argv[2])
for i in range(30):
    h=('d' if i%2 else 'e')*64
    m.publish_metadata(out,dict(sha256=h,source=dict(snapshot_sha256='b'*64)),dict(target_sha256=h,target_source_snapshot='b'*64))
'''
            process=subprocess.Popen([sys.executable,'-c',script,str(ROOT/'tools'),str(out)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            try:
                def writer():
                    for i in range(30):publish('a' if i%2 else 'c')
                def reader():
                    for _ in range(100):
                        candidate,test=m.read_metadata_pair(out)
                        self.assertEqual(candidate['sha256'],test['target_sha256'])
                        self.assertEqual(candidate['source']['snapshot_sha256'],test['target_source_snapshot'])
                with ThreadPoolExecutor(max_workers=3) as pool:
                    futures=[pool.submit(writer),pool.submit(reader),pool.submit(reader)]
                    for future in futures:future.result(timeout=20)
                stdout,stderr=process.communicate(timeout=20);self.assertEqual(0,process.returncode,stdout+stderr)
            finally:
                if process.poll() is None:process.kill();process.communicate(timeout=5)
            self.assertEqual({out,out.with_name('metadata-androidTest.json')},set(root.iterdir()))

    def test_inspects_zip_signature_hash_and_dirty_source(self):
        import hashlib
        import tempfile
        import zipfile
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            apk = directory / 'actual.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/arm64-v8a/librospocket.so', b'test-only-ELF\0POCKET_TEST_HOOKS=OFF\0')
                archive.writestr('lib/arm64-v8a/libddsc.so', b'test-only-DDS')
            aapt = directory / 'aapt2'
            aapt.write_text('#!/bin/sh\ncat <<EOF\npackage: name=\'test.dev\' versionCode=\'7\' versionName=\'changed\'\napplication-debuggable\nEOF\n')
            signer = directory / 'apksigner'
            signer.write_text('#!/bin/sh\ncat <<EOF\nSigner #1 certificate DN: C=US, O=Android, CN=Android Debug\nSigner #1 certificate SHA-256 digest: ' + 'ab' * 32 + '\nEOF\n')
            for path in (aapt, signer):
                path.chmod(0o755)
            result = self.module().collect_metadata(apk, ROOT, aapt, signer)
            self.assertEqual(result['package'], 'test.dev')
            self.assertEqual(result['version_name'], 'changed')
            self.assertEqual(result['abis'], ['arm64-v8a'])
            self.assertFalse(result['test_hooks'])
            self.assertEqual(result['sha256'], hashlib.sha256(apk.read_bytes()).hexdigest())
            self.assertEqual(result['signing']['certificate_sha256'], 'ab' * 32)
            self.assertEqual(result['signing']['class'], 'debug')
            self.assertEqual(result['source']['head_sha'], __import__('subprocess').check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip())
            expected_dirty = bool(__import__('subprocess').check_output(['git', 'status', '--porcelain', '--untracked-files=all'], cwd=ROOT))
            self.assertEqual(result['source']['dirty'], expected_dirty)
            self.assertEqual(len(result['source']['snapshot_sha256']), 64)
            destination = directory / 'metadata.json'
            cli = __import__('subprocess').run([
                __import__('sys').executable, str(ROOT / 'tools/artifact_metadata.py'),
                '--apk', str(apk), '--output', str(destination), '--repo', str(ROOT),
                '--aapt2', str(aapt), '--apksigner', str(signer)], capture_output=True, text=True)
            self.assertEqual(cli.returncode, 0, cli.stderr)
            self.assertTrue(destination.is_file(), 'CLI must write metadata JSON')
            self.assertEqual(__import__('json').loads(destination.read_text()), result)
            expected = directory / 'source.json'
            source_cli = __import__('subprocess').run([
                __import__('sys').executable, str(ROOT / 'tools/artifact_metadata.py'),
                '--repo', str(ROOT), '--source'], capture_output=True, text=True)
            self.assertEqual(source_cli.returncode, 0, source_cli.stderr)
            self.assertEqual(__import__('json').loads(source_cli.stdout), result['source'])
            expected.write_text('{}')
            rejected = __import__('subprocess').run([
                __import__('sys').executable, str(ROOT / 'tools/artifact_metadata.py'),
                '--apk', str(apk), '--output', str(destination), '--repo', str(ROOT),
                '--aapt2', str(aapt), '--apksigner', str(signer),
                '--expected-source', str(expected)], capture_output=True, text=True)
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn('Sources changed', rejected.stderr)
            self.assertEqual(__import__('json').loads(destination.read_text()), result)

if __name__ == '__main__':
    unittest.main()
