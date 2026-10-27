"""Explicit OFF/ON/OFF same-cache native build and header/symbol exclusion gate."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROBE = '''#include "inspector.hpp"
void probe(pocket::Session& session) {
 auto id=session.reader_identity_for_test(0);
 auto handle=session.arm_reader_failure_for_test(0,"reader",id.session,id.generation,id.request,1,1);
 session.cancel_reader_failure_for_test(handle);
 session.reader_failure_receipt_for_test();
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    cache = args.evidence.resolve() / 'native-cache'
    for iteration, hooks in enumerate(('OFF', 'ON', 'OFF')):
        logs = []
        commands = [
            ['cmake', '-S', 'app/src/main/cpp', '-B', str(cache), '-G', 'Ninja',
             '-DCMAKE_PREFIX_PATH=' + os.environ['POCKET_TOOLCHAINS'] + '/host',
             '-DPOCKET_ENABLE_TEST_HOOKS=' + hooks],
            ['cmake', '--build', str(cache), '--target', 'pocket_core', '--parallel', '4'],
        ]
        for command in commands:
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
            logs.append(result.stdout + result.stderr)
            assert result.returncode == 0, logs
        archive = cache / 'libpocket_core.a'
        assert archive.read_bytes().count(('POCKET_TEST_HOOKS=' + hooks).encode()) == 1
        symbols = subprocess.check_output(['nm', '-C', str(archive)], text=True)
        for name in ('arm_reader_failure_for_test', 'cancel_reader_failure_for_test',
                     'reader_identity_for_test', 'reader_failure_receipt_for_test'):
            assert (name in symbols) is (hooks == 'ON'), (hooks, name)
        command = ['c++', '-std=c++17', '-Iapp/src/main/cpp', '-fsyntax-only', '-x', 'c++', '-']
        if hooks == 'ON':
            command.insert(1, '-DPOCKET_ENABLE_TEST_HOOKS=1')
        result = subprocess.run(command, cwd=ROOT, input=PROBE, capture_output=True, text=True)
        logs.append(result.stdout + result.stderr)
        assert (result.returncode == 0) is (hooks == 'ON')
        if hooks == 'OFF':
            assert 'no member named' in result.stderr
        (args.evidence / f'{iteration}-{hooks}.log').write_text('\n'.join(logs) + '\n' + symbols)
    print('PASS: native OFF/ON/OFF same cache, actual capability markers and symbols; '
          'all four hook APIs compile only with ON')


if __name__ == '__main__':
    main()
