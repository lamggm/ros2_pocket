#!/usr/bin/env python3
"""Real rclpy -> native DDS, ONLY in tools/test.sh's private network namespace.
Budgets fixed before implementation: four slots, 64 cached samples/reader,
64 samples/slot/poll, 256 total/poll; latest text <=8192 bytes; wire sample <=64KiB; 20s measurement,
reader <25% one CPU, RSS <96MiB, RSS span <8MiB; intake 80..120% of 5/10/20/40Hz.
Host budget is not Android/tablet acceptance.
"""
import json
import os
from pathlib import Path
import selectors
import subprocess
import sys
import threading
import time
import rclpy
from rclpy.qos import QoSProfile, ReliabilityPolicy, DurabilityPolicy
from std_msgs.msg import String, Float64, Int32, Bool


def main():
    fixture_begin = time.monotonic()
    os.environ['ROS_DOMAIN_ID'] = '71'
    rclpy.init()
    node = rclpy.create_node('multiwatch_fixture')
    types = [String, Float64, Int32, Bool]
    rates = [5, 10, 20, 40]
    pubs = []
    counters = [0]*4
    paused = [False]*4
    long_text = [False]
    oversized = [False]
    def send(i):
        if paused[i]:
            return
        counters[i] += 1
        msg = types[i]()
        msg.data = ('sample α '+str(counters[i])) if i == 0 else (float(counters[i]) if i == 1 else (counters[i] if i == 2 else bool(counters[i] % 2)))
        if i==0 and long_text[0]:
            msg.data='a'+'α'*5000
        if i==0 and oversized[0]:
            msg.data='x'*70000
        pubs[i].publish(msg)
    timers = []
    for i, typ in enumerate(types):
        qos = QoSProfile(depth=64, reliability=ReliabilityPolicy.BEST_EFFORT if i in (0,2) else ReliabilityPolicy.RELIABLE,
                         durability=DurabilityPolicy.TRANSIENT_LOCAL if i == 3 else DurabilityPolicy.VOLATILE)
        pubs.append(node.create_publisher(typ, '/multiwatch/t'+str(i), qos))
        timers.append(node.create_timer(1/rates[i], lambda i=i: send(i)))
    thread = threading.Thread(target=lambda: rclpy.spin(node), daemon=True)
    thread.start()
    child = subprocess.Popen([sys.argv[1]], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    def receive():
        with selectors.DefaultSelector() as sel:
            sel.register(child.stdout, selectors.EVENT_READ)
            assert sel.select(8), 'native reader timeout'
        return json.loads(child.stdout.readline())
    def cmd(s):
        child.stdin.write(s+'\n');child.stdin.flush()
        return receive()
    def poll():
        snapshot = cmd('poll')
        watches = snapshot['watches']
        assert len(watches) == 4 and [w['slot'] for w in watches] == list(range(4)), watches
        return snapshot
    observations = []
    def observe(label, baseline, healthy, check, seconds=1.2):
        """Sustain the asserted state while every unaffected reader takes NEW data."""
        begin = time.monotonic()
        previous = baseline['watches']
        while True:
            snapshot = poll()
            check(snapshot)
            for i in healthy:
                watch = snapshot['watches'][i]
                assert watch['readerActive'] and watch['fresh'] and watch['matched'] > 0, (label, i, watch)
                assert watch['generation'] == baseline['watches'][i]['generation'], (label, i, watch)
                assert watch['count'] >= previous[i]['count'], (label, i, watch)
            previous = snapshot['watches']
            if time.monotonic() - begin >= seconds:
                break
            time.sleep(.1)
        deltas = {i: previous[i]['count'] - baseline['watches'][i]['count'] for i in healthy}
        assert all(delta > 0 for delta in deltas.values()), (label, deltas, snapshot)
        observations.append({'case': label, 'seconds': time.monotonic()-begin,
                             'healthyCountDeltas': deltas})
        return snapshot
    def until(predicate, seconds=6):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            s=poll()
            if predicate(s):
                return s
            time.sleep(.05)
        raise AssertionError(json.dumps(s))
    names=['String','Float64','Int32','Bool']
    def watch(i, r=0, t=0, topic=None, type_name=None):
        return cmd(f'watch {i} {topic or "rt/multiwatch/t"+str(i)} std_msgs::msg::dds_::{type_name or names[i]}_ {int(r)} {int(t)}')
    def inactive(snapshot, slot, *, failed=False):
        watch = snapshot['watches'][slot]
        assert not watch['readerActive'] and not watch['fresh'], watch
        assert watch['count'] == 0 and watch['matched'] == 0 and watch['age'] == -1, watch
        assert watch['last'] == '' and watch['subscription'] == '' and watch['type'] == '', watch
        assert watch['hz'] == 0, watch
        assert bool(watch['error']) == failed, watch
        assert watch['subscriptionState'] == ('error' if failed else 'inactive'), watch
    def connected(snapshot):
        assert snapshot['connectionState'] == 'connected', snapshot
    try:
        assert receive()['ok']
        assert not cmd('watch 0 rt/multiwatch/t0 std_msgs::msg::dds_::String_ True False')['ok'], 'malformed QoS must not silently become best effort'
        assert not cmd('remove not-a-slot')['ok'], 'malformed slot must not silently remove slot zero'
        for i in range(4):
            assert watch(i, i==1, i==3)['ok']
        live=until(lambda s: all(w['count']>1 and w['fresh'] for w in s['watches']))
        assert len(live['watches'])==4
        assert live['count']==live['watches'][0]['count'], 'legacy slot zero snapshot'
        assert not cmd('watch 4 rt/fifth std_msgs::msg::dds_::String_ 0 0')['ok']
        assert not cmd('remove -1')['ok']
        before=poll()
        assert watch(0, 1)['ok']
        def mismatched(snapshot):
            connected(snapshot)
            watch = snapshot['watches'][0]
            assert watch['readerActive'] and watch['subscriptionState'] == 'waiting', watch
            assert watch['qos'] == 'reliable / volatile' and watch['error'] == '', watch
            assert watch['matched'] == 0 and watch['count'] == 0 and not watch['fresh'], watch
            assert watch['last'] == '' and watch['age'] == -1, watch
            # Discovery has seen the actual incompatible writer, not merely an empty slot.
            assert any(row['wireName'] == 'rt/multiwatch/t0' and row['publishers'] > 0
                       for row in snapshot['topics']), snapshot['topics']
        observe('sustained reliable/best-effort mismatch', before, (1,2,3), mismatched, seconds=1.5)
        assert watch(0)['ok']
        until(lambda s: s['watches'][0]['count']>1 and s['watches'][0]['fresh'])
        before=poll()
        assert not watch(0,1,1,topic='rt/multiwatch/invalid#topic')['ok']
        def failed(snapshot):
            connected(snapshot)
            inactive(snapshot, 0, failed=True)
            assert snapshot['watches'][0]['qos'] == 'reliable / transient_local', snapshot['watches'][0]
        observe('failed topic creation preserves live peers', before, (1,2,3), failed)
        assert watch(0)['ok']
        until(lambda s: s['watches'][0]['fresh'])
        paused[2]=True
        stale=until(lambda s: s['watches'][2]['subscriptionState']=='stale', seconds=6)
        assert all(stale['watches'][i]['fresh'] for i in (0,1,3))
        old=stale['watches'][2]['count'];paused[2]=False
        until(lambda s: s['watches'][2]['fresh'] and s['watches'][2]['count']>old)
        before=poll()
        assert cmd('remove 1')['ok'] and cmd('remove 1')['ok']
        def removed(snapshot):
            connected(snapshot)
            inactive(snapshot, 1)
        observe('idempotent removal preserves live peers', before, (0,2,3), removed)
        assert watch(1,1)['ok']
        until(lambda s: all(w['fresh'] for w in s['watches']))
        # Same DDS topic/type, independent requested QoS and reader ownership.
        assert watch(0, topic='rt/multiwatch/t1', type_name='Float64')['ok']
        shared=until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']))
        def shared_topic(snapshot):
            connected(snapshot)
            first, peer = snapshot['watches'][:2]
            assert first['subscription'] == peer['subscription'] == '/multiwatch/t1', (first, peer)
            assert first['type'] == peer['type'] == 'std_msgs::msg::dds_::Float64_', (first, peer)
            assert first['qos'] == 'best_effort / volatile', first
            assert peer['qos'] == 'reliable / volatile', peer
        observe('same topic with different requested QoS', shared, (0,1,2,3), shared_topic)
        before=poll()
        assert cmd('remove 0')['ok'] and cmd('remove 0')['ok']
        def shared_removed(snapshot):
            connected(snapshot)
            inactive(snapshot, 0)
            peer=snapshot['watches'][1]
            assert peer['subscription'] == '/multiwatch/t1' and peer['qos'] == 'reliable / volatile', peer
        observe('same-topic peer advances after removal', before, (1,2,3), shared_removed)
        # Restore all FOUR ORIGINAL topic/type/QoS tuples before performance budgets.
        assert watch(0)['ok']
        restored=until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']))
        expected_qos=['best_effort / volatile', 'reliable / volatile',
                      'best_effort / volatile', 'best_effort / transient_local']
        for i, w in enumerate(restored['watches']):
            assert w['subscription'] == '/multiwatch/t'+str(i), w
            assert w['type'] == 'std_msgs::msg::dds_::'+names[i]+'_', w
            assert w['qos'] == expected_qos[i] and w['matched'] > 0, w
        # Poll at the app's actual 500ms intake cadence. /proc is the native child,
        # not rclpy, Docker or the Python assertion driver.
        def proc():
            stat=Path(f'/proc/{child.pid}/stat').read_text().split()
            return (int(stat[13])+int(stat[14]))/os.sysconf('SC_CLK_TCK'), int(stat[23])*os.sysconf('SC_PAGE_SIZE')
        start=poll();cpu0,rss0=proc();rss=[rss0];begin=time.monotonic()
        while time.monotonic()-begin<20:
            time.sleep(.5);end=poll();rss.append(proc()[1])
        elapsed=time.monotonic()-begin;cpu1,_=proc()
        hz=[(end['watches'][i]['count']-start['watches'][i]['count'])/elapsed for i in range(4)]
        measured={'rmw':os.environ.get('RMW_IMPLEMENTATION'),'seconds':elapsed,'cpuPercentOneCore':100*(cpu1-cpu0)/elapsed,'rssMaxBytes':max(rss),'rssSpanBytes':max(rss)-min(rss),'receivedHz':hz,'sourceHz':rates}
        print('MEASUREMENT '+json.dumps(measured),flush=True)
        assert measured['cpuPercentOneCore']<25 and max(rss)<96*1024**2 and max(rss)-min(rss)<8*1024**2
        assert all(.8*r<=h<=1.2*r for r,h in zip(rates,hz)), measured
        long_text[0]=True
        text=until(lambda s: s['watches'][0]['last'].startswith('aα'))['watches'][0]['last']
        assert len(text.encode('utf-8'))<=8192 and text=='a'+'α'*4095
        # Let queues fill, then require per-slot/global intake to remain bounded.
        before=poll();time.sleep(2.2);after=poll()
        deltas=[after['watches'][i]['count']-before['watches'][i]['count'] for i in range(4)]
        assert all(0<d<=64 for d in deltas) and sum(deltas)<=256, deltas
        paused[0]=True;time.sleep(.3);before=poll()
        oversized[0]=True;paused[0]=False;time.sleep(1);after=poll()
        assert after['watches'][0]['count']==before['watches'][0]['count'], 'oversized wire samples must not enter a watch cache'
        assert all(after['watches'][i]['count']>before['watches'][i]['count'] for i in (1,2,3))
        stopped=cmd('stop')
        assert not stopped['running']
        for i in range(4):
            inactive(stopped, i)
        print('PASS independent subscriptions, sustained QoS mismatch/recovery, fifth-slot rejection, '
              'actual DDS creation failure isolation, same-topic different-QoS ownership, '
              f'advancing survivors; fixtureSeconds={time.monotonic()-fixture_begin:.3f}', flush=True)
    finally:
        # CTest retains only the first 1024 bytes of passed-test output by default.
        # Emit the actual budget measurement before these detailed observations.
        for observation in observations:
            print('OBSERVATION '+json.dumps(observation), flush=True)
        child.stdin.write('exit\n');child.stdin.flush();child.wait(timeout=5)
        rclpy.shutdown();thread.join(timeout=5);node.destroy_node()

if __name__=='__main__':
    main()
