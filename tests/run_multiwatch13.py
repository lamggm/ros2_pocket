#!/usr/bin/env python3
"""Run front-13 real DDS in a bounded, owned network-none Jazzy container.

No build/pull, no devices, no generic publisher. Build the adapter first (docs).
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PREFIX = Path('/home/lamggm/Documents/ros2_pocket-xeon/toolchains/host')


def docker(*args, timeout=20, check=True):
    return subprocess.run(['docker', *map(str,args)], capture_output=True, text=True,
                          timeout=timeout, check=check)


def verify_removed(result, cid):
    if result.returncode != 1 or re.fullmatch(r'error: no such object: '+re.escape(cid)+r'\s*', result.stderr, re.IGNORECASE) is None:
        raise RuntimeError('container removal readback failed: '+result.stderr)


def run(image, binary, output, rmw):
    binary, output = Path(binary).resolve(strict=True), Path(output).resolve(strict=True)
    if not binary.is_file() or not output.is_dir() or not re.fullmatch(r'[a-zA-Z0-9./:_@-]+', image):
        raise ValueError('explicit local image, binary and existing output directory required')
    if rmw not in ('rmw_fastrtps_cpp','rmw_cyclonedds_cpp'):
        raise ValueError('unsupported RMW')
    # Resolve the image before creating anything; never implicitly pull.
    image_id = json.loads(docker('image','inspect',image).stdout)[0]['Id']
    owner = uuid.uuid4().hex
    name = 'pocket-multiwatch13-'+owner
    script = ROOT/'tests/multiwatch13_integration.py'
    args = ['create','--name',name,'--label','io.lamggm.multiwatch13='+owner,
            '--network','none','--read-only','--cap-drop','ALL',
            '--security-opt','no-new-privileges','--pids-limit','128',
            '--memory','512m','--cpus','1',
            '--tmpfs','/tmp:rw,noexec,nosuid,size=64m',
            '--user',f'{os.getuid()}:{os.getgid()}',
            '-e','HOME=/tmp','-e','ROS_LOG_DIR=/tmp/ros/log',
            '-e','ROS_LOCALHOST_ONLY=1','-e','ROS_DOMAIN_ID=67',
            '-e','RMW_IMPLEMENTATION='+rmw,
            '-e','CYCLONEDDS_URI=file:///fixture/loopback.xml',
            '-v',f'{ROOT / "tests"}:{ROOT / "tests"}:ro',
            '-v',f'{binary.parent}:{binary.parent}:ro','-v',f'{PREFIX}:{PREFIX}:ro',
            '--entrypoint','/bin/bash',image_id,'-lc',
            'source /opt/ros/jazzy/setup.bash && exec timeout 170s python3 "$1" "$2"',
            'multiwatch13',str(script),str(binary)]
    cid = docker(*args).stdout.strip()
    record = dict(owner=owner, id=cid, name=name, imageId=image_id, rmw=rmw,
                  classification='host-isolated-real-dds-not-android')
    log = output/(rmw+'-'+owner+'.log')
    metadata = output/(rmw+'-'+owner+'.json')
    def owned():
        item = json.loads(docker('inspect',cid).stdout)[0]
        if (item['Id'] != cid or item['Config']['Labels'].get('io.lamggm.multiwatch13') != owner
                or item['HostConfig']['NetworkMode'] != 'none'):
            raise RuntimeError('owned container identity/isolation changed')
        return item
    try:
        record['created'] = owned()
        docker('start',cid)
        exit_code = int(docker('wait',cid,timeout=180).stdout.strip())
        record['exitCode'] = exit_code
        log.write_text(docker('logs',cid).stdout, encoding='utf-8')
        record['stopped'] = owned()['State']
        record['log'] = log.name
        if exit_code: raise RuntimeError(f'isolated campaign exit {exit_code}; {log}')
        result_lines = [line.split(' ',1)[1] for line in log.read_text().splitlines()
                        if line.startswith('MULTIWATCH13_RESULT ')]
        if len(result_lines) != 1: raise RuntimeError('missing/nonunique campaign result')
        record['result'] = json.loads(result_lines[0])
        return record
    finally:
        # Preserve diagnostics on every failure and cleanup ONLY immutable owned ID.
        try:
            owned()
            combined = docker('logs',cid)
            log.write_text(combined.stdout+'\nSTDERR:\n'+combined.stderr, encoding='utf-8')
            docker('rm','-f',cid)
            absent = docker('inspect',cid,check=False)
            verify_removed(absent, cid)
            record['cleanupVerified'] = True
        finally:
            metadata.write_text(json.dumps(record,indent=2)+'\n', encoding='utf-8')


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image',required=True)
    parser.add_argument('--binary',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--rmw',required=True,choices=['rmw_fastrtps_cpp','rmw_cyclonedds_cpp'])
    args=parser.parse_args(argv)
    result=run(args.image,args.binary,args.output,args.rmw)
    print(json.dumps(dict(rmw=args.rmw,cases=result['result']['cases'],cleanupVerified=result['cleanupVerified'])))


if __name__=='__main__': main()
