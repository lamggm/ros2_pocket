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

    def test_inspects_zip_signature_hash_and_dirty_source(self):
        import hashlib
        import tempfile
        import zipfile
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            apk = directory / 'actual.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/arm64-v8a/librospocket.so', b'test-only-ELF')
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
