"""Synthetic wire/parser controls ONLY. No Android transport qualification."""
import importlib.util,hashlib,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PACKAGE='com.lamggm.rospocket.dev'
def tool():
    spec=importlib.util.spec_from_file_location('android_instrumentation',ROOT/'tools/android_instrumentation.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def varint(n):
    b=bytearray()
    while n>127:b.append((n&127)|128);n>>=7
    b.append(n);return bytes(b)
def field(n,value):
    if isinstance(value,str):value=value.encode()
    if isinstance(value,int):return varint(n<<3)+varint(value)
    return varint((n<<3)|2)+varint(len(value))+value

def process(pid=123,name=PACKAGE,uid=10123):return field(1,pid)+field(2,name)+field(3,uid)+field(4,0)
def wire(active=False):
    base=field(1,process())
    if active:
        # Runner component is foreign. TARGET info, not runner package, identifies instrumentation.
        base+=field(3,field(1,field(1,'foreign.runner'))+field(2,0)+field(3,process())+field(4,PACKAGE)+field(5,field(1,field(2,PACKAGE)))+field(10,field(2,'FOREIGN_ARGUMENTS_MUST_NOT_RETAIN')))
    return base
class InstrumentationParserTests(unittest.TestCase):
    def test_source_backed_target_correlation_and_unknown_command_trap(self):
        m=tool();absent=m.parse_processes(wire(),PACKAGE,123,10123)
        self.assertIs(absent['targetActive'],False)
        active=m.parse_processes(wire(True),PACKAGE,123,10123)
        self.assertIs(active['targetActive'],True);self.assertNotIn('FOREIGN',str(active));self.assertNotIn('runner',str(active))
        for bad in (b'',b'Unknown command: instrumentation\n',wire()[:-1],field(1,process(124)),field(1,process())+field(1,process(123,'conflict')),wire()+field(3,b''),field(60,1)+wire()):
            with self.subTest(bad=bad[:20]),self.assertRaises(ValueError):m.parse_processes(bad,PACKAGE,123,10123)
    def test_malformed_or_ambiguous_nested_records_never_prove_absence(self):
        m=tool(); f=field; base=wire(); foreign=f(5,f(1,f(2,'another.package')))
        mutants=[base+f(2,b'\x80'),base+f(2,123),base+f(1,process(123,'another.package')),
                 f(1,process()+f(99,1)),base+f(3,foreign+f(99,1)),
                 base+f(3,foreign+f(2,2)),base+f(3,foreign+f(2,b'UNKNOWN')),
                 base+f(3,foreign+f(2,0)+f(2,1)),base+f(3,foreign)+f(3,foreign),
                 base+f(2,process(123,'another.package')),base+f(2,process(124,PACKAGE)),
                 base+f(3,foreign+f(5,f(1,f(2,'another.package'))))]
        for bad in mutants:
            with self.subTest(raw=bad),self.assertRaises(ValueError):m.parse_processes(bad,PACKAGE,123,10123)
        self.assertFalse(m.parse_processes(base+f(2,process(124,'isolated.package')),PACKAGE,123,10123)['targetActive'])

    def test_every_supported_process_reference_reconciles_identity(self):
        m=tool()
        for root in (1,2,7,9,10,11,15,16,18):
            for conflicting in (process(123,'another.package'),process(123,PACKAGE,10124)):
                with self.subTest(root=root,raw=conflicting),self.assertRaises(ValueError):
                    m.parse_processes(wire()+field(root,conflicting),PACKAGE,123,10123)
        foreign=field(5,field(1,field(2,'another.package')))
        with self.assertRaises(ValueError):
            m.parse_processes(wire()+field(7,process(124,'foreign'))+field(3,foreign+field(3,process(124,'conflict'))),PACKAGE,123,10123)

    def test_every_supported_reference_preserves_unique_target_identity(self):
        m=tool()
        for root in (1,2,7,9,10,11,15,16,18):
            with self.subTest(root=root),self.assertRaises(ValueError):
                m.parse_processes(wire()+field(root,process(124,PACKAGE)),PACKAGE,123,10123)
        running=field(5,field(1,field(2,'another.package')))+field(3,process(124,PACKAGE))
        with self.assertRaises(ValueError):m.parse_processes(wire()+field(3,running),PACKAGE,123,10123)

    def test_identical_aosp_references_are_not_conflicting_processes(self):
        m=tool()
        repeated=wire()+field(1,process())+field(2,process())
        for root in (7,9,10,11,15,16,18):repeated+=field(root,process())
        self.assertFalse(m.parse_processes(repeated,PACKAGE,123,10123)['targetActive'])
        instrument=field(5,field(1,field(2,PACKAGE)))+field(3,process())+field(3,process())
        self.assertTrue(m.parse_processes(repeated+field(3,instrument),PACKAGE,123,10123)['targetActive'])

    def test_unrelated_target_is_not_decided_by_runner_name(self):
        m=tool();foreign=field(3,field(1,field(1,PACKAGE))+field(5,field(1,field(2,'another.package'))))
        self.assertFalse(m.parse_processes(wire()+foreign,PACKAGE,123,10123)['targetActive'])
if __name__=='__main__':unittest.main()
