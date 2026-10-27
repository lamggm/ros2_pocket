#!/usr/bin/env python3
"""Synthetic omission controls against the ACTUAL Python oracle source.

Every mutant must be killed by exactly one existing behavioral test, not syntax,
import failure or an invented alternate implementation. No product file is edited.
"""
import importlib.util
import io
import json
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parent


def main():
    original=(ROOT/'device_multiwatch_fixture.py').read_text()
    rows=[]
    for name,old,new,test in [
        ('publication-gap',"        assert 0 < b['monotonicNs']-a['monotonicNs'] <= 1_500_000_000, 'publication gap'",'        pass','NegativeWindowTests.test_window_requires_actual_contiguous_publications_and_a_later_capture'),
        ('closing-fence',"    assert events[-1]['monotonicNs'] <= history_fence_ns < observations[-1]['capturedNs'], 'closing fence'",'    pass','NegativeWindowTests.test_window_requires_actual_contiguous_publications_and_a_later_capture'),
        ('new-rosout',"    assert log_sequence(after, owner) > log_sequence(before, owner), 'stalled rosout'",'    pass','IsolationTests.test_survivors_must_advance_and_preserve_identity_and_new_logs'),
    ]:
        assert original.count(old)==1
        # Exec a temporary module: source not mutated on disk, no global monkeypatch.
        import types
        module=types.ModuleType('device_multiwatch_fixture')
        exec(compile(original.replace(old,new),str(ROOT/'device_multiwatch_fixture.py')+'::'+name,'exec'),module.__dict__)
        prior=sys.modules.get('device_multiwatch_fixture')
        sys.modules['device_multiwatch_fixture']=module
        try:
            spec=importlib.util.spec_from_file_location('front13_omission_test',ROOT/'test_multiwatch13.py')
            tests=importlib.util.module_from_spec(spec);spec.loader.exec_module(tests)
            suite=unittest.defaultTestLoader.loadTestsFromName(test,tests)
            stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
            print(stream.getvalue())
            assert result.testsRun==1 and len(result.failures)==1 and not result.errors, (name,'mutant survived or infrastructure error')
            rows.append(dict(mutation=name,testsRun=result.testsRun,failures=len(result.failures),errors=len(result.errors),killed=True))
        finally:
            if prior is None: sys.modules.pop('device_multiwatch_fixture',None)
            else: sys.modules['device_multiwatch_fixture']=prior
    print('MUTATION_CONTROLS '+json.dumps(rows))


if __name__=='__main__': main()
