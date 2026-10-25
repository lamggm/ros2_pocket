"""Actual offline unsigned APK artifact guard integration; never uses a device.
Run explicitly, inside a private network namespace with the documented JDK/SDK.
This is not part of fast Python discovery. Mutates only ignored build libraries,
restores them in finally, and stores each actual Gradle log in --evidence.
"""
import argparse
from pathlib import Path
import subprocess
import hashlib
import json
import os
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--resources-only', action='store_true')
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)

    def gradle(label, *tasks, success=True):
        command = ['./gradlew', '--offline', '--no-daemon', '--console=plain', *tasks]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=240)
        (args.evidence / (label + '.log')).write_text(result.stdout + result.stderr)
        assert (result.returncode == 0) is success, (label, result.returncode, result.stdout[-5000:] + result.stderr[-5000:])
        return result.stdout + result.stderr

    gradle('resources', ':app:packageReleaseResources')
    if args.resources_only:
        return
    library = ROOT / 'app/src/main/jniLibs/arm64-v8a/librospocket.so'
    original = library.read_bytes()
    assert original.count(b'POCKET_TEST_HOOKS=OFF') == 1
    cmake_dir = ROOT / 'build/native-android'

    def build_native(hooks):
        command = ['cmake', '-S', 'app/src/main/cpp', '-B', str(cmake_dir),
                   '-DPOCKET_ENABLE_TEST_HOOKS=' + hooks]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        build = subprocess.run(['cmake', '--build', str(cmake_dir), '--parallel', '4'],
                               cwd=ROOT, capture_output=True, text=True)
        (args.evidence / ('cmake-' + hooks + '-' + str(len(list(args.evidence.glob('cmake-*')))) + '.log')).write_text(result.stdout + result.stderr + build.stdout + build.stderr)
        assert build.returncode == 0, build.stderr
        data = (cmake_dir / 'librospocket.so').read_bytes()
        assert data.count(('POCKET_TEST_HOOKS=' + hooks).encode()) == 1
        return data

    def inspect(label, expected):
        directory = ROOT / 'app/build/outputs/apk/release'
        metadata = json.loads((directory / 'output-metadata.json').read_text())
        assert metadata['elements']
        for element in metadata['elements']:
            apk = directory / element['outputFile']
            with zipfile.ZipFile(apk) as archive:
                libs = [name for name in archive.namelist() if name.endswith('/librospocket.so')]
                assert libs == ['lib/arm64-v8a/librospocket.so']
                assert archive.read(libs[0]) == expected
            shutil.copy2(apk, args.evidence / (label + '.apk'))
            (args.evidence / (label + '.json')).write_text(json.dumps(dict(
                apk=str(apk), sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                native_sha256=hashlib.sha256(expected).hexdigest(),
                test_only=True, unsigned=True), indent=2))
            signer = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner'
            result = subprocess.run([str(signer), 'verify', str(apk)], capture_output=True, text=True)
            assert result.returncode != 0, 'Release fixture must remain unsigned'

    try:
        off = build_native('OFF')
        library.write_bytes(off)
        log = gradle('off-assemble', ':app:assembleRelease')
        assert 'Verified native hooks OFF:' in log
        inspect('off', off)
        log = gradle('off-up-to-date', ':app:packageRelease')
        assert ':app:packageRelease UP-TO-DATE' in log
        assert 'Verified native hooks OFF:' in log, 'UP-TO-DATE must not bypass guard'
        on = build_native('ON')
        library.write_bytes(on)
        log = gradle('on-package', ':app:packageRelease', success=False)
        assert 'Native test-hook capability enabled or unknown' in log
        inspect('on-rejected', on)
        # APK guard must not intercept unrelated Android resource/bundle tasks.
        gradle('on-resources-bundle', ':app:packageReleaseResources', ':app:bundleRelease')
        unknown = off.replace(b'POCKET_TEST_HOOKS=OFF', b'POCKET_TEST_HOOKS=???')
        library.write_bytes(unknown)
        log = gradle('unknown-package', ':app:packageRelease', success=False)
        assert 'Native test-hook capability enabled or unknown' in log
        inspect('unknown-rejected', unknown)
        library.unlink()
        log = gradle('missing-package', ':app:packageRelease', success=False)
        assert 'Native test-hook capability enabled or unknown' in log
        with zipfile.ZipFile(ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk') as archive:
            assert not any(name.endswith('/librospocket.so') for name in archive.namelist())
        final_off = build_native('OFF')
        library.write_bytes(final_off)
        log = gradle('restored-off', ':app:packageRelease')
        assert 'Verified native hooks OFF:' in log
        inspect('restored-off', final_off)
    finally:
        library.write_bytes(original)
        # Same-cache final OFF even when an intermediate behavioral assertion fails.
        build_native('OFF')
    print('PASS: directory APK artifacts OFF/ON/unknown/missing; actual packaged libs; '
          'unsigned release, UP-TO-DATE guard, resource and bundle tasks; CMake OFF/ON/OFF')


if __name__ == '__main__':
    main()
