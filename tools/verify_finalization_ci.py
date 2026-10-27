#!/usr/bin/env python3
"""Fail-closed integration registration. Host tests only; never physical acceptance."""
import argparse
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_GATES = {'11-soak', '13-multiwatch', '14-plot'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=ROOT / 'ci/finalization-gates.json')
    args = parser.parse_args()
    try:
        registry = json.loads(args.registry.read_text())
        if not isinstance(registry, dict) or set(registry) != {'schema_version', 'gates'}:
            raise ValueError('unsupported registry fields')
        gates = registry['gates']
        if (type(registry['schema_version']) is not int or registry['schema_version'] != 1
                or not isinstance(gates, list) or len(gates) != len(REQUIRED_GATES)
                or any(not isinstance(gate, dict) or set(gate) != {'id', 'state', 'test_files', 'minimum_tests'}
                       for gate in gates)
                or {gate['id'] for gate in gates} != REQUIRED_GATES):
            raise ValueError('exact schema 1 and unique 11/13/14 registration required')
        seen = set()
        for gate in gates:
            if gate['state'] not in ('pending_integration', 'registered'):
                raise ValueError('unsupported gate state')
            if type(gate['minimum_tests']) is not int or gate['minimum_tests'] < 1:
                raise ValueError('positive integer minimum_tests required')
            if not isinstance(gate['test_files'], list):
                raise ValueError('test_files must be an explicit list')
            for name in gate['test_files']:
                if not isinstance(name, str):
                    raise ValueError('test file must be a relative path')
                path = ROOT / name
                if (path.parent != ROOT / 'tests' or not path.name.startswith('test_')
                        or path.suffix != '.py' or path.is_symlink()):
                    raise ValueError('only direct tests/test_*.py files may be registered')
                if path.resolve() in seen:
                    raise ValueError('duplicate test file registration')
                seen.add(path.resolve())
        pending = [gate['id'] + ': pending integration' for gate in gates
                   if gate['state'] != 'registered']
        if pending:
            raise ValueError('\n'.join(pending))
        for gate in gates:
            suite = unittest.TestSuite()
            if not gate['test_files']:
                raise ValueError(gate['id'] + ': no test files registered')
            for name in gate['test_files']:
                path = ROOT / name
                if not path.is_file():
                    raise ValueError(gate['id'] + ': missing test file ' + name)
                selected = unittest.defaultTestLoader.discover(str(path.parent), pattern=path.name)
                if not selected.countTestCases():
                    raise ValueError(gate['id'] + ': empty test selection ' + name)
                suite.addTests(selected)
            if suite.countTestCases() < gate['minimum_tests']:
                raise ValueError(gate['id'] + ': test count below registered minimum')
            result = unittest.TextTestRunner(verbosity=2).run(suite)
            print(f"{gate['id']}: {result.testsRun} tests, {len(result.skipped)} skips", flush=True)
            if not result.wasSuccessful() or result.skipped or result.expectedFailures:
                return 1
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
