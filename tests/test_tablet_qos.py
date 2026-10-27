"""Synthetic host oracles; never physical/tablet evidence."""
import copy
import os
import unittest
from unittest.mock import patch
with patch.dict(os.environ,{'POCKET_TABLET':'HOST_QOS_DOUBLE'}):
    import tablet_ui as ui
import tablet_regression as regression


def waiting_nodes():
    return [{'package':ui.PKG,'resource-id':ui.PKG+':id/counts',
             'text':'AGUARDANDO · 0 msgs\n0 publicador(es) compatível(is) · idade —'}]


def waiting_qa(captured=1000000000):
    inactive=lambda slot: dict(slot=slot,valid=True)
    watch=dict(slot=0,valid=True,requestedReliable=True,requestedTransientLocal=False,
               pending=False,requestVersion=3,appliedVersion=3,readerGeneration=4,
               readerActive=True,state='waiting',count=0,matched=0,hasError=False,
               nativeFresh=False,freshAtExport=False,receivedHz=0.0,sampleAgeAtCaptureSeconds=-1)
    return dict(schemaVersion=1,valid=True,foreground=True,clock='java.nanoTime',
                applicationId=ui.PKG,debuggable=True,pid=100,
                captureAgeNs=100,maxCaptureAgeNs=2000000000,
                capturedMonotonicNs=captured,exportedMonotonicNs=captured+100,
                lifecycleGeneration=1,policyGeneration=2,sessionGeneration=3,
                watches=[watch,inactive(1),inactive(2),inactive(3)])


class StrictQaTests(unittest.TestCase):
    def test_bool_cannot_impersonate_a_zero_intake_count(self):
        good=waiting_qa()
        self.assertTrue(regression.reliable_unmatched_sample(waiting_nodes(),good))
        good['watches'][0]['count']=False
        self.assertFalse(regression.reliable_unmatched_sample(waiting_nodes(),good))


    def test_actual_ui_first_line_with_hz_is_a_valid_waiting_sample(self):
        nodes=waiting_nodes()
        nodes[0]['text']=nodes[0]['text'].replace('0 msgs','0 msgs · 0.0 Hz recebidos')+'\nreliable / volatile'
        self.assertTrue(regression.reliable_unmatched_sample(nodes,waiting_qa()))

    def test_malformed_qa_is_rejected_without_exceptions(self):
        variants=[None,[],True,{},dict(waiting_qa(),watches=None),dict(waiting_qa(),watches=[None]*4)]
        for field in ('schemaVersion','pid','captureAgeNs','maxCaptureAgeNs','capturedMonotonicNs','exportedMonotonicNs','lifecycleGeneration','policyGeneration','sessionGeneration'):
            for value in (None,True,False,'1',1.0,-1,1<<63):variants.append(dict(waiting_qa(),**{field:value}))
        for field in ('slot','count','matched','requestVersion','appliedVersion','readerGeneration'):
            for value in (None,True,False,'0',0.0,-1,1<<63):
                sample=waiting_qa();sample['watches'][0][field]=value;variants.append(sample)
        for sample in variants:
            with self.subTest(sample=sample):self.assertFalse(regression.reliable_unmatched_sample(waiting_nodes(),sample))


