#!/usr/bin/env python3
"""Front-13 run-owned sequenced ROS fixture. No ROS import on input validation.

The host campaign embeds this genuine rclpy producer in its network-none
container. --publish is for an explicitly authorized future isolated tablet bench;
no commands/services/actions are published. All four messages are String sequences.
"""
import argparse
from collections import deque
import json
import re
import threading
import time


def profiles(owner):
    if not isinstance(owner, str) or re.fullmatch('[0-9a-f]{32}', owner) is None:
        raise ValueError('owner must be a fresh lowercase UUID hex')
    return [dict(slot=i, topic=f'/pocket_multiwatch/run_{owner}/watch{i}',
                 rateHz=rate, reliable=i in (1, 3), transientLocal=i == 3)
            for i, rate in enumerate((1, 2, 5, 10))]


def log_sequence(snapshot, owner):
    logs = snapshot['rosout']
    assert logs['active'] is True and logs['error'] == '', logs
    return max((e['line'] for e in logs['entries'] if e['logger'] == 'mw13.' + owner), default=0)


def verify_survivors(before, after, slots, owner):
    assert before['sessionGeneration'] == after['sessionGeneration'], 'session replaced'
    assert len(after['watches']) == 4
    for i in slots:
        a, b = before['watches'][i], after['watches'][i]
        assert type(b['slot']) is int and b['slot'] == i
        assert b['generation'] == a['generation'], ('replaced survivor', i)
        assert b['readerActive'] is True and b['error'] == '', ('inactive survivor', i)
        assert type(b['count']) is int and b['count'] > a['count'], ('stalled survivor', i)
    assert log_sequence(after, owner) > log_sequence(before, owner), 'stalled rosout'


def verify_negative(events, observations, owner, gid, history_fence_ns):
    """HOST same-Linux-clock evidence only; never compare to Android nanoTime.

    Freeze actual publication history FIRST; command a subsequent native poll.
    Zero intake must persist on that exact reader across >=5 s, with contiguous
    sequenced publications <=1.5 s apart throughout the observed source window.
    """
    topic = profiles(owner)[0]['topic']
    assert re.fullmatch('[0-9a-f]{32}', gid)
    assert len(observations) >= 3 and len(events) >= 6
    first = observations[0]['snapshot']
    identity = (first['sessionGeneration'], first['watches'][0]['generation'])
    last_capture = -1
    for o in observations:
        assert type(o['capturedNs']) is int and last_capture < o['capturedNs'] <= o['exportedNs']
        last_capture = o['capturedNs']
        s, w = o['snapshot'], o['snapshot']['watches'][0]
        advertised = [e for t in s['topics'] if t['wireName'] == 'rt'+topic
                      for e in t['endpoints'] if e['writer'] is True]
        assert len(advertised) == 1 and advertised[0]['id'] == gid, 'writer not discovered by actual Session'
        assert '/multiwatch13_'+owner in advertised[0]['nodes'], 'unowned discovered writer'
        assert advertised[0]['qos']['reliability'] == 'best_effort'
        assert (s['sessionGeneration'], w['generation']) == identity
        expected = dict(count=0, matched=0, readerActive=True, fresh=False, age=-1,
                        hz=0, last='', error='', subscription=topic,
                        qos='reliable / volatile', subscriptionState='waiting')
        for k, value in expected.items():
            assert type(w[k]) is type(value) or (k in ('age', 'hz') and type(w[k]) in (int, float)), (k, w)
            assert w[k] == value, (k, w)
    assert observations[-1]['capturedNs']-observations[0]['capturedNs'] >= 5_000_000_000
    for e in events:
        assert e['owner'] == owner and e['topic'] == topic and e['writerGid'] == gid
        assert e['reliability'] == 'best_effort'
        assert type(e['sequence']) is int and type(e['monotonicNs']) is int
    for a, b in zip(events, events[1:]):
        assert b['sequence'] == a['sequence']+1
        assert 0 < b['monotonicNs']-a['monotonicNs'] <= 1_500_000_000, 'publication gap'
    assert events[0]['monotonicNs'] >= observations[0]['capturedNs']
    assert events[-1]['monotonicNs']-events[0]['monotonicNs'] >= 5_000_000_000
    assert events[-1]['monotonicNs'] <= history_fence_ns < observations[-1]['capturedNs'], 'closing fence'
    return dict(writerGid=gid, publicationSpanNs=events[-1]['monotonicNs']-events[0]['monotonicNs'],
                captureSpanNs=observations[-1]['capturedNs']-observations[0]['capturedNs'],
                closingCaptureNs=observations[-1]['capturedNs'], historyFenceNs=history_fence_ns)


