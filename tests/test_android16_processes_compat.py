"""Deterministic synthetic Android16 wire fixtures; NOT physical proof.

Source contract and SHA pins: docs/ANDROID-DIAGNOSTIC-POLICY.md.
Never reads device captures or ignored evidence. Fixture IDs are invented.
"""
import struct
import unittest
from test_android_instrumentation import field as f, process, tool, wire, PACKAGE


def uid_record(uid=10123, state=1002):
    # UidRecordProto: current uses ProcessStateEnum, not ActivityManager 0..20.
    return (f(1,uid)+f(2,state)+f(3,0)+f(4,0)+f(5,0)
            +f(6,f(1,100)+f(2,200))+f(7,0)+f(8,2)+f(8,6)
            +f(9,1)+f(10,f(1,300)+f(2,299)+f(3,298)))


def float_field(n,value):
    # Protobuf float is fixed32 (wire5), not a varint or length-delimited bytes.
    from test_android_instrumentation import varint
    return varint((n<<3)|5)+struct.pack('<f',value)


def oom_record(proc=None, source=None):
    if proc is None:proc=process()
    if source is None:source=process()
    detail=(b''.join(f(n,-1 & (2**64-1)) for n in (1,2,3,4,5))
            +f(7,1002)+f(8,1002)+f(9,'1KB')+f(10,'0KB')+f(11,'1KB')
            +f(12,0)+f(13,0)+f(14,0)
            +f(15,f(1,100)+f(2,25)+float_field(3,0.25)))
    return (f(1,0)+f(2,1)+f(3,'fore')+f(4,2)+f(5,1)+f(7,1002)
            +f(8,0)+f(9,proc)+f(10,'top-activity')
            +f(11,f(1,PACKAGE)+f(2,'SyntheticComponent'))+f(13,source)+f(15,detail))


def lru(record):
    # Counters are global, the repeated list is package-filtered.
    return f(6,f(1,87)+f(2,31)+f(3,20)+f(4,record))