class PublicationWindowTests(unittest.TestCase):
    owner='HOST_QOS_OWNER'
    gid='ab'*16
    def events(self,times=None):
        times=times if times is not None else range(0,7100000000,200000000)
        return [dict(sequence=i+1,monotonicNs=stamp,writerGid=self.gid,owner=self.owner,
                     topic='/pocket_test/harness_'+self.owner+'/chatter',node='/pocket_fixture_'+self.owner,
                     reliability='best_effort') for i,stamp in enumerate(times)]

    def test_sparse_polling_cannot_hide_a_publication_gap(self):
        # Four observations are <1.5s apart. Actual publisher stops for 2s,
        # then bursts; an increasing sequence-only oracle used to hide this.
        events=self.events([0,100000000,200000000,300000000,2400000000,2500000000,2600000000,2700000000,2800000000])
        with patch.object(regression,'FIXTURE_OWNER',self.owner):
            state={}
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events[:4],400000000,waiting_qa(),self.gid))
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events,2800000000,waiting_qa(2000000000),self.gid))

    def test_publication_coverage_cannot_outlast_the_last_zero_intake_capture(self):
        with patch.object(regression,'FIXTURE_OWNER',self.owner):
            state={};events=self.events()
            regression.sustained_qos_unmatched(waiting_nodes(),state,events[:2],250000000,waiting_qa(),self.gid)
            regression.sustained_qos_unmatched(waiting_nodes(),state,events[:12],2300000000,waiting_qa(2000000000),self.gid)
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events,7100000000,waiting_qa(3000000000),self.gid))

    def test_intake_during_slow_fixture_read_is_not_hidden_by_earlier_qa(self):
        from types import SimpleNamespace
        clock=[250000000];delay=[False]
        def qa():
            sample=waiting_qa(clock[0])
            if clock[0]>=4000000000:sample['watches'][0]['count']=1
            return sample
        def logs():
            if delay[0]:clock[0]=6200000000
            return self.events(range(0,clock[0]+1,200000000))
        with patch.object(regression,'FIXTURE_OWNER',self.owner),patch.object(regression.ui,'client',return_value=SimpleNamespace(qa=qa)),patch.object(regression,'fixture_heartbeats',side_effect=logs),patch.object(regression.time,'monotonic_ns',side_effect=lambda:clock[0]):
            state={}
            self.assertFalse(regression.observe_qos_window(waiting_nodes(),state,self.gid))
            clock[0]=1500000000
            self.assertFalse(regression.observe_qos_window(waiting_nodes(),state,self.gid))
            clock[0]=3000000000;delay[0]=True
            self.assertFalse(regression.observe_qos_window(waiting_nodes(),state,self.gid))
            self.assertEqual(state,{})

    def test_complete_contiguous_event_history_needs_a_post_export_closing_capture(self):
        with patch.object(regression,'FIXTURE_OWNER',self.owner):
            state={};events=self.events()
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events[:2],250000000,waiting_qa(),self.gid))
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events[:12],2300000000,waiting_qa(3050000000),self.gid))
            # Publication coverage is now sufficient, but closure is NOT.
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events,7100000000,waiting_qa(7850000000),self.gid))
            fence=state['closingFenceNs']
            # A newer cache captured BEFORE that export still cannot close.
            self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events,7300000000,waiting_qa(fence-50),self.gid))
            result=regression.sustained_qos_unmatched(waiting_nodes(),state,self.events(range(0,8100000000,200000000)),8100000000,waiting_qa(8850000000),self.gid)
            self.assertTrue(result)
            self.assertGreaterEqual(result['publicationSpanNs'],5000000000)
            self.assertGreaterEqual(result['qaCaptureSpanNs'],5000000000)
            self.assertGreater(result['closingCaptureNs'],result['closingFenceNs'])
            self.assertEqual(result['sequenceEnd'],events[-1]['sequence']) # frozen producer interval

    def test_event_history_rejects_stale_future_gapped_and_foreign_events(self):
        cases={
            'missing_event':lambda events:events[:8]+events[9:],
            'foreign_gid':lambda events:[dict(e,writerGid='cd'*16) for e in events],
            'foreign_owner':lambda events:[dict(e,owner='other') for e in events],
            'wrong_qos':lambda events:[dict(e,reliability='reliable') for e in events],
            'actual_gap':lambda events:[dict(e,monotonicNs=e['monotonicNs']+(2000000000 if i>5 else 0)) for i,e in enumerate(events)],
        }
        with patch.object(regression,'FIXTURE_OWNER',self.owner):
            for label,mutate in cases.items():
                with self.subTest(label=label):
                    state={};events=self.events()
                    regression.sustained_qos_unmatched(waiting_nodes(),state,events[:2],250000000,waiting_qa(),self.gid)
                    regression.sustained_qos_unmatched(waiting_nodes(),state,events[:12],2300000000,waiting_qa(2000000000),self.gid)
                    damaged=mutate(events)
                    self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,damaged,damaged[-1]['monotonicNs']+100000000,waiting_qa(3000000000),self.gid))
                    self.assertEqual(state,{})
            for now in (6000000000,10000000000):
                state={};events=self.events()
                regression.sustained_qos_unmatched(waiting_nodes(),state,events[:2],250000000,waiting_qa(),self.gid)
                self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events,now,waiting_qa(3000000000),self.gid))
                self.assertEqual(state,{})

    def test_frozen_capture_and_reader_replacement_cannot_stitch_a_window(self):
        with patch.object(regression,'FIXTURE_OWNER',self.owner):
            for field in ('capturedMonotonicNs','readerGeneration','sessionGeneration','requestVersion'):
                state={};events=self.events();qa=waiting_qa()
                regression.sustained_qos_unmatched(waiting_nodes(),state,events[:2],250000000,qa,self.gid)
                later=waiting_qa(2000000000)
                if field=='capturedMonotonicNs':later=qa
                elif field=='sessionGeneration':later[field]+=1
                else:later['watches'][0][field]+=1
                self.assertFalse(regression.sustained_qos_unmatched(waiting_nodes(),state,events,7100000000,later,self.gid))
                self.assertEqual(state,{})


