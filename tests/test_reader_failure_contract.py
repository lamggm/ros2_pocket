import unittest
from reader_failure_contract import verify_inactive


class FailureVerifierTests(unittest.TestCase):
    def snapshot(self):
        primary = dict(slot=0, generation=3, readerActive=False, subscription='',
                       last='', message=None, count=0, matched=0, age=-1, hz=0,
                       fresh=False, subscriptionState='error',
                       error='Falha ao assinar: Bad Parameter', numericFields=[],
                       receivedMonotonicNs=None, sourceTimestampNs=None)
        return dict(primary, sessionGeneration=1, readerGeneration=4,
                    watches=[primary, dict(slot=1, readerActive=False, count=0)])

    def test_active_primary_cannot_hide_behind_inactive_extra(self):
        snapshot = self.snapshot()
        snapshot['readerActive'] = snapshot['watches'][0]['readerActive'] = True
        snapshot['count'] = snapshot['watches'][0]['count'] = 7
        with self.assertRaises(AssertionError):
            verify_inactive(snapshot, 'reader')

    def test_each_primary_field_is_required_at_both_projections(self):
        values = dict(readerActive=True, subscription='/old', last='old', message={},
                      count=7, matched=1, age=0, hz=1, fresh=True,
                      subscriptionState='receiving', error='', numericFields=[{}],
                      receivedMonotonicNs=1, sourceTimestampNs=1)
        for key, bad in values.items():
            for projection in ('root', 'watch'):
                with self.subTest(key=key, projection=projection):
                    snapshot = self.snapshot()
                    target = snapshot if projection == 'root' else snapshot['watches'][0]
                    target[key] = bad
                    with self.assertRaises(AssertionError):
                        verify_inactive(snapshot, 'reader')

    def test_valid_inactive(self):
        verify_inactive(self.snapshot(), 'reader')
