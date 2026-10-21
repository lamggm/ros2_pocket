#!/usr/bin/env python3
"""Separate rclpy/native processes, only in the network-none fixture (domain 71).
Four typed trees plus the dedicated rosout reader; no native sample injection.
Host budgets: <25% one core, <96 MiB RSS, <8 MiB RSS span, 20s at 5/10/20/40Hz.
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
from geometry_msgs.msg import Vector3, Quaternion, Twist, Pose
from nav_msgs.msg import Odometry
from std_msgs.msg import String, Bool, Int32, Int64, Float32, Float64
from rcl_interfaces.msg import Log


def flatten(node):
    yield node
    for child in node.get('children', []):
        yield from flatten(child)


def main():
    os.environ['ROS_DOMAIN_ID'] = '71'
    rclpy.init()
    node = rclpy.create_node('structured_multiwatch_fixture', enable_rosout=False)
    classes = [Vector3, Quaternion, Twist, Odometry, Pose, String, Bool, Int32, Int64, Float32, Float64]
    rates = [5, 10, 20, 40] + [10]*7
    pubs, timers = [], []
    sequences = [0]*len(classes)
    lock = threading.Lock()
    log_paused, log_sequence = [False], [0]
    def sample(i):
        sequences[i] += 1
        m = classes[i]()
        base = 100*(i+1)
        def vector(v):
            v.x, v.y, v.z = base+.25, -base-.5, base+.75
        def quaternion(q):
            vector(q)
            q.w = base+.125
        if i == 0:
            vector(m)
        elif i == 1:
            quaternion(m)
        elif i == 2:
            vector(m.linear)
            vector(m.angular)
        elif i == 3:
            m.header.stamp.sec = -17
            m.header.stamp.nanosec = sequences[i]
            m.header.frame_id, m.child_frame_id = 'map_ação_🤖', 'base_世界_🚀'
            vector(m.pose.pose.position)
            quaternion(m.pose.pose.orientation)
            vector(m.twist.twist.linear)
            vector(m.twist.twist.angular)
            m.pose.covariance = [base+j/4 for j in range(36)]
            m.twist.covariance = [-base-j/8 for j in range(36)]
        elif i == 4:
            vector(m.position)
            quaternion(m.orientation)
        else:
            m.data = ['ação_世界_🤖', True, -2147483647, -9223372036854775808, float('nan'), float('inf')][i-5]
        return m
    for i, cls in enumerate(classes):
        qos = QoSProfile(depth=64, reliability=ReliabilityPolicy.BEST_EFFORT if i == 0 else ReliabilityPolicy.RELIABLE,
                         durability=DurabilityPolicy.VOLATILE)
        pubs.append(node.create_publisher(cls, '/structured_multiwatch/t'+str(i), qos))
        timers.append(node.create_timer(1/rates[i], lambda i=i: pubs[i].publish(sample(i))))
    log_pub = node.create_publisher(Log, '/rosout', QoSProfile(depth=128, reliability=ReliabilityPolicy.RELIABLE))
    def send_log():
        with lock:
            if log_paused[0]:
                return
            log_sequence[0] += 1
            m = Log()
            m.name, m.msg, m.level, m.line = 'structured.multiwatch', 'structured λ '+str(log_sequence[0]), 20, log_sequence[0]
            m.stamp = node.get_clock().now().to_msg()
            log_pub.publish(m)
    timers.append(node.create_timer(.1, send_log))
    thread = threading.Thread(target=lambda: rclpy.spin(node), daemon=True)
    thread.start()
    child = subprocess.Popen([sys.argv[1]], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    observations = []
    def receive():
        with selectors.DefaultSelector() as selector:
            selector.register(child.stdout, selectors.EVENT_READ)
            assert selector.select(8), 'native process timeout'
        return json.loads(child.stdout.readline(), parse_constant=lambda x: (_ for _ in ()).throw(AssertionError(x)))
    def cmd(command):
        child.stdin.write(command+'\n')
        child.stdin.flush()
        return receive()
    def poll():
        s = cmd('poll')
        assert len(s['watches']) == 4
        assert s['message'] == s['watches'][0]['message'], 'root tree must be exactly slot zero'
        for key in ('subscription', 'last', 'count', 'hz', 'matched', 'qos', 'readerActive', 'fresh'):
            assert s[key] == s['watches'][0][key], key
        return s
    def watch(slot, kind, reliable=0, topic=None, wiretype=None):
        cls = classes[kind]
        package = 'nav_msgs' if kind == 3 else 'geometry_msgs' if kind < 5 else 'std_msgs'
        return cmd(f'watch {slot} {topic or "rt/structured_multiwatch/t"+str(kind)} {wiretype or package+"::msg::dds_::"+cls.__name__+"_"} {reliable} 0')
    def logs(s):
        return max((e['line'] for e in s['rosout']['entries'] if e['logger'] == 'structured.multiwatch'), default=0)
    def until(predicate, seconds=7):
        deadline = time.monotonic()+seconds
        while time.monotonic() < deadline:
            s = poll()
            if predicate(s):
                return s
            time.sleep(.05)
        raise AssertionError(json.dumps(s, ensure_ascii=False))
    def validate(w, kind):
        m = w['message']
        assert w['readerActive'] and w['fresh'] and w['count'] > 0 and w['matched'] > 0, w
        assert m['schemaVersion'] == 1 and not m['truncated'], m
        package = 'nav_msgs' if kind == 3 else 'geometry_msgs' if kind < 5 else 'std_msgs'
        assert m['type'] == package+'/msg/'+classes[kind].__name__, m['type']
        fields = {f['path']: f for f in flatten(m['root'])}
        assert m['root']['path'] == '' and m['root']['kind'] == 'object'
        assert m['numericPaths'] == [p for p, f in fields.items() if f['kind'] == 'number']
        assert len(fields) <= 192
        base = 100*(kind+1)
        if kind < 5:
            path = ['x', 'x', 'linear.x', 'pose.pose.position.x', 'position.x'][kind]
            assert fields[path]['value'] == base+.25, (kind, path, fields[path])
            if kind == 3:
                assert fields['header.stamp.sec']['value'] == -17
                assert fields['header.stamp.nanosec']['value'] > 0
                assert fields['header.frame_id']['value'] == 'map_ação_🤖'
                assert fields['child_frame_id']['value'] == 'base_世界_🚀'
                for prefix, expected in [('pose', [base+j/4 for j in range(36)]), ('twist', [-base-j/8 for j in range(36)])]:
                    a = fields[prefix+'.covariance']
                    assert a['kind'] == 'array' and len(a['children']) == 36
                    assert [fields[f'{prefix}.covariance[{j}]']['value'] for j in range(36)] == expected
        else:
            f = fields['data']
            if kind < 9:
                assert f['value'] == ['ação_世界_🤖', True, -2147483647, -9223372036854775808][kind-5]
                if kind == 8:
                    assert f['display'] == '-9223372036854775808'
            else:
                assert f['value'] is None and f['numberState'] == ('nan' if kind == 9 else 'positive_infinity')
    def observe(label, before, healthy, check, kinds, seconds=1.2, advancing_logs=True):
        begin = time.monotonic()
        while True:
            s = poll()
            check(s)
            for i in healthy:
                validate(s['watches'][i], kinds[i])
                assert s['watches'][i]['generation'] == before['watches'][i]['generation'], (label, i)
            if time.monotonic()-begin >= seconds:
                break
            time.sleep(.1)
        deltas = [s['watches'][i]['count']-before['watches'][i]['count'] for i in healthy]
        assert all(d > 0 for d in deltas), (label, deltas)
        assert s['rosout']['active'] and not s['rosout']['error']
        if advancing_logs:
            assert logs(s) > logs(before), label
        observations.append({'case':label, 'healthySlots':list(healthy), 'countDeltas':deltas, 'lastLog':logs(s)})
        return s
    def empty(s, slot, failed=False):
        w = s['watches'][slot]
        assert w['message'] is None and w['last'] == '' and w['subscription'] == '' and w['type'] == '', w
        assert not w['readerActive'] and not w['fresh'] and w['count'] == 0 and w['age'] == -1 and w['matched'] == 0, w
        assert bool(w['error']) == failed, w
    try:
        assert receive()['ok']
        kinds = [0, 1, 2, 3]
        for i in range(4):
            assert watch(i, kinds[i])['ok']
        live = until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']) and logs(s) > 0)
        for i in range(4):
            validate(live['watches'][i], kinds[i])
        observe('four independent structured trees plus dedicated rosout', live, range(4), lambda s: None, kinds)
        # Actual middleware creation failure empties only one tree.
        before = poll()
        assert not watch(1, 1, topic='rt/structured_multiwatch/invalid#topic')['ok']
        observe('creation failure clears only its tree', before, (0,2,3), lambda s: empty(s,1,True), kinds)
        assert watch(1,1)['ok']
        until(lambda s: s['watches'][1]['fresh'])
        before = poll()
        assert cmd('remove 2')['ok']
        observe('removal clears only its tree', before, (0,1,3), lambda s: empty(s,2), kinds)
        # Pose replaces Twist without changing other reader generations.
        kinds[2] = 4
        assert watch(2,4)['ok']
        immediate = poll()
        assert immediate['watches'][2]['message'] is None if immediate['watches'][2]['count'] == 0 else immediate['watches'][2]['message']['type'] == 'geometry_msgs/msg/Pose'
        before = until(lambda s: all(w['fresh'] for w in s['watches']))
        observe('reassignment delivers real Pose while survivors advance', before, range(4), lambda s: None, kinds)
        # Two readers of the same BE Vector3, one incompatible, one receiving.
        before = poll()
        assert watch(1,0,1)['ok']
        def mismatch(s):
            w = s['watches'][1]
            assert w['message'] is None and w['count'] == 0 and w['matched'] == 0 and not w['fresh']
            assert w['qos'] == 'reliable / volatile' and not w['error']
        observe('same structured topic independent QoS mismatch', before, (0,2,3), mismatch, kinds, seconds=1.5)
        kinds[1] = 0
        assert watch(1,0)['ok']
        before = until(lambda s: s['watches'][1]['fresh'])
        observe('same structured topic independent QoS recovery', before, range(4), lambda s: None, kinds)
        # Conservative unsupported rejection keeps reader/tree but cannot be fresh.
        before = poll()
        assert not watch(2,4,wiretype='geometry_msgs::msg::dds_::Point_')['ok']
        def rejected(s):
            w = s['watches'][2]
            assert w['readerActive'] and w['message']['type'] == 'geometry_msgs/msg/Pose'
            assert w['generation'] == before['watches'][2]['generation'] and w['error'] and not w['fresh']
        observe('unsupported type retains old tree but errors not fresh', before, (0,1,3), rejected, kinds)
        assert watch(2,4)['ok']
        until(lambda s: s['watches'][2]['fresh'])
        # Restore four initial types for 20s host resource/intake measurement.
        kinds = [0,1,2,3]
        for i in range(4):
            assert watch(i,kinds[i])['ok']
        before = until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']))
        with lock:
            log_paused[0] = True
        time.sleep(.3)
        before = poll()
        assert logs(before) > 0 and cmd('clear_rosout')['ok']
        def cleared(s):
            assert s['rosout']['count'] == 0 and s['rosout']['entries'] == []
        observe('clear logs leaves four advancing structured trees/generations', before, range(4), cleared, kinds, advancing_logs=False)
        with lock:
            log_paused[0] = False
        until(lambda s: logs(s) > logs(before))
        def proc():
            p = Path(f'/proc/{child.pid}/stat').read_text().split()
            return (int(p[13])+int(p[14]))/os.sysconf('SC_CLK_TCK'), int(p[23])*os.sysconf('SC_PAGE_SIZE')
        start = poll()
        cpu0, rss0 = proc()
        rss = [rss0]
        begin = time.monotonic()
        while time.monotonic()-begin < 20:
            time.sleep(.5)
            end = poll()
            for i in range(4):
                validate(end['watches'][i], i)
            rss.append(proc()[1])
        elapsed = time.monotonic()-begin
        cpu1, _ = proc()
        hz = [(end['watches'][i]['count']-start['watches'][i]['count'])/elapsed for i in range(4)]
        metrics = {'rmw':os.environ.get('RMW_IMPLEMENTATION'), 'seconds':elapsed, 'cpuPercentOneCore':100*(cpu1-cpu0)/elapsed,
                   'rssMaxBytes':max(rss), 'rssSpanBytes':max(rss)-min(rss), 'receivedHz':hz, 'sourceHz':rates[:4]}
        print('STRUCTURED_MEASUREMENT '+json.dumps(metrics), flush=True)
        assert metrics['cpuPercentOneCore'] < 25 and max(rss) < 96*1024**2 and max(rss)-min(rss) < 8*1024**2
        assert all(.8*r <= h <= 1.2*r for r,h in zip(rates,hz)), metrics
        # Every supported descriptor in EVERY slot; each round mixes types.
        tested = set()
        for round_ in range(len(classes)):
            kinds = [(round_+i)%len(classes) for i in range(4)]
            for i in range(4):
                assert watch(i,kinds[i])['ok']
            s = until(lambda s: all(w['fresh'] and w['count'] > 1 for w in s['watches']))
            for i, kind in enumerate(kinds):
                validate(s['watches'][i], kind)
                tested.add((i,kind))
        assert len(tested) == 4*len(classes)
        stopped = cmd('stop')
        for i in range(4):
            empty(stopped,i)
        assert stopped['message'] is None and not stopped['rosout']['active'] and stopped['rosout']['entries'] == []
        print('PASS structured four-watch + dedicated rosout; all 44 slot/type combinations, fixed covariance, UTF-8, exact Int64, nonfinite JSON null', flush=True)
    finally:
        for item in observations:
            print('STRUCTURED_OBSERVATION '+json.dumps(item), flush=True)
        child.stdin.write('exit\n')
        child.stdin.flush()
        child.wait(timeout=5)
        rclpy.shutdown()
        thread.join(timeout=5)
        node.destroy_node()


if __name__ == '__main__':
    main()
