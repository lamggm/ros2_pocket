#!/usr/bin/env python3
"""Real rclpy -> separate native Session, only in the network-none fixture.
Latest-sample plot projection, not an every-message recorder. No UI/robot calls.
"""
import json
import os
import selectors
import subprocess
import sys
import threading
import time

import rclpy
from geometry_msgs.msg import Vector3, Quaternion, Twist, Pose
from nav_msgs.msg import Odometry
from std_msgs.msg import String, Bool, Int32, Int64, Float32, Float64
from rcl_interfaces.msg import Log
from rclpy.qos import QoSProfile, ReliabilityPolicy


def flatten(field):
    yield field
    for child in field.get('children', []):
        yield from flatten(child)


def main():
    os.environ['ROS_DOMAIN_ID'] = '71'
    rclpy.init()
    node = rclpy.create_node('plot_multiwatch_fixture', enable_rosout=False)
    classes = [Vector3, Quaternion, Twist, Odometry, Pose, String, Bool, Int32, Int64, Float32, Float64]
    lock = threading.Lock()
    paused = set()
    log_sequence = [0]
    pubs, timers = [], []

    def sample(kind):
        m = classes[kind]()
        base = 100*(kind+1)
        def vector(v):
            v.x, v.y, v.z = base+.123456789012345, float('nan'), float('-inf')
        def quat(q):
            vector(q)
            q.w = float('inf')
        if kind == 0:
            vector(m)
        elif kind == 1:
            quat(m)
        elif kind == 2:
            vector(m.linear)
            vector(m.angular)
        elif kind == 3:
            m.header.stamp.sec, m.header.stamp.nanosec = -17, 123456789
            m.header.frame_id, m.child_frame_id = 'map_ação_🤖', 'base_世界'
            vector(m.pose.pose.position)
            quat(m.pose.pose.orientation)
            vector(m.twist.twist.linear)
            vector(m.twist.twist.angular)
            m.pose.covariance = [base+i/4 for i in range(36)]
            m.twist.covariance = [-base-i/8 for i in range(36)]
        elif kind == 4:
            vector(m.position)
            quat(m.orientation)
        else:
            m.data = ['not a number: data = 99', True, -2147483647, 9007199254740993,
                      float('nan'), 42.123456789012345][kind-5]
        return m

    def send(kind):
        with lock:
            if kind not in paused:
                pubs[kind].publish(sample(kind))
    for kind, cls in enumerate(classes):
        pubs.append(node.create_publisher(cls, '/plot_multiwatch/t'+str(kind),
                    QoSProfile(depth=64, reliability=ReliabilityPolicy.BEST_EFFORT)))
        timers.append(node.create_timer(.05+.01*(kind % 4), lambda kind=kind: send(kind)))
    log_pub = node.create_publisher(Log, '/rosout', QoSProfile(depth=128, reliability=ReliabilityPolicy.RELIABLE))
    def send_log():
        log_sequence[0] += 1
        m = Log()
        m.name, m.msg, m.level, m.line = 'plot.multiwatch', 'plot live λ', 20, log_sequence[0]
        log_pub.publish(m)
    timers.append(node.create_timer(.05, send_log))
    thread = threading.Thread(target=lambda: rclpy.spin(node), daemon=True)
    thread.start()
    child = subprocess.Popen([sys.argv[1]], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    tested = set()

    def receive():
        with selectors.DefaultSelector() as selector:
            selector.register(child.stdout, selectors.EVENT_READ)
            assert selector.select(8), 'native process timeout'
        line = child.stdout.readline()
        assert line, 'native process ended'
        return json.loads(line, parse_constant=lambda token: (_ for _ in ()).throw(AssertionError(token)))
    def cmd(command):
        child.stdin.write(command+'\n')
        child.stdin.flush()
        return receive()
    def poll():
        s = cmd('poll')
        assert len(s['watches']) == 4
        keys = ['message', 'count', 'last', 'subscription']
        if all(w['count'] > 1 for w in s['watches']):
            keys += ['receivedMonotonicNs', 'sourceTimestampNs', 'numericFields']
        for key in keys:
            assert key in s and key in s['watches'][0], 'missing plot contract after actual four-watch intake: '+key
            assert s[key] == s['watches'][0][key], 'root must be exactly slot zero: '+key
        return s
    def logs(s):
        return max((e['line'] for e in s['rosout']['entries'] if e['logger'] == 'plot.multiwatch'), default=0)
    def until(predicate, seconds=8):
        deadline = time.monotonic()+seconds
        while time.monotonic() < deadline:
            s = poll()
            if predicate(s):
                return s
            time.sleep(.05)
        raise AssertionError(json.dumps(s, ensure_ascii=False))
    def watch(slot, kind, reliable=0, topic=None):
        pkg = 'nav_msgs' if kind == 3 else 'geometry_msgs' if kind < 5 else 'std_msgs'
        return cmd(f'watch {slot} {topic or "rt/plot_multiwatch/t"+str(kind)} {pkg}::msg::dds_::{classes[kind].__name__}_ {reliable} 0')
    def empty(w):
        assert w['numericFields'] == [] and w['receivedMonotonicNs'] is None and w['sourceTimestampNs'] is None, w
        assert w['message'] is None and w['count'] == 0
    def validate(w, kind):
        assert w['fresh'] and w['count'] > 0 and w['readerActive'], w
        received, source = w['receivedMonotonicNs'], w['sourceTimestampNs']
        assert isinstance(received, int) and 0 < received <= time.monotonic_ns()
        assert time.monotonic_ns()-received < 3_000_000_000
        assert isinstance(source, int) and abs(source-time.time_ns()) < 10_000_000_000
        assert source != received and source != -16_876_543_211, 'DDS source is neither steady clock nor ROS header'
        tree = {f['path']: f for f in flatten(w['message']['root']) if f['kind'] == 'number'}
        fields = w['numericFields']
        assert [f['path'] for f in fields] == list(tree) == w['message']['numericPaths']
        for f in fields:
            leaf = tree[f['path']]
            assert f == {'path':leaf['path'], 'unit':None, 'state':leaf['numberState'], 'value':leaf['value']}, f
        values = {f['path']:f for f in fields}
        if kind < 5:
            path = ['x', 'x', 'linear.x', 'pose.pose.position.x', 'position.x'][kind]
            assert values[path]['value'] == 100*(kind+1)+.123456789012345
            assert any(f['state'] == 'nan' and f['value'] is None for f in fields)
            assert any(f['state'] == 'negative_infinity' and f['value'] is None for f in fields)
            if kind in (1,3,4):
                assert any(f['state'] == 'positive_infinity' and f['value'] is None for f in fields)
            if kind == 3:
                assert values['header.stamp.sec']['value'] == -17
                assert values['header.stamp.nanosec']['value'] == 123456789
                for i in range(36):
                    assert values[f'pose.covariance[{i}]']['value'] == 400+i/4
                    assert values[f'twist.covariance[{i}]']['value'] == -400-i/8
        elif kind in (5,6):
            assert fields == [], 'Bool/string must never be echo-parsed into numeric samples'
        elif kind == 7:
            assert values['data']['value'] == -2147483647
        elif kind == 8:
            assert type(values['data']['value']) is int and values['data']['value'] == 9007199254740993
        elif kind == 9:
            assert values['data']['state'] == 'nan' and values['data']['value'] is None
        elif kind == 10:
            assert values['data']['value'] == 42.123456789012345
        tested.add((w['slot'], kind))
    def progress(before, healthy, duration=.4):
        time.sleep(duration)
        s = poll()
        for slot in healthy:
            a, b = before['watches'][slot], s['watches'][slot]
            assert b['count'] > a['count'] and b['receivedMonotonicNs'] > a['receivedMonotonicNs']
            assert b['generation'] == a['generation']
        assert logs(s) > logs(before) and s['rosout']['active'] and not s['rosout']['error']
        return s
    try:
        assert receive()['ok']
        initial = poll()
        for w in initial['watches']:
            empty(w)
        assert initial['numericFields'] == [] and initial['receivedMonotonicNs'] is None and initial['sourceTimestampNs'] is None
        # Four independently receiving structured watches, plus independent logs.
        for slot in range(4):
            assert watch(slot, slot)['ok']
        live = until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']) and logs(s) > 0)
        for slot in range(4):
            validate(live['watches'][slot], slot)
        live = progress(live, range(4))
        print('PLOT_INITIAL '+json.dumps([{'slot':w['slot'], 'count':w['count'],
              'numericFieldCount':len(w['numericFields']), 'receivedMonotonicNs':w['receivedMonotonicNs'],
              'sourceTimestampNs':w['sourceTimestampNs']} for w in live['watches']]), flush=True)
        # Stop each source without removing its reader: polls cannot mint timestamps.
        for frozen_slot in range(4):
            with lock:
                paused.add(frozen_slot)
            stable = poll()
            for _ in range(4):
                time.sleep(.15)
                latest = poll()
                if latest['watches'][frozen_slot]['count'] == stable['watches'][frozen_slot]['count']:
                    break
                stable = latest
            after = progress(latest, tuple(i for i in range(4) if i != frozen_slot))
            for key in ('count','numericFields','receivedMonotonicNs','sourceTimestampNs','message'):
                assert after['watches'][frozen_slot][key] == latest['watches'][frozen_slot][key], 'poll invented '+key
            with lock:
                paused.clear()
            live = until(lambda s: s['watches'][frozen_slot]['count'] > after['watches'][frozen_slot]['count'])
        # Failed QoS and real topic creation failure clear only that watch's plot frame.
        assert watch(1,1,1)['ok']
        bad = poll()
        empty(bad['watches'][1])
        bad = progress(bad, (0,2,3), .6)
        empty(bad['watches'][1])
        assert bad['watches'][1]['matched'] == 0 and not bad['watches'][1]['fresh']
        assert watch(1,1)['ok']
        live = until(lambda s: s['watches'][1]['fresh'])
        assert not watch(2,2,topic='rt/plot_multiwatch/invalid#topic')['ok']
        bad = poll()
        empty(bad['watches'][2])
        bad = progress(bad, (0,1,3))
        empty(bad['watches'][2])
        assert bad['watches'][2]['error']
        assert watch(2,2)['ok']
        live = until(lambda s: s['watches'][2]['fresh'])
        assert cmd('remove 3')['ok']
        removed = poll()
        empty(removed['watches'][3])
        removed = progress(removed, (0,1,2))
        empty(removed['watches'][3])
        # Every supported descriptor in every slot, retaining typed numeric declaration order.
        for round_ in range(len(classes)):
            kinds = [(round_+slot)%len(classes) for slot in range(4)]
            for slot, kind in enumerate(kinds):
                assert watch(slot,kind)['ok']
            before_log = logs(poll())
            s = until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']) and logs(s)>before_log)
            for slot, kind in enumerate(kinds):
                validate(s['watches'][slot], kind)
        assert len(tested) == 44
        stop = cmd('stop')
        for w in stop['watches']:
            empty(w)
        assert stop['numericFields'] == [] and stop['receivedMonotonicNs'] is None and stop['sourceTimestampNs'] is None
        assert not stop['rosout']['active']
        print('PLOT_MULTIWATCH '+json.dumps({'rmw':os.environ.get('RMW_IMPLEMENTATION'), 'slotTypeCases':len(tested),
              'rosoutAdvancing':True, 'typedPaths':True, 'stableIdleTimestamps':True, 'exactInt64':9007199254740993,
              'isolation':['paused source','QoS mismatch/recovery','creation failure/recovery','remove','stop']}), flush=True)
    finally:
        if child.poll() is None:
            child.stdin.write('exit\n')
            child.stdin.flush()
            child.wait(timeout=5)
        rclpy.shutdown()
        thread.join(timeout=5)
        node.destroy_node()


if __name__ == '__main__':
    main()
