#!/usr/bin/env python3
"""Run-owned read-only plot stimuli. ROS imports occur only in publisher mode.

Use only in a private domain/container (or an explicitly authorized physical bench).
The two Jazzy publishers prove RMW interoperability. Optional stamped writer is a
separate real Cyclone process, not evidence of a Jazzy DDS-source-clock override.
"""
import argparse
import json
import math
from pathlib import Path
import subprocess
import time

# Physical UI profiling measured ~489 s before the first chart crop (139 fresh
# XML captures). Reserve the rest of the full flow, including 109 s fixed waits,
# further guarded navigation/QA/crops and scheduling margin. These are wall-clock
# execution caps, NOT draw/worker freshness, rate, phase or soak budgets.
CAMPAIGN_SECONDS = 1800
RUNNER_TIMEOUT_SECONDS = 1980  # campaign + 180 s independent cleanup allowance
FIXTURE_MAX_SECONDS = 2040  # runner + 60 s startup/headroom, then forced expiry
MIN_PHASE_SECONDS = 6


def bounded_duration(value):
    try:
        duration = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError(f'duration must be 30..{FIXTURE_MAX_SECONDS} seconds') from None
    if not 30 <= duration <= FIXTURE_MAX_SECONDS:
        raise argparse.ArgumentTypeError(f'duration must be 30..{FIXTURE_MAX_SECONDS} seconds')
    return duration
PHASES = ('finite', 'nan', 'positive_infinity', 'negative_infinity', 'gap',
          'return', 'source_reset', 'source_jump', 'window')


def values(phase, sequence):
    if phase not in PHASES:
        raise ValueError('Unknown plot phase')
    value = 40.0 + sequence % 11
    if phase == 'nan':
        value = math.nan
    elif phase == 'positive_infinity':
        value = math.inf
    elif phase == 'negative_infinity':
        value = -math.inf
    elif phase == 'gap':
        value = None
    source = (1_000_000_000 if phase == 'source_reset' else
              4_000_000_000_000 if phase == 'source_jump' else 1_000_000_000_000) + sequence * 500_000_000
    return value, -70.0 - sequence % 13, source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--domain', type=int, required=True)
    parser.add_argument('--namespace', required=True, help='run-owned /plot_<uuid>')
    parser.add_argument('--control', type=Path, required=True)
    parser.add_argument('--duration', type=bounded_duration, default=420)
    parser.add_argument('--stamped-writer', type=Path)
    args = parser.parse_args()
    import re
    if not 0 <= args.domain <= 101 or not re.fullmatch(r'/plot_[a-f0-9]{32}', args.namespace) :
        parser.error('bounded domain, run-owned namespace and duration required')
    import os
    os.environ['ROS_DOMAIN_ID'] = str(args.domain)
    import rclpy
    from std_msgs.msg import Float64
    from rclpy.qos import QoSProfile, ReliabilityPolicy
    rclpy.init()
    node = rclpy.create_node(args.namespace[1:], enable_rosout=False)
    qos = QoSProfile(depth=64, reliability=ReliabilityPolicy.BEST_EFFORT)
    a = node.create_publisher(Float64, args.namespace + '/a', qos)
    b = node.create_publisher(Float64, args.namespace + '/b', qos)
    stamped = None
    deadline, sequence = time.monotonic() + args.duration, 0
    phase, phase_since = 'finite', time.monotonic()
    try:
        if args.stamped_writer:
            stamped = subprocess.Popen([str(args.stamped_writer), str(args.domain), 'rt' + args.namespace + '/stamped'],
                                       stdin=subprocess.PIPE, text=True)
        while time.monotonic() < deadline:
            requested = args.control.read_text().strip() if args.control.exists() else 'finite'
            if requested == 'stop':
                break
            if requested not in PHASES:
                raise ValueError('Invalid control file')
            if requested != phase:
                if time.monotonic() - phase_since < MIN_PHASE_SECONDS:
                    raise ValueError('Phase shorter than six seconds; polling opportunity not established')
                phase, phase_since = requested, time.monotonic()
            sequence += 1
            av, bv, source = values(phase, sequence)
            if av is not None:
                a.publish(Float64(data=av))
                if stamped:
                    stamped.stdin.write(f'{av} {source}\n')
                    stamped.stdin.flush()
            b.publish(Float64(data=bv))
            print(json.dumps({'event': 'publication', 'namespace': args.namespace, 'phase': phase,
                              'sequence': sequence, 'monotonicNs': time.monotonic_ns(),
                              'aPublished': av is not None, 'bPublished': True,
                              'sourceTimestampNs': source if stamped else None,
                              'stampedKind': 'separate_cyclone_dds' if stamped else None}), flush=True)
            rclpy.spin_once(node, timeout_sec=0)
            time.sleep(.5)  # Source rate 2 Hz; never increase Android's polling.
    finally:
        if stamped:
            stamped.stdin.close()
            try:
                stamped.wait(timeout=4)
            except subprocess.TimeoutExpired:
                stamped.kill()
                stamped.wait(timeout=4)
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