class EndpointOwnershipTests(unittest.TestCase):
    owner='HOST_QOS_OWNER'
    gid='ab'*16
    def details(self,node=None,gid=None):
        node=node or '/pocket_fixture_'+self.owner
        text='/pocket_test/harness_'+self.owner+'/chatter\nstd_msgs/msg/String\nQoS anunciado:\n\nPUBLICADOR (oferecido)\nGID: '+(gid or self.gid)+'\nNó(s) por GID: '+node+'\nreliability: best_effort'
        return [{'package':ui.PKG,'resource-id':ui.PKG+':id/explorer_details','text':text}]

    def test_discovered_writer_gid_must_belong_to_the_owned_fixture_node(self):
        with patch.object(regression,'FIXTURE_OWNER',self.owner):
            topic='/pocket_test/harness_'+self.owner+'/chatter'
            self.assertEqual(regression.best_effort_offered(self.details(),topic),self.gid)
            self.assertFalse(regression.best_effort_offered(self.details(node='/other_fixture'),topic))
            self.assertFalse(regression.best_effort_offered(self.details(gid='not_a_gid'),topic))


class HeartbeatTransportTests(unittest.TestCase):
    owner='HOST_QOS_OWNER'
    cid='12'*32
    def metadata(self):
        return dict(Id=self.cid,State=dict(Running=True),Mounts=[dict(Source=str(regression.ROOT/'tests/fixture.py'))],
                    Config=dict(Labels={'io.lamggm.pocket.harness-owner':self.owner},Env=['ROS_DOMAIN_ID=67','POCKET_QOS_HEARTBEAT='+self.owner]))

    def test_logs_use_verified_immutable_id_and_malformed_protocol_blocks(self):
        import json
        from types import SimpleNamespace
        with patch.object(regression,'FIXTURE_NAME','HOST_FIXTURE'),patch.object(regression,'FIXTURE_OWNER',self.owner):
            for payload,raises in (('POCKET_QOS_EVENT {"sequence":1}\n',False),('POCKET_QOS_EVENT {bad}\n',True),('no heartbeat\n',True)):
                with self.subTest(payload=payload),patch.object(regression.subprocess,'run',side_effect=[SimpleNamespace(stdout=json.dumps([self.metadata()])),SimpleNamespace(stdout=payload)]) as run:
                    if raises:
                        with self.assertRaisesRegex(RuntimeError,'heartbeat'):regression.fixture_heartbeats()
                    else:self.assertEqual(regression.fixture_heartbeats(),[{'sequence':1}])
                    self.assertEqual(run.call_args_list[1].args[0],['docker','logs','--tail','128',self.cid])

    def test_unowned_or_stopped_fixture_never_supplies_a_heartbeat(self):
        import json
        from types import SimpleNamespace
        variants=[]
        wrong=self.metadata();wrong['Config']['Labels']={};variants.append(wrong)
        stopped=self.metadata();stopped['State']['Running']=False;variants.append(stopped)
        wrong_mount=self.metadata();wrong_mount['Mounts']=[];variants.append(wrong_mount)
        for metadata in variants:
            with self.subTest(metadata=metadata),patch.object(regression,'FIXTURE_NAME','HOST_FIXTURE'),patch.object(regression,'FIXTURE_OWNER',self.owner),patch.object(regression.subprocess,'run',return_value=SimpleNamespace(stdout=json.dumps([metadata]))) as run:
                with self.assertRaisesRegex(RuntimeError,'ownership'):regression.fixture_heartbeats()
                self.assertEqual(run.call_count,1)