class Android16ProcessesCompatibilityTests(unittest.TestCase):
    def parse(self,raw):
        return tool().parse_processes(raw,PACKAGE,123,10123)

    def test_qpr2_known_capability_flags_preserve_active_detection(self):
        # QPR2 Detail16 is repeated ProcessCapabilityEnum (wire0), not a bitmask.
        # ProcessList.writeProcessCapabilitiesListToProto emits each set bit once.
        for active in (False,True):
            for flags in ((1,), (9,), tuple(range(1,10)), (9,2,5)):
                with self.subTest(active=active,flags=flags):
                    extra=lru(f(9,process())+f(15,b''.join(f(16,n) for n in flags)))
                    self.assertIs(self.parse(wire(active)+extra)['targetActive'],active)


    def test_qpr2_duplicate_capability_flags_fail_closed(self):
        for active in (False,True):
            for flags in ((2,2),(9,2,9)):
                with self.subTest(active=active,flags=flags),self.assertRaises(ValueError):
                    self.parse(wire(active)+lru(f(15,b''.join(f(16,n) for n in flags))))


    def test_qpr2_unknown_capabilities_and_wrong_wires_are_unavailable(self):
        # UNKNOWN=0 and unmapped/negative values cannot establish known status.
        for active in (False,True):
            for flag in (0,10,127,2**64-1):
                with self.subTest(active=active,flag=flag),self.assertRaises(ValueError):
                    self.parse(wire(active)+lru(f(15,f(16,flag))))
            for wrong in (f(16,b'\x01\x02'),float_field(16,1.0)):
                with self.subTest(active=active,wire=wrong),self.assertRaises(ValueError):
                    self.parse(wire(active)+lru(f(15,wrong)))


    def test_independent_uid_collections_need_not_share_snapshots(self):
        # ProcessList.mActiveUids and UidObserverController.mValidateUids are
        # distinct projections. The observer creates UidRecord(uid,null) and
        # updates only event fields; optional presence, state, seq/time can differ.
        active_uid=uid_record()
        validate_uid=f(1,10123)+f(2,1003)+f(6,f(1,500)+f(2,600))
        for active in (False,True):
            with self.subTest(active=active):
                self.assertIs(self.parse(wire(active)+f(4,active_uid)+f(5,validate_uid))['targetActive'],active)


    def test_nonfinite_cpu_utilization_is_unavailable(self):
        for value in (float('nan'),float('inf'),-float('inf')):
            with self.subTest(value=value),self.assertRaises(ValueError):
                self.parse(wire()+lru(f(15,f(15,float_field(3,value)))))


    def test_cpu_float_uses_fixed32_without_invented_ratio_range(self):
        for value in (-1.0,0.0,0.25,100.0,1000.0):
            with self.subTest(value=value):
                self.assertFalse(self.parse(wire()+lru(f(15,f(15,float_field(3,value)))))['targetActive'])


    def test_pinned_qpr2_parser_identity_is_versioned(self):
        self.assertEqual('aosp-android16-qpr2-am-processes-v2',
                         self.parse(wire())['parser'])


    def test_uid_recursive_schema_status_wire_and_duplicates_fail_closed(self):
        uid=f(1,10123)
        invalid=[b'',f(2,1002),f(1,0),f(1,2**31),uid+f(1,10123),
                 uid+f(2,1002)+f(2,1002),uid+f(2,b'known'),uid+f(3,2),
                 uid+f(6,1),uid+f(6,f(3,1)),uid+f(6,f(1,1)+f(1,1)),
                 uid+f(6,b'')+f(6,b''),uid+f(8,8),uid+f(8,b'\x01'),
                 uid+f(10,f(4,1)),uid+f(10,f(1,1)+f(1,1)),uid+f(11,1)]
        invalid += [uid+f(2,n) for n in (0,998,999,1021,2**64-1)]
        for collection in (4,5):
            for record in invalid:
                with self.subTest(collection=collection,record=record),self.assertRaises(ValueError):
                    self.parse(wire()+f(collection,record))
        for collection in (4,5):
            # A UID record is not authority for PID/name presence, even matching UID.
            with self.subTest(anchor=collection),self.assertRaises(ValueError):
                self.parse(f(collection,uid_record()))


    def test_lru_recursive_schema_status_wire_and_duplicates_fail_closed(self):
        invalid=[f(16,1),f(1,2),f(2,2**31),f(2,b'1'),f(3,b'\xff'),
                 f(4,4),f(4,2**64-1),f(9,1),f(9,process()+f(99,1)),
                 f(11,f(3,'unknown')),f(15,f(6,1)),f(15,f(16,0)),
                 f(15,f(12,2)),f(15,f(15,f(4,1))),
                 f(15,f(15,f(3,1))),f(15,f(15,f(1,1)+f(1,1))),
                 f(15,f(15,float_field(3,0.25)+float_field(3,0.25)))]
        invalid += [f(7,n) for n in (0,998,999,1021,2**64-1)]
        invalid += [f(15,f(s,n)) for s in (7,8) for n in (0,998,999,1021)]
        invalid += [piece+piece for piece in (f(1,0),f(4,1),f(9,process()),f(15,b''))]
        for record in invalid:
            with self.subTest(record=record),self.assertRaises(ValueError):
                self.parse(wire()+lru(record))
        for extra in (f(6,1),f(6,f(5,1)),f(6,f(1,1)+f(1,1)),lru(b'')+lru(b'')):
            with self.subTest(extra=extra),self.assertRaises(ValueError):
                self.parse(wire()+extra)


    def test_unsupported_sections_and_wire_bounds_remain_unavailable(self):
        from test_android_instrumentation import varint
        invalid=[wire()+f(8,b''),wire()+f(12,b''),wire()+f(60,1),
                 wire()+f(6,f(4,f(15,f(17,1)))),
                 wire()+f(6,f(4,f(3,'x'*65537))),
                 wire()+lru(f(9,process(name='x'*1025))),
                 wire()+varint(6<<3|2)+varint(10)+b'x',
                 wire()+b'\x80'*11,wire()+b'\x00',
                 b'x'*(tool().MAX_BYTES+1),f(24,1)*16385]
        for raw in invalid:
            with self.subTest(size=len(raw)),self.assertRaises(ValueError):self.parse(raw)


    def test_source_backed_active_and_validate_uids(self):
        for active in (False,True):
            with self.subTest(active=active):
                raw=wire(active)+f(4,uid_record())+f(5,uid_record())
                self.assertIs(self.parse(raw)['targetActive'],active)


    def test_uid_record_semantic_ambiguity_is_not_absence(self):
        good=uid_record()
        cases=[f(4,b''),f(4,f(2,1002)),f(4,f(1,0)),
               f(4,f(1,10123)+f(9,2**64-1)),
               f(4,good)+f(4,good),f(5,good)+f(5,good),
               # Same-UID duplicates still reject WITHIN each projection.
               f(4,good)+f(4,uid_record(state=1003)),
               f(5,good)+f(5,uid_record(state=1003)),
               f(4,f(1,10123)+f(8,2)+f(8,2))]
        for extra in cases:
            with self.subTest(extra=extra),self.assertRaises(ValueError):
                self.parse(wire()+extra)

    def test_uid_projections_accept_field_order_and_optional_presence(self):
        a=f(1,10123)+f(2,1002)+f(6,f(1,100)+f(2,200))+f(8,2)+f(8,6)
        b=f(8,6)+f(6,f(2,200)+f(1,100))+f(2,1002)+f(8,2)+f(1,10123)
        self.assertFalse(self.parse(wire()+f(4,a)+f(5,b))['targetActive'])
        # A UID can own unlisted processes; the command filters by package.
        self.assertFalse(self.parse(wire()+f(4,f(1,10123)+f(9,2)))['targetActive'])


    def test_full_source_backed_lru_shape_preserves_active_detection(self):
        for active in (False,True):
            with self.subTest(active=active):
                raw=wire(active)+f(4,uid_record())+f(5,uid_record())+lru(oom_record())
                self.assertIs(self.parse(raw)['targetActive'],active)

    def test_new_lru_identities_reconcile_without_becoming_anchors(self):
        for ref in (9,13):
            for bad in (process(123,'other.package'),process(123,PACKAGE,10124),
                        process(124,PACKAGE),process(0)):
                with self.subTest(ref=ref,bad=bad),self.assertRaises(ValueError):
                    self.parse(wire()+lru(f(ref,bad)))
            with self.subTest(anchor=ref),self.assertRaises(ValueError):
                self.parse(lru(f(ref,process())))
        # Conflicting foreign PID cross-references also reject, including a
        # conflict first seen in the LRU list and later in instrumentation.
        foreign=f(5,f(1,f(2,'another.package')))+f(3,process(124,'changed'))
        with self.assertRaises(ValueError):
            self.parse(wire()+lru(f(9,process(124,'original')))+f(3,foreign))
        with self.assertRaises(ValueError):
            self.parse(wire()+f(2,process(124,'isolated'))+lru(f(13,process(124,'changed'))))
        with self.assertRaises(ValueError):
            self.parse(wire()+lru(f(9,process(124,'original'))+f(13,process(124,'changed'))))


    def test_lru_oneofs_are_exclusive_even_for_false_or_empty_values(self):
        for a,b in ((f(5,0),f(6,0)),(f(11,b''),f(12,'')),
                    (f(13,process()),f(14,''))):
            with self.subTest(a=a,b=b),self.assertRaises(ValueError):
                self.parse(wire()+lru(a+b))


if __name__=='__main__':unittest.main()