class Producer:
    """Genuine rclpy timers and typed writers; bounded metadata history only."""
    def __init__(self, owner, domain=67, emit=False):
        self.owner, self.specs, self.emit = owner, profiles(owner), emit
        if type(domain) is not int or domain not in (67, 68):
            raise ValueError('reserved fixture domain 67/68 required')
        self.domain = domain
        self.lock = threading.Lock()
        self.events = deque(maxlen=4096)
        self.counts = [0]*4
        self.paused = [False]*4
        self.logs_paused = False
        self.log_count = 0
        self.errors = []
        self.thread = None
        self.node = None
        self.rclpy = None
        self.gids = []

    def __enter__(self):
        import rclpy
        from rclpy.qos import QoSProfile, ReliabilityPolicy, DurabilityPolicy
        from std_msgs.msg import String
        from rcl_interfaces.msg import Log
        self.rclpy = rclpy
        rclpy.init(domain_id=self.domain)
        try:
            self.node = rclpy.create_node('multiwatch13_'+self.owner, enable_rosout=False)
            self.publishers = []
            self.timers = []
            for p in self.specs:
                qos = QoSProfile(depth=64,
                    reliability=ReliabilityPolicy.RELIABLE if p['reliable'] else ReliabilityPolicy.BEST_EFFORT,
                    durability=DurabilityPolicy.TRANSIENT_LOCAL if p['transientLocal'] else DurabilityPolicy.VOLATILE)
                pub = self.node.create_publisher(String, p['topic'], qos)
                self.publishers.append(pub)
                def send(i=p['slot'], pub=pub, p=p):
                    with self.lock:
                        if self.paused[i]: return
                        self.counts[i] += 1
                        sample = String()
                        sample.data = f"{self.owner}:{i}:{self.counts[i]}"
                        pub.publish(sample)
                        event = dict(owner=self.owner, topic=p['topic'], sequence=self.counts[i],
                            monotonicNs=time.monotonic_ns(), writerGid=self.gids[i],
                            reliability='reliable' if p['reliable'] else 'best_effort')
                        self.events.append(event)
                        if self.emit: print('MULTIWATCH13_EVENT '+json.dumps(event), flush=True)
                self.timers.append(self.node.create_timer(1/p['rateHz'], send))
            # Self endpoint lookup is authoritative and includes the real RMW GID.
            deadline = time.monotonic()+5
            for p in self.specs:
                while True:
                    endpoints = [e for e in self.node.get_publishers_info_by_topic(p['topic'])
                                 if e.node_name == self.node.get_name()]
                    if len(endpoints) == 1:
                        self.gids.append(bytes(endpoints[0].endpoint_gid).hex())
                        break
                    if time.monotonic() >= deadline: raise RuntimeError('own writer GID unavailable')
                    time.sleep(.05)
            self.log_pub = self.node.create_publisher(Log, '/rosout', QoSProfile(depth=128,
                reliability=ReliabilityPolicy.RELIABLE, durability=DurabilityPolicy.VOLATILE))
            def send_log():
                with self.lock:
                    if self.logs_paused: return
                    self.log_count += 1
                    sample = Log()
                    sample.stamp = self.node.get_clock().now().to_msg()
                    sample.level = 20
                    sample.name = 'mw13.'+self.owner
                    sample.msg = f'multiwatch13 λ {self.log_count}'
                    sample.line = self.log_count
                    self.log_pub.publish(sample)
            self.timers.append(self.node.create_timer(.2, send_log))
            def spin():
                try: rclpy.spin(self.node)
                except rclpy.executors.ExternalShutdownException: pass
                except BaseException as ex: self.errors.append(repr(ex))
            self.thread = threading.Thread(target=spin, daemon=True)
            self.thread.start()
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def history(self, slot=0, since=0):
        with self.lock:
            return [e.copy() for e in self.events
                    if e['topic'] == self.specs[slot]['topic'] and e['monotonicNs'] >= since]

    def __exit__(self, *unused):
        if self.rclpy is not None and self.rclpy.ok(): self.rclpy.shutdown()
        if self.thread is not None:
            self.thread.join(timeout=5)
            if self.thread.is_alive(): raise RuntimeError('fixture executor cleanup deadline')
        if self.node is not None: self.node.destroy_node()