class PublicQosFlowTests(unittest.TestCase):
    def test_public_run_records_sustained_evidence_not_one_waiting_frame(self):
        self._exercise_public_qos()

    def test_public_run_rejects_intake_hidden_by_slow_fixture_read(self):
        self._exercise_public_qos(delayed_receipt=True)

    def _exercise_public_qos(self,delayed_receipt=False):
        import io,tempfile
        from contextlib import ExitStack
        from pathlib import Path
        from types import SimpleNamespace
        class AfterQos(RuntimeError):pass
        clock=[1000000000];seen=[];qos_reads=[0];reader_receiving=[False]
        actual_check=regression.check
        def nodes():
            return waiting_nodes()+[{'package':ui.PKG,'resource-id':ui.PKG+':id/'+name,'text':text,'checked':'false'}
                                   for name,text in [('connect','CONECTAR'),('diagnostics','diagnostics'),('qos_reliable','Reliable'),('qos_transient','Transient local'),('explorer_favorites',''),('explorer_search',''),('explorer_namespace',''),('explorer_type','')]]
        def dump(label):clock[0]+=500000000;return nodes()
        def events():
            return PublicationWindowTests().events(range(0,clock[0]+1,200000000))[-128:]
        def owned_events():
            qos_reads[0]+=1
            if delayed_receipt and qos_reads[0]==3:
                clock[0]+=3200000000;reader_receiving[0]=True
            return [dict(e,owner=regression.FIXTURE_OWNER,topic=regression.fixture_topic('chatter'),node='/pocket_fixture_'+regression.FIXTURE_OWNER) for e in events()]
        def qa():
            sample=waiting_qa(clock[0])
            if reader_receiving[0]:sample['watches'][0]['count']=1
            return sample
        def check(name,fn,**kwargs):
            if name=='Reliable stays unmatched while fixture publishes':
                seen.append(name);return actual_check(name,fn,**kwargs)
            if seen:raise AfterQos('HOST boundary after QoS')
            if name=='DDS discovery confirms owned writer offers best_effort':return 'ab'*16
            return {'message':'pocket-real-jazzy seq=10'}
        with tempfile.TemporaryDirectory() as directory,ExitStack() as stack:
            client=SimpleNamespace(kind='synthetic_public_qos_NOT_physical',out=Path(directory),verify=lambda:None,find=lambda *args,**kw:nodes(),hide_ime=lambda:None,qa=qa)
            for obj,name,kwargs in [(ui,'client',dict(return_value=client)),(regression,'check',dict(side_effect=check)),(regression,'dump',dict(side_effect=dump)),(regression,'fixture_heartbeats',dict(side_effect=owned_events)),(regression.time,'monotonic',dict(side_effect=lambda:clock[0]/1000000000)),(regression.time,'monotonic_ns',dict(side_effect=lambda:clock[0])),(regression.time,'sleep',dict(side_effect=lambda seconds:clock.__setitem__(0,clock[0]+int(seconds*1000000000))))]:stack.enter_context(patch.object(obj,name,**kwargs))
            for name in ('launch','field','tap','adb','select_topic','fixture_start','owned_fixture_stop','disconnect_app'):stack.enter_context(patch.object(regression,name))
            stack.enter_context(patch('sys.stdout',new_callable=io.StringIO))
            try:regression.run()
            except AfterQos:
                self.assertFalse(delayed_receipt,'QoS falsely passed while intake during the slow fixture read was hidden')
            except AssertionError:
                if not delayed_receipt:raise
            else:self.fail('Public QoS run did not reach the expected bounded result')
            if delayed_receipt:
                self.assertTrue(reader_receiving[0])
                rows=[row for row in regression.results if row['test']=='Reliable stays unmatched while fixture publishes']
                self.assertEqual(len(rows),1);self.assertFalse(rows[0]['passed'])
                self.assertEqual(rows[0]['classification'],'observation_timeout')
                return
            evidence=next(row['observed'] for row in regression.results if row['test']=='Reliable stays unmatched while fixture publishes')
            self.assertGreaterEqual(evidence['publicationSpanNs'],5000000000)
            self.assertGreaterEqual(evidence['qaObservations'],3)
            self.assertEqual(evidence['writerGid'],'ab'*16)
            self.assertGreater(evidence['closingCaptureNs'],evidence['closingFenceNs'])
            self.assertGreaterEqual(evidence['qaCaptureSpanNs'],5000000000)


