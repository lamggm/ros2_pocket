#!/usr/bin/env python3
"""Run the plot phase driver in two unique, bounded network-none containers."""
import argparse
import hashlib
import shlex
import json
import os
from pathlib import Path
import subprocess
import uuid


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--image', required=True, help='existing image ID/digest; never pull')
    p.add_argument('--toolchains', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    args = p.parse_args()
    root = Path(__file__).resolve().parents[1]
    args.toolchains = args.toolchains.resolve()
    args.out.mkdir(parents=True, exist_ok=True)
    inputs = ['tests/device_plot_fixture.py', 'tests/device_plot_writer.cpp', 'tests/plot_phase_host.py',
              'tests/fixture-loopback.xml', 'build/plot-stamped-writer', 'build/native-host/multiwatch_wire']
    hashes = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in inputs}
    (args.out / 'inputs.json').write_text(json.dumps(hashes, indent=2))
    def docker(*cmd, timeout=20, check=True):
        return subprocess.run(['docker', *cmd], text=True, capture_output=True, timeout=timeout, check=check)
    image = json.loads(docker('image', 'inspect', args.image).stdout)[0]['Id']
    for rmw in ('rmw_fastrtps_cpp', 'rmw_cyclonedds_cpp'):
        out = (args.out / rmw).resolve(); out.mkdir(exist_ok=False)
        cid = None
        try:
            command = ['run', '--detach', '--pull', 'never', '--name', 'plot14-' + uuid.uuid4().hex,
                       '--label', 'ros-pocket.front=14', '--network', 'none', '--read-only',
                       '--tmpfs', '/tmp:rw,noexec,nosuid,size=64m', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
                       '--pids-limit', '128', '--memory', '512m', '--cpus', '1', '--user', f'{os.getuid()}:{os.getgid()}',
                       '-v', f'{root / "tests"}:/work/tests:ro', '-v', f'{out}:/results:rw',
                       '-v', f'{root / "build/native-host"}:/work/build/native-host:ro',
                       '-v', f'{root / "build/plot-stamped-writer"}:/work/build/plot-stamped-writer:ro',
                       '-v', f'{args.toolchains / "host"}:{args.toolchains / "host"}:ro',
                       '-e', f'RMW_IMPLEMENTATION={rmw}', '-e', 'ROS_LOCALHOST_ONLY=1',
                       '-e', 'ROS_DOMAIN_ID=71', '-e', 'HOME=/tmp', '-e', 'ROS_LOG_DIR=/tmp/ros/log',
                       '-e', 'CYCLONEDDS_URI=file:///work/tests/fixture-loopback.xml',
                       '--entrypoint', '/bin/bash', image, '-ec',
                       'source /opt/ros/jazzy/setup.bash; export LD_LIBRARY_PATH=' + shlex.quote(str(args.toolchains / 'host/lib')) + ':$LD_LIBRARY_PATH; ldd -r /work/build/native-host/multiwatch_wire; '
                       'exec timeout 170 python3 /work/tests/plot_phase_host.py '
                       '--reader /work/build/native-host/multiwatch_wire --writer /work/build/plot-stamped-writer --out /results']
            cid = docker(*command).stdout.strip()
            state = json.loads(docker('inspect', cid).stdout)[0]
            (out / 'container.json').write_text(json.dumps(state, indent=2))
            assert state['HostConfig']['NetworkMode'] == 'none' and state['Image'] == image
            result = docker('wait', cid, timeout=180)
            logs = docker('logs', cid)
            (out / 'container.log').write_text(logs.stdout + logs.stderr)
            if result.stdout.strip() != '0':
                raise RuntimeError(f'{rmw} failed with exit {result.stdout.strip()}; see {out}')
            report = json.loads((out / 'result.json').read_text())
            assert report['passed'] is True
            print(json.dumps(report), flush=True)
        finally:
            if cid:
                docker('rm', '-f', cid)
                check = docker('inspect', cid, check=False)
                (out / 'cleanup.json').write_text(json.dumps({'id': cid, 'inspectExit': check.returncode,
                                                              'stderr': check.stderr}, indent=2))
                if check.returncode == 0 or ('no such object: ' + cid) not in check.stderr.lower():
                    raise RuntimeError('Owned container cleanup not confirmed')
    after = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in inputs}
    if after != hashes:
        raise RuntimeError('Fixture inputs changed during execution')


if __name__ == '__main__':
    main()
