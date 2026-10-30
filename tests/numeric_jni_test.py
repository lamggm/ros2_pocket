"""Real production Linux JNI boundary; no Android runtime or JSON stub parser.
The SDK JAR supplies compile-time signatures only. The JVM prints raw native JSON;
Python validates the returned bytes. No Android framework method is executed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve() / uuid.uuid4().hex
    out.mkdir(parents=True, exist_ok=False)
    core = args.core.resolve()
    jdk = Path(os.environ['JAVA_HOME'])
    sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
    android_jar = sdk / 'platforms/android-35/android.jar'
    assert core.is_file() and android_jar.is_file()
    tc = Path(os.environ['POCKET_TOOLCHAINS']) / 'host'
    libraries = list(tc.glob('lib*/libddsc.so'))
    assert len(libraries) == 1
    lib = libraries[0].parent
    hooks = 'POCKET_ENABLE_TEST_HOOKS:BOOL=ON' in (core.parent / 'CMakeCache.txt').read_text()

    def run(label, command):
        p = subprocess.run([str(x) for x in command], cwd=ROOT, capture_output=True, text=True, timeout=25)
        (out / (label + '.stdout')).write_text(p.stdout)
        (out / (label + '.stderr')).write_text(p.stderr)
        if p.returncode:
            raise RuntimeError(label + ' exit ' + str(p.returncode) + ': ' + p.stderr[-2200:])
        return p.stdout

    flags = ['-DPOCKET_ENABLE_TEST_HOOKS=1'] if hooks else []
    run('compile-jni', ['c++', '-std=c++17', '-shared', '-fPIC', *flags,
                       '-I' + str(jdk / 'include'), '-I' + str(jdk / 'include/linux'),
                       '-Iapp/src/main/cpp', '-Iapp/src/main/cpp/generated', '-I' + str(tc / 'include'),
                       'app/src/main/cpp/jni.cpp', core,
                       '-L' + str(lib), '-Wl,-rpath,' + str(lib), '-lddsc', '-pthread',
                       '-o', out / 'librospocket.so'])
    run('compile-java', [jdk / 'bin/javac', '-encoding', 'UTF-8', '-cp', android_jar,
                        '-d', out, 'app/src/main/java/com/lamggm/rospocket/NativeBridge.java',
                        'tests/NumericJniBridgeTest.java'])
    raw = run('real-jni', [jdk / 'bin/java', '-Djava.library.path=' + str(out),
                          '-cp', str(out) + os.pathsep + str(android_jar),
                          'com.lamggm.rospocket.NumericJniBridgeTest'])
    rows = [json.loads(line) for line in raw.splitlines()]
    assert len(rows) == 12
    assert rows[0]['ok'] is True
    assert rows[1]['numericBatch']['epoch'] == 1
    assert rows[1]['numericBatch']['slots'][0]['paths'] == ['data']
    assert all(row['ok'] is False for row in rows[2:9])
    assert rows[9]['numericBatch'] == rows[1]['numericBatch']
    assert rows[10]['ok'] is True and rows[11]['numericBatch']['epoch'] == 2
    assert all(not slot['paths'] and not slot['samples'] for slot in rows[11]['numericBatch']['slots'])
    result = {'classification': 'real Linux JNI boundary; no DDS producer or Android execution',
              'passed': True, 'invalid_requests_rejected': 7,
              'core_sha256': hashlib.sha256(core.read_bytes()).hexdigest(),
              'jni_sha256': hashlib.sha256((out / 'librospocket.so').read_bytes()).hexdigest()}
    (out / 'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