class NativeTransport:
    """The standalone host adapter is not Android JNI or Activity execution."""
    def __init__(self, binary):
        import subprocess
        self.process = subprocess.Popen([binary, '67'], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, text=True, bufsize=1)
        assert self._receive()['ok'] is True

    def _receive(self):
        import selectors
        with selectors.DefaultSelector() as selector:
            selector.register(self.process.stdout, selectors.EVENT_READ)
            if not selector.select(5): raise RuntimeError('native adapter timeout')
        line = self.process.stdout.readline(262145)
        if not line or len(line) > 262144: raise RuntimeError('native adapter EOF/output bound')
        return json.loads(line)

    def command(self, text):
        self.process.stdin.write(text+'\n')
        self.process.stdin.flush()
        return self._receive()

    def close(self):
        try:
            if self.process.poll() is None:
                self.process.stdin.write('exit\n')
                self.process.stdin.flush()
                self.process.wait(timeout=5)
        finally:
            if self.process.poll() is None:
                self.process.kill()
                self.process.wait(timeout=5)
            self.process.stdin.close()
            self.process.stdout.close()


def host_main(binary):
    import uuid
    owner = uuid.uuid4().hex
    with Producer(owner) as producer:
        transport = NativeTransport(binary)
        try: return campaign(transport, producer)
        finally: transport.close()


