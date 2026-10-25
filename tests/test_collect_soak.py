"""Real collector transformations and CLI; all device inputs explicitly synthetic."""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from test_device_snapshot import snapshot, frame
from test_qualification import metadata, diagnostic_metadata


def stat(pid=123, ticks=100, start=1234):
    fields = ['S'] + ['0']*49
    fields[11] = str(ticks); fields[12] = '10'; fields[19] = str(start)
    return (str(pid)+' (synthetic process) '+' '.join(fields)+'\n').encode()


class MetricTests(unittest.TestCase):
    def test_actual_proc_battery_pss_parsers_preserve_identity_units(self):
        import collect_soak as c
        self.assertEqual(c.parse_stat(stat()), dict(pid=123, cpu_ticks=110, start_time_ticks=1234))
        self.assertEqual(c.parse_pss(b'App Summary\n TOTAL PSS: 4321 TOTAL RSS: 9999\n'), 4321)
        self.assertEqual(c.parse_pss(b' TOTAL 4321 500 1 20 0 0\n'), 4321)
        self.assertEqual(c.parse_pss(b' TOTAL 4321 500 1 20 0 0\n TOTAL PSS: 4321 TOTAL RSS: 9999\n'),4321)
        with self.assertRaises(ValueError):
            c.parse_pss(b' TOTAL 9999 500 1 20 0 0\n TOTAL PSS: 4321 TOTAL RSS: 9999\n')
        self.assertEqual(c.parse_battery(b'Current Battery Service state:\n AC powered: false\n USB powered: false\n Wireless powered: false\n Dock powered: false\n status: 3\n level: 80\n scale: 100\n'), (80.0, False))
        self.assertEqual(c.cpu_percent(dict(cpu_ticks=100, host_ns=1_000_000_000, uptime_s=10),
                                       dict(cpu_ticks=250, host_ns=16_000_000_000, uptime_s=25), 100), 10)


class Clock:
    def __init__(self): self.ns = 1_000_000_000
    def now(self): return self.ns
    def sleep(self, seconds): self.ns += int(seconds*1e9)