class BenchPeerTests(unittest.TestCase):
    def exercise_setup(self,peer,invalid=False):
        import tempfile,json
        from pathlib import Path
        from types import SimpleNamespace
        from unittest.mock import Mock
        from contextlib import ExitStack
        class SetupBoundary(RuntimeError):pass
        with tempfile.TemporaryDirectory() as d,ExitStack() as stack:
            client=SimpleNamespace(kind='synthetic_transport_test',out=Path(d),verify=Mock(),find=Mock())
            stack.enter_context(patch.dict(os.environ,{'POCKET_BENCH_PEER':peer}))
            stack.enter_context(patch.object(regression.ui,'client',return_value=client))
            stack.enter_context(patch.object(regression,'launch'))
            stack.enter_context(patch.object(regression,'dump',return_value=[{'package':ui.PKG,'resource-id':ui.PKG+':id/connect','text':'CONECTAR'}]))
            fields=stack.enter_context(patch.object(regression,'field'))
            stack.enter_context(patch.object(regression,'tap',side_effect=SetupBoundary('host setup boundary')))
            stack.enter_context(patch.object(regression,'disconnect_app'))
            error=None
            try:regression.run()
            except BaseException as exc:error=exc
            self.assertIsInstance(error,ValueError if invalid else SetupBoundary)
            report=json.loads(next(Path(d).glob('regression-results-*.json')).read_text())
            if invalid:client.verify.assert_not_called();fields.assert_not_called()
            else:
                fields.assert_any_call('peer',peer)
                self.assertEqual(report['connectionConfig'],{'peerMode':'explicit' if peer else 'automatic','peerIpv4':peer})

    def test_explicit_peer_reaches_real_public_setup(self):
        self.exercise_setup('192.0.2.10')

    def test_empty_peer_retains_automatic_discovery(self):
        self.exercise_setup('')

    def test_invalid_peer_is_rejected_before_any_device_operation(self):
        for peer in ('192.0.2.999','192.0.2.10;id','192.0.2.10/24','192.000.2.10',' 192.0.2.10','::1','host.local'):
            with self.subTest(peer=peer):self.exercise_setup(peer,invalid=True)


if __name__=='__main__':
    unittest.main()