def campaign(transport, producer, clock=time):
    """Public host campaign entrypoint. Injected transports are synthetic controls.

    All reads/actions use the production Session adapter in the real DDS gate.
    Deadline is absolute. Every recovery requires new fixture sequence advancement.
    """
    owner, specs = producer.owner, producer.specs
    cases = []
    deadline = clock.monotonic()+150
    def command(text):
        assert clock.monotonic() < deadline, 'campaign deadline'
        assert not producer.errors, producer.errors
        value = transport.command(text)
        print('MULTIWATCH13_TRACE '+json.dumps(dict(command=text, value=value)), flush=True)
        return value
    def poll(): return command('poll')
    def snapshot(): return poll()['snapshot']
    def until(predicate, seconds=12):
        end = min(deadline, clock.monotonic()+seconds)
        while clock.monotonic() < end:
            value = snapshot()
            if predicate(value): return value
            clock.sleep(.5)
        raise AssertionError(('intake condition timeout', value))
    def watch(slot, profile=None, reliable=None):
        p = specs[slot] if profile is None else specs[profile]
        r = p['reliable'] if reliable is None else reliable
        assert command(f"watch {slot} rt{p['topic']} {int(r)} {int(p['transientLocal'])}")['ok'] is True
    def live(s):
        return all(w['readerActive'] is True and w['count'] > 1 and w['error'] == ''
                   and w['fresh'] is True for w in s['watches']) and log_sequence(s, owner)>0
    def observe(label, before, healthy, check=lambda s: None, seconds=2.2, logs=True):
        start = clock.monotonic()
        while True:
            after = snapshot()
            check(after)
            if clock.monotonic()-start >= seconds: break
            clock.sleep(.5)
        if logs: verify_survivors(before, after, healthy, owner)
        else:
            # Clear/pause is the sole case where new rosout is deliberately absent.
            assert before['sessionGeneration'] == after['sessionGeneration']
            for i in healthy:
                a,b = before['watches'][i], after['watches'][i]
                assert b['generation'] == a['generation'] and b['count'] > a['count']
                assert b['readerActive'] is True and b['error'] == ''
        cases.append(label)
        return after
    def inactive(s, slot, stage=None):
        w = s['watches'][slot]
        for k,v in dict(readerActive=False, fresh=False, count=0, matched=0,
                        last='', subscription='', hz=0, age=-1, message=None,
                        numericFields=[], receivedMonotonicNs=None, sourceTimestampNs=None).items():
            assert w[k] == v and not (type(w[k]) is bool and type(v) is int), (k,w)
        assert w['subscriptionState'] == ('error' if stage else 'inactive')
        if stage:
            prefix = 'Falha ao criar topico:' if stage == 'topic' else 'Falha ao assinar:'
            assert w['error'].startswith(prefix) and 'Bad Parameter' in w['error']
        else: assert w['error'] == ''
    def recovery(slot, old_sequence):
        first = until(lambda s: s['watches'][slot]['count']>0 and
            int(s['watches'][slot]['last'].split(':')[-1])>old_sequence)
        w = first['watches'][slot]
        return until(lambda s: s['watches'][slot]['generation']==w['generation'] and
            s['watches'][slot]['count']>w['count'] and
            s['watches'][slot]['receivedMonotonicNs']>w['receivedMonotonicNs'] and
            s['watches'][slot]['last']!=w['last'])
    for i in range(4): watch(i)
    before = until(live)
    for i,p in enumerate(specs):
        assert before['watches'][i]['subscription'] == p['topic']
    # Intake cadence is the actual app cadence, not a high-rate test-only poll.
    start = clock.monotonic()
    while clock.monotonic()-start < 12:
        clock.sleep(.5)
        after = snapshot()
    elapsed = clock.monotonic()-start
    rates = [(after['watches'][i]['count']-before['watches'][i]['count'])/elapsed for i in range(4)]
    assert all(.8*p['rateHz'] <= rate <= 1.2*p['rateHz'] for p,rate in zip(specs,rates)), rates
    verify_survivors(before, after, range(4), owner)
    cases.append('mixed_rates')
    before = after
    assert command(f"watch 4 rt{specs[0]['topic']} 0 0")['ok'] is False
    def preserved(s):
        for a,b in zip(before['watches'], s['watches']):
            for k in ('slot','generation','subscription','type','qos','readerActive','error'):
                assert a[k] == b[k], ('fifth mutated existing watch',k)
    observe('fifth_rejected', before, range(4), preserved)
    before = snapshot()
    watch(0, reliable=True)
    observations = [poll()]
    start_capture = observations[0]['capturedNs']
    while clock.monotonic_ns()-start_capture < 7_000_000_000:
        clock.sleep(.5)
        observations.append(poll())
    # Freeze producer history, then export a strictly newer native capture.
    history = producer.history(since=start_capture)
    fence = clock.monotonic_ns()
    observations.append(poll())
    print('MULTIWATCH13_PUBLICATIONS '+json.dumps(history), flush=True)
    negative = verify_negative(history, observations, owner, producer.gids[0], fence)
    verify_survivors(before, observations[-1]['snapshot'], (1,2,3), owner)
    cases.append('negative_qos')
    old_sequence = int(before['watches'][0]['last'].split(':')[-1])
    watch(0, reliable=False)
    recovery(0,old_sequence)
    cases.append('qos_recovery')
    receipts = []
    for slot in (0,2):
        for stage in ('topic','reader'):
            before = until(live)
            old_sequence = int(before['watches'][slot]['last'].split(':')[-1])
            arm = command(f'arm {slot} {stage}')
            assert arm['ok'] is True
            p = specs[slot]
            assert command(f"watch {slot} rt{p['topic']} {int(p['reliable'])} {int(p['transientLocal'])}")['ok'] is False
            receipt = command('receipt')
            assert receipt['session'] == before['sessionGeneration'] == arm['session']
            assert receipt['slot'] == receipt['targetSlot'] == slot
            assert receipt['attemptedGeneration'] == before['watches'][slot]['generation']+1
            assert receipt['request'] == arm['request'] and receipt['nonce'] == arm['nonce']
            assert receipt['stage'] == stage and receipt['topicApi'] == 'dds_create_topic'
            if stage == 'topic':
                assert receipt['topicReturn'] == -3 and receipt['readerReturn'] is None
                assert receipt['readerApi'] is None and receipt['topicArgumentOverridden'] is True
                assert receipt['topicName'] == 'rt/pocket_test/invalid topic'
            else:
                assert receipt['topicReturn'] > 0 and receipt['readerReturn'] == -3
                assert receipt['readerApi'] == 'dds_create_reader' and receipt['readerParent'] == 0
                assert receipt['readerArgumentOverridden'] is True
            receipts.append(receipt)
            def failed(s):
                inactive(s,slot,stage)
                assert s['watches'][slot]['generation'] == receipt['attemptedGeneration']
                assert command('receipt') == receipt
            observe(f'{stage}_failure_{slot}', before, [i for i in range(4) if i!=slot], failed)
            watch(slot)
            recovered = recovery(slot,old_sequence)
            assert recovered['watches'][slot]['generation'] == receipt['attemptedGeneration']+1
            assert command('receipt') is None
    watch(0,profile=1,reliable=False)
    before = until(live)
    def same_topic(s):
        a,b = s['watches'][:2]
        assert a['subscription'] == b['subscription'] == specs[1]['topic']
        assert a['qos'] == 'best_effort / volatile' and b['qos'] == 'reliable / volatile'
    before = observe('same_topic',before,range(4),same_topic)
    assert command('remove 0')['ok'] is True
    assert command('remove 0')['ok'] is True
    observe('remove_same_topic',before,(1,2,3),lambda s: inactive(s,0))
    with producer.lock: old_sequence = producer.counts[0]
    watch(0)
    recovery(0,old_sequence)
    cases.append('recreate')
    with producer.lock: producer.logs_paused = True
    clock.sleep(.5)
    before = snapshot()
    assert command('clear_rosout')['ok'] is True
    def cleared(s):
        assert s['rosout']['active'] is True and s['rosout']['count'] == 0 and s['rosout']['entries'] == []
    observe('rosout_clear',before,range(4),cleared,logs=False)
    with producer.lock: producer.logs_paused = False
    until(lambda s: log_sequence(s,owner)>log_sequence(before,owner))
    stopped = command('stop')
    assert stopped['running'] is False and stopped['rosout']['active'] is False
    assert stopped['rosout']['entries'] == []
    for i in range(4): inactive(stopped,i)
    cases.append('stop')
    return dict(classification='host-isolated-real-dds-not-android', cases=cases, owner=owner,
                sourceHz=[p['rateHz'] for p in specs], receivedHz=rates, measuredSeconds=elapsed,
                negativeWindow=negative, receipts=receipts)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--owner', required=True)
    parser.add_argument('--domain', required=True, type=int, choices=range(67, 69))
    parser.add_argument('--seconds', type=int, default=240, choices=range(30, 601))
    parser.add_argument('--publish', action='store_true', required=True)
    args = parser.parse_args()
    profiles(args.owner)
    with Producer(args.owner, args.domain, emit=True):
        time.sleep(args.seconds)