class SyntheticTransport:
    """Explicit synthetic Linux/Android outputs, no device or real ADB."""
    def __init__(self, clock, meta):
        self.clock=clock; self.meta=meta; self.calls=[]; self.sequence=0; self.mutate=None; self.journal=None
    def read(self, args, deadline, limit=65536):
        self.calls.append(list(args)); started=self.clock.now(); self.clock.ns += 1_000_000
        command=' '.join(args)
        if command=='get-state': raw=b'device\n'
        elif command.endswith('getprop ro.build.version.sdk'): raw=b'36\n'
        elif command.endswith('getprop ro.build.fingerprint'): raw=b'SYNTHETIC-ANDROID16-BUILD\n'
        elif 'getconf CLK_TCK' in command: raw=b'100\n'
        elif 'pidof ' in command: raw=b'123\n'
        elif command.endswith('/cmdline'): raw=b'com.lamggm.rospocket.dev\0'
        elif command.endswith('/status'): raw=b'Uid:\t10123\t10123\t10123\t10123\n'
        elif command.endswith('/stat'): raw=stat(ticks=self.clock.ns//100_000_000)
        elif command.endswith('/proc/uptime'): raw=f'{self.clock.ns/1e9:.3f} 0\n'.encode()
        elif 'pm path ' in command: raw=b'package:/data/app/synthetic/base.apk\n'
        elif 'sha256sum ' in command: raw=(self.meta['sha256']+'  /data/app/synthetic/base.apk\n').encode()
        elif 'meminfo' in command: raw=b' TOTAL PSS: 4321 TOTAL RSS: 9999\n'
        elif command.endswith('dumpsys battery'): raw=b'AC powered: false\nUSB powered: false\nWireless powered: false\nstatus: 3\nlevel: 80\nscale: 100\n'
        elif '--proto processes' in command:
            from test_android_instrumentation import wire
            raw=wire()
        elif '--pocket-qa-v1' in command:
            self.sequence += 1; data=snapshot(self.meta['schema_version']==2)
            data['pid']=123; data['testHooks']['pid']=123
            shift=self.sequence*600_000_000
            for key in ('capturedMonotonicNs','exportedMonotonicNs'):
                data[key]+=shift; data['testHooks'][key]=data[key]
            data['testHooks']['captureSequence']=self.sequence
            display=data['plotDisplay']
            display['observedMonotonicNs']+=shift; display['lastDrawMonotonicNs']+=shift
            for rows in (display['series'],display['drawnSeries'],data['plotIntake']['series']):
                for row in rows:
                    row['oldestReceiveNs']+=shift; row['newestReceiveNs']+=shift
            for w in data['watches']: w['count']=100+int(self.clock.ns/1e9)*2
            raw=frame(data)
        else: raise AssertionError(command)
        if self.mutate: raw=self.mutate(args,raw)
        if self.journal is not None:
            import base64, hashlib
            self.journal.event('read',command=args,host_start_ns=started,host_end_ns=self.clock.now(),
                               success=True,error=None,input_size_bytes=len(raw),
                               input_sha256=hashlib.sha256(raw).hexdigest(),stderr_size_bytes=0,
                               input_b64=base64.b64encode(raw).decode())
        return raw


class CollectorTests(unittest.TestCase):
    def test_full_campaign_accepts_near_maximum_valid_qa_frame_with_bounded_journal(self):
        import collect_soak as c
        import qualification as q
        import test_qualification as fixtures
        import device_snapshot as qa
        root=Path(__file__).resolve().parents[1]
        clock=Clock(); meta=diagnostic_metadata(); transport=SyntheticTransport(clock,meta)
        attestation=fixtures.QualificationTests().diagnostic_soak(q)['os_transport_validation']
        build=__import__('hashlib').sha256(b'SYNTHETIC-ANDROID16-BUILD').hexdigest()
        for item in (attestation,attestation['negative_control'],attestation['positive_control']['os']):
            item['android_build_sha256']=build
        target=qa.FRAME_MAX_BYTES-128
        frame_bytes=[None]

        def near_maximum_frame(args,raw):
            if '--pocket-qa-v1' not in args:
                return raw
            data=json.loads(raw.decode().splitlines()[2])
            data['syntheticQaPadding']=''
            base=len(json.dumps(data).encode())
            data['syntheticQaPadding']='x'*(target-base)
            self.assertEqual(len(json.dumps(data).encode()),target)
            framed=frame(data)
            frame_bytes[0]=len(framed)
            self.assertLessEqual(len(framed),qa.MAX_BYTES)
            return framed

        transport.mutate=near_maximum_frame
        collector=c.Collector(transport,meta,meta['apk'],clock.now,clock.sleep)
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            out=Path(directory)/'near-max-qa'
            journal=c.Journal(out,root)
            transport.journal=journal
            result=c.run_campaign(collector,journal,attestation)
            saved=q.load_json(out/'soak-input.json')
            events=[json.loads(line) for line in (out/'events.jsonl').read_text().splitlines()]
            self.assertEqual(result['collection_status'],'complete',json.dumps(dict(
                result=result, collection_error=saved.get('collection_error'),
                journal_bytes=journal.bytes, journal_records=journal.records)))
            self.assertEqual(result['analysis']['status'],'passed')
            self.assertIn(result['points'],(121,122))
            self.assertEqual(len(saved['samples']),result['points'])
            self.assertEqual(sum(e['kind']=='sample' for e in events),result['points'])
            self.assertEqual(events[-1]['kind'],'finished')
            self.assertGreater(journal.bytes,8*1024*1024)
            self.assertLess(journal.bytes,c.JOURNAL_MAX_BYTES)
            self.assertLess(journal.records,c.JOURNAL_MAX_RECORDS)
            self.assertLess((out/'soak-input.json').stat().st_size,q.JSON_MAX_BYTES)
            if os.environ.get('SOAK_FOLLOWUP_EVIDENCE'):
                import shutil
                dest=Path(os.environ['SOAK_FOLLOWUP_EVIDENCE'])/'GREEN-near-max-campaign'
                shutil.copytree(out,dest,dirs_exist_ok=True)
                (dest/'measurements.json').write_text(json.dumps(dict(
                    classification='synthetic host campaign; NOT physical acceptance',
                    frame_payload_bytes=target,frame_bytes=frame_bytes[0],
                    journal_bytes=journal.bytes,journal_records=journal.records,
                    journal_budget_bytes=c.JOURNAL_MAX_BYTES,
                    journal_remaining_bytes=c.JOURNAL_MAX_BYTES-journal.bytes,
                    journal_record_cap=c.JOURNAL_MAX_RECORDS,
                    points=result['points'],soak_input_bytes=(out/'soak-input.json').stat().st_size,
                    report_serialization_limit_bytes=q.JSON_MAX_BYTES)),encoding='utf-8')

    def test_sample_collects_real_transformations_and_official_os_java_proof(self):
        import collect_soak as c
        import android_instrumentation as ai
        meta=diagnostic_metadata(); clock=Clock(); transport=SyntheticTransport(clock,meta)
        collector=c.Collector(transport,meta,Path(meta['apk']),clock.now,clock.sleep)
        collector.preflight()
        baseline=collector.process_metrics(clock.now()+5_000_000_000)
        clock.sleep(15)
        sample=collector.sample(baseline)
        self.assertEqual(sample['pss_kib'],4321)
        self.assertGreater(sample['cpu_percent'],0)
        self.assertEqual(sample['received_counts'],[132]*4)
        self.assertIs(sample['charging'],False)
        proof=sample['diagnostic_proof']
        ai.validate_observation(proof['os_before'],meta['package'],False,collector.build)
        self.assertGreater(proof['qa']['capturedMonotonicNs'],proof['export_fence_ns'])
        self.assertFalse(any('uiautomator' in ' '.join(call).lower() for call in transport.calls))


    def test_full_fixed_plan_progressive_persistence_and_short_run_refused(self):
        import collect_soak as c
        import test_qualification as fixtures
        import qualification as q
        clock=Clock(); meta=diagnostic_metadata(); transport=SyntheticTransport(clock,meta)
        collector=c.Collector(transport,meta,meta['apk'],clock.now,clock.sleep)
        attestation=fixtures.QualificationTests().diagnostic_soak(q)['os_transport_validation']
        build=__import__('hashlib').sha256(b'SYNTHETIC-ANDROID16-BUILD').hexdigest()
        for item in (attestation,attestation['negative_control'],attestation['positive_control']['os']):
            item['android_build_sha256']=build
        root=Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            out=Path(directory)/'synthetic-run'
            journal=c.Journal(out,root)
            result=c.run_campaign(collector,journal,attestation)
            self.assertEqual(result['analysis']['status'],'passed')
            saved=q.load_json(out/'soak-input.json')
            self.assertEqual(len(saved['samples']),121)
            events=[json.loads(line) for line in (out/'events.jsonl').read_text().splitlines()]
            self.assertEqual(sum(e['kind']=='sample' for e in events),121)
            self.assertEqual(q.analyze_soak(dict(saved,samples=saved['samples'][:2]),meta)['status'],'incomplete')
            self.assertEqual(events[-1]['kind'],'finished')
            metadata_file=out/'synthetic-metadata.json'; metadata_file.write_text(json.dumps(meta))
            def analyze(input_file):
                return subprocess.run([sys.executable,str(root/'tools/qualification.py'),'analyze-soak',
                                       '--metadata',str(metadata_file),'--apk',meta['apk'],'--input',str(input_file)],
                                      capture_output=True,text=True,timeout=10)
            cli_positive=analyze(out/'soak-input.json')
            self.assertEqual(cli_positive.returncode,0,cli_positive.stderr)
            short=out/'short.json'; short.write_text(json.dumps(dict(saved,samples=saved['samples'][:2])))
            cli_short=analyze(short); self.assertEqual(cli_short.returncode,1)
            broken=out/'broken.json'; broken.write_text('{"samples":[')
            cli_broken=analyze(broken); self.assertEqual(cli_broken.returncode,2)
            self.assertNotIn('Traceback',cli_broken.stderr)
            if os.environ.get('SOAK_CONTROL_EVIDENCE'):
                import shutil
                dest=Path(os.environ['SOAK_CONTROL_EVIDENCE'])/'synthetic-full'
                shutil.copytree(out,dest,dirs_exist_ok=True)
                shutil.copyfile(meta['apk'],dest/'SYNTHETIC-NOT-INSTALLABLE.apk')
                (dest/'executed-cli-results.json').write_text(json.dumps(dict(
                    classification='synthetic host controls; NOT physical acceptance',
                    positive=dict(exit=cli_positive.returncode,stdout=cli_positive.stdout,stderr=cli_positive.stderr),
                    short=dict(exit=cli_short.returncode,stdout=cli_short.stdout,stderr=cli_short.stderr),
                    invalid=dict(exit=cli_broken.returncode,stdout=cli_broken.stdout,stderr=cli_broken.stderr)),indent=2))
            for mutate in (lambda x: x['samples'][0].update(clk_tck=250),
                           lambda x: x['samples'][0]['process_metrics'].update(start_time_ticks=999),
                           lambda x: x['samples'][0]['diagnostic_proof']['qa'].update(clock='host'),
                           lambda x: x['samples'][0].update(cpu_percent=0),
                           lambda x: x['samples'][0].update(collection_read=dict(host_start_ns=0,host_end_ns=99_000_000_000))):
                bad=copy.deepcopy(saved); mutate(bad)
                with self.subTest(mutant=mutate): self.assertEqual(q.analyze_soak(bad,meta)['status'],'incomplete')
            self.assertLessEqual(saved['samples'][-1]['monotonic_s']-saved['samples'][0]['monotonic_s'],1815)

    def test_same_worker_capture_is_pending_until_newer_than_java_export_fence(self):
        import collect_soak as c
        clock=Clock(); meta=diagnostic_metadata(); t=SyntheticTransport(clock,meta)
        collector=c.Collector(t,meta,meta['apk'],clock.now,clock.sleep)
        collector.preflight(); baseline=collector.process_metrics(clock.now()+5_000_000_000); clock.sleep(15)
        original=t.read; first=[None]; calls=[0]
        def read(args,deadline,limit=65536):
            raw=original(args,deadline,limit)
            if '--pocket-qa-v1' in args:
                calls[0]+=1
                if calls[0]==1: first[0]=raw
                if calls[0]==2: return first[0]
            return raw
        t.read=read
        sample=collector.sample(baseline)
        self.assertEqual(calls[0],3)
        self.assertGreater(sample['diagnostic_proof']['qa']['capturedMonotonicNs'], sample['diagnostic_proof']['export_fence_ns'])

    def test_pending_capture_cannot_hide_an_intermediate_policy_change(self):
        import collect_soak as c
        clock=Clock(); meta=diagnostic_metadata(); t=SyntheticTransport(clock,meta)
        collector=c.Collector(t,meta,meta['apk'],clock.now,clock.sleep)
        collector.preflight(); baseline=collector.process_metrics(clock.now()+5_000_000_000); clock.sleep(15)
        original=t.read; fence=[None]; calls=[0]
        def read(args,deadline,limit=65536):
            raw=original(args,deadline,limit)
            if '--pocket-qa-v1' in args:
                calls[0]+=1
                if calls[0]==1: fence[0]=json.loads(raw.decode().splitlines()[2])
                if calls[0]==2:
                    bad=copy.deepcopy(fence[0]); bad['policyGeneration']+=1; bad['testHooks']['policy']+=1
                    return frame(bad)
            return raw
        t.read=read
        with self.assertRaisesRegex(ValueError,'changed'): collector.sample(baseline)

    def test_incomplete_collector_envelope_cannot_pass_official_analyzer(self):
        import qualification as q
        import test_qualification as fixtures
        data=fixtures.QualificationTests().synthetic_soak(q)
        data.update(collector_schema=1,collection_status='incomplete')
        self.assertEqual(q.analyze_soak(data,metadata())['status'],'incomplete')

    def test_pipeline_rejects_synthetic_process_metrics_power_os_and_candidate_mutants(self):
        import collect_soak as c
        from test_android_instrumentation import wire
        mutants=[('pidof ',lambda raw:b'123 456\n'), ('/cmdline',lambda raw:b'foreign.app\0'),
                 ('/stat',lambda raw:stat(start=999)), ('getconf CLK_TCK',lambda raw:b'250\n'),
                 ('sha256sum ',lambda raw:b'0'*64+b'  /data/app/synthetic/base.apk\n'),
                 ('meminfo',lambda raw:b'TOTAL UNKNOWN\n'),
                 ('dumpsys battery',lambda raw:raw.replace(b'USB powered: false',b'USB powered: true')),
                 ('dumpsys battery',lambda raw:raw+b'(UPDATES STOPPED -- use reset)\n'),
                 ('/proc/uptime',lambda raw:b'1.0 0\n'),
                 ('--proto processes',lambda raw:wire(True)),
                 ('--proto processes',lambda raw:b'Unknown command: processes\n'),
                 ('--pocket-qa-v1',lambda raw:raw.replace(b'"armed": false',b'"armed": true'))]
        for target,mutate in mutants:
            clock=Clock(); meta=diagnostic_metadata(); t=SyntheticTransport(clock,meta)
            collector=c.Collector(t,meta,meta['apk'],clock.now,clock.sleep)
            collector.preflight(); baseline=collector.process_metrics(clock.now()+5_000_000_000); clock.sleep(15)
            t.mutate=lambda args,raw: mutate(raw) if target in ' '.join(args) else raw
            with self.subTest(target=target,mutant=mutate), self.assertRaises(ValueError): collector.sample(baseline)
        clock=Clock(); meta=diagnostic_metadata(); t=SyntheticTransport(clock,meta)
        collector=c.Collector(t,meta,meta['apk'],clock.now,clock.sleep)
        collector.preflight(); baseline=collector.process_metrics(clock.now()+5_000_000_000); clock.sleep(15)
        def late(args,raw):
            if '--proto' in args: clock.sleep(6)
            return raw
        t.mutate=late
        with self.assertRaisesRegex(ValueError,'deadline'): collector.sample(baseline)

    def test_gaps_interrupt_and_replacement_preserve_completed_points_without_resume(self):
        import collect_soak as c
        import qualification as q
        root=Path(__file__).resolve().parents[1]
        for failure in ('gap','interrupt','pid'):
            clock=Clock(); meta=metadata(); t=SyntheticTransport(clock,meta)
            naps=[0]
            def sleep(seconds):
                naps[0]+=1
                if naps[0]==3:
                    if failure=='interrupt': raise KeyboardInterrupt()
                    if failure=='gap': seconds+=20
                    if failure=='pid': t.mutate=lambda args,raw:b'124\n' if 'pidof' in args else raw
                clock.sleep(seconds)
            collector=c.Collector(t,meta,meta['apk'],clock.now,sleep)
            with tempfile.TemporaryDirectory(dir=root/'build') as directory:
                out=Path(directory)/'run'
                result=c.run_campaign(collector,c.Journal(out,root))
                saved=q.load_json(out/'soak-input.json')
                self.assertEqual(result['analysis']['status'],'incomplete')
                self.assertEqual(len(saved['samples']),2)
                self.assertEqual(saved['collection_status'],'incomplete')
                with self.assertRaises(FileExistsError): c.Journal(out,root)
                self.assertIn('incomplete',(out/'events.jsonl').read_text())

    def test_122nd_point_closes_real_capture_jitter_without_shortening_duration(self):
        import collect_soak as c
        root=Path(__file__).resolve().parents[1]
        clock=Clock(); meta=metadata(); t=SyntheticTransport(clock,meta); delayed=[False]
        def mutate(args,raw):
            if '--pocket-qa-v1' in args and not delayed[0]:
                delayed[0]=True; clock.sleep(1)
            return raw
        t.mutate=mutate
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            result=c.run_campaign(c.Collector(t,meta,meta['apk'],clock.now,clock.sleep),c.Journal(Path(directory)/'run',root))
            self.assertEqual(result['points'],122)
            self.assertEqual(result['analysis']['status'],'passed')


class CliTests(unittest.TestCase):
    def test_real_cli_preflight_sigint_and_no_short_duration_option(self):
        import collect_soak as c
        import base64
        import signal
        import time
        import qualification as q
        root=Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            d=Path(directory); meta=metadata()
            from artifact_metadata import source_provenance
            meta['source']=source_provenance(root)
            metadata_path=d/'metadata.json'; metadata_path.write_text(json.dumps(meta))
            load_plan=d/'predeclared-load.json'; load_plan.write_text(json.dumps(q.SOAK_PLAN))
            clock=Clock(); t=SyntheticTransport(clock,meta); replies=[]; original=t.read
            def record(args,deadline,limit=65536):
                raw=original(args,deadline,limit)
                replies.append(dict(args=args,raw=base64.b64encode(raw).decode()))
                return raw
            t.read=record
            result=c.run_campaign(c.Collector(t,meta,meta['apk'],clock.now,clock.sleep),c.Journal(d/'prepare',root),preflight_only=True)
            self.assertEqual(result['collection_status'],'incomplete')
            transcript=d/'transcript.json'; transcript.write_text(json.dumps(replies))
            state=d/'state'; script=d/'SYNTHETIC-adb'
            script.write_text('#!'+sys.executable+'\nimport base64,json,sys\nfrom pathlib import Path\np=Path('+repr(str(state))+')\ni=int(p.read_text()) if p.exists() else 0\nr=json.loads(Path('+repr(str(transcript))+').read_text())[i]\nassert sys.argv[1:3]==["-s","SYNTHETIC"]\nassert sys.argv[3:]==r["args"], (sys.argv,r)\np.write_text(str(i+1))\nsys.stdout.buffer.write(base64.b64decode(r["raw"]))\n')
            script.chmod(0o700)
            common=['--load-plan',str(load_plan),'--repo',str(root),'--metadata',str(metadata_path),'--apk',meta['apk'],'--serial','SYNTHETIC','--adb',str(script),'--authorize-read-only']
            def cli(command,*extra):
                return subprocess.run([sys.executable,str(root/'tools/collect_soak.py'),command,*common,*extra],capture_output=True,text=True,timeout=10)
            positive=cli('preflight','--output-dir',str(d/'cli-preflight'))
            self.assertEqual(positive.returncode,1,positive.stderr)
            events=[json.loads(line) for line in (d/'cli-preflight/events.jsonl').read_text().splitlines()]
            self.assertEqual(sum(e['kind']=='preflight-observation' for e in events),1)
            self.assertIn('preflight only',(d/'cli-preflight/soak-input.json').read_text())
            state.unlink()
            from test_android_instrumentation import wire
            active_replies=copy.deepcopy(replies)
            for reply in active_replies:
                if '--proto' in reply['args']: reply['raw']=base64.b64encode(wire(True)).decode()
            transcript.write_text(json.dumps(active_replies))
            active=cli('preflight','--output-dir',str(d/'cli-active'))
            self.assertEqual(active.returncode,1,active.stderr)
            self.assertIn('OS-visible instrumentation active',q.load_json(d/'cli-active/soak-input.json')['collection_error'])
            transcript.write_text(json.dumps(replies))
            bad_plan=d/'bad-plan.json'; bad_plan.write_text(json.dumps(dict(q.SOAK_PLAN,expected_hz=[1,2,2,2])))
            divergent=cli('collect','--output-dir',str(d/'divergent'),'--load-plan',str(bad_plan))
            self.assertEqual(divergent.returncode,2)
            self.assertIn('divergent workload',divergent.stderr)
            self.assertFalse((d/'divergent').exists())
            bad_plan.write_text(json.dumps(dict(q.SOAK_PLAN,graph_required=1)))
            mistyped=cli('preflight','--output-dir',str(d/'mistyped-plan'),'--load-plan',str(bad_plan))
            self.assertEqual(mistyped.returncode,2,mistyped.stdout)
            self.assertIn('divergent workload',mistyped.stderr)
            negative=cli('collect','--output-dir',str(d/'short'),'--duration-s','1')
            self.assertEqual(negative.returncode,2)
            self.assertFalse((d/'short').exists())
            state.unlink()
            interrupted=d/'cli-interrupted'
            proc=subprocess.Popen([sys.executable,str(root/'tools/collect_soak.py'),'collect',*common,'--output-dir',str(interrupted)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            try:
                deadline=time.monotonic()+8
                # Baseline is the prefix before the first OS observation in preflight.
                first_os=next(i for i,r in enumerate(replies) if '--proto' in r['args'])
                while time.monotonic()<deadline:
                    if state.exists() and int(state.read_text() or '0')>=first_os-4: break
                    time.sleep(.02)
                else: self.fail('CLI failed to reach baseline before bounded signal')
                proc.send_signal(signal.SIGINT)
                stdout,stderr=proc.communicate(timeout=3)
                self.assertEqual(proc.returncode,1,stderr)
                self.assertNotIn('Traceback',stderr)
                saved=q.load_json(interrupted/'soak-input.json')
                self.assertEqual(saved['collection_status'],'incomplete')
                self.assertIn('interrupted',saved['collection_error'])
            finally:
                if proc.poll() is None: proc.kill(); proc.communicate()
            if os.environ.get('SOAK_CONTROL_EVIDENCE'):
                import shutil
                destination=Path(os.environ['SOAK_CONTROL_EVIDENCE'])/'synthetic-cli'
                destination.mkdir(parents=True,exist_ok=True)
                for name in ('cli-preflight','cli-active','cli-interrupted'):
                    shutil.copytree(d/name,destination/name,dirs_exist_ok=True)
                (destination/'results.json').write_text(json.dumps(dict(
                    classification='synthetic host CLI; no actual ADB/device',preflight=dict(exit=positive.returncode,stdout=positive.stdout,stderr=positive.stderr),
                    active_os=dict(exit=active.returncode,stdout=active.stdout,stderr=active.stderr),
                    divergent_load=dict(exit=divergent.returncode,stderr=divergent.stderr),
                    short_option=dict(exit=negative.returncode,stderr=negative.stderr),sigint=dict(exit=proc.returncode,stdout=stdout,stderr=stderr)),indent=2))
            print('SYNTHETIC real CLI: preflight exit=1, short option exit=2, SIGINT exit=1; no device used')

    def test_invalid_metadata_and_unchanged_on_binary_downgrade_rejected_before_reads(self):
        import qualification as q
        root=Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            d=Path(directory)
            on=diagnostic_metadata(); downgraded=metadata()
            plan=d/'load.json'; plan.write_text(json.dumps(q.SOAK_PLAN))
            downgraded.update(apk=on['apk'],sha256=on['sha256'],size_bytes=on['size_bytes'])
            for name,raw in [('broken','{"samples":['),('downgraded',json.dumps(downgraded))]:
                path=d/(name+'.json'); path.write_text(raw)
                result=subprocess.run([sys.executable,str(root/'tools/collect_soak.py'),'preflight',
                    '--metadata',str(path),'--load-plan',str(plan),'--apk',on['apk'],'--serial','SYNTHETIC',
                    '--adb',str(d/'MUST-NOT-EXECUTE'),'--authorize-read-only','--output-dir',str(d/name)],
                    capture_output=True,text=True,timeout=5)
                self.assertEqual(result.returncode,2,result.stderr)
                self.assertNotIn('Traceback',result.stderr)
                self.assertFalse((d/name).exists())


class TransportTests(unittest.TestCase):
    def test_journal_overflow_rejection_keeps_terminal_incomplete_result(self):
        import collect_soak as c
        import qualification as q
        root=Path(__file__).resolve().parents[1]
        meta=metadata()

        class FailingCollector:
            metadata=meta
            apk=meta['apk']
            def preflight(self):
                raise ValueError('synthetic collection failure')

        limit=64*1024
        filler_overhead=len((json.dumps(dict(kind='filler',padding=''),separators=(',',':'),allow_nan=False)+'\n').encode())
        filler='x'*(limit-filler_overhead-1)

        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            with mock.patch.object(c,'JOURNAL_MAX_BYTES',limit):
                reject=Path(directory)/'reject'
                journal=c.Journal(reject,root)
                journal.event('filler',padding=filler)
                with self.assertRaisesRegex(ValueError,'progress journal budget exceeded'):
                    journal.event('overflow',padding='x')
                self.assertLessEqual(journal.bytes,limit)
                journal.close()

                terminal=Path(directory)/'terminal'
                journal=c.Journal(terminal,root)
                journal.event('filler',padding=filler)
                result=c.run_campaign(FailingCollector(),journal,preflight_only=True)
                self.assertEqual(result['collection_status'],'incomplete')
                saved=q.load_json(terminal/'soak-input.json')
                self.assertEqual(saved['collection_status'],'incomplete')
                self.assertIn('progress journal budget exceeded',saved['collection_error'])
                self.assertEqual(q.load_json(terminal/'analysis.json')['status'],'incomplete')

    def test_real_subprocess_timeout_output_bounds_stderr_and_exit(self):
        import collect_soak as c
        import time
        with tempfile.TemporaryDirectory() as directory:
            script=Path(directory)/'synthetic-adb'
            script.write_text('#!'+sys.executable+'\nimport sys,time\nmode=sys.argv[-1]\nif mode=="timeout": time.sleep(10)\nelif mode=="huge": sys.stdout.write("x"*100000)\nelif mode=="stderr": sys.stderr.write("error")\nelif mode=="exit": sys.exit(3)\nelse: print("SYNTHETIC")\n')
            script.chmod(0o700)
            transport=c.Adb(script,'SYNTHETIC-ONLY')
            self.assertEqual(transport.read(['ok'],time.monotonic_ns()+1_000_000_000),b'SYNTHETIC\n')
            root=Path(__file__).resolve().parents[1]
            with tempfile.TemporaryDirectory(dir=root/'build') as run:
                journal=c.Journal(Path(run)/'raw',root)
                transport.journal=journal
                transport.read(['ok'],time.monotonic_ns()+1_000_000_000)
                journal.close()
                record=json.loads((Path(run)/'raw/events.jsonl').read_text())
                self.assertEqual(__import__('base64').b64decode(record['input_b64']),b'SYNTHETIC\n')
            transport.journal=None
            for mode in ('timeout','huge','stderr','exit'):
                start=time.monotonic()
                with self.subTest(mode=mode), self.assertRaises((ValueError,OSError)):
                    transport.read([mode],time.monotonic_ns()+200_000_000,1024)
                self.assertLess(time.monotonic()-start,1.5)


if __name__ == '__main__': unittest.main()
