#!/usr/bin/env python3
"""Required isolated host campaigns for integrated fronts 13/14.

Called by tools/test.sh --fixture, including the unchanged public CI workflow.
Never uses a device or changes the ordinary Android native capability policy.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def commands(root, toolchains, image, out):
    build = out / 'native-hooks'
    library = build / 'libpocket_core.a'
    prefix = toolchains / 'host'
    common = ['-Iapp/src/main/cpp', '-Iapp/src/main/cpp/generated', '-I'+str(prefix/'include')]
    link = ['-L'+str(prefix/'lib'), '-Wl,-rpath,'+str(prefix/'lib'), '-lddsc', '-lpthread']
    return [
        ['cmake', '-S', 'app/src/main/cpp', '-B', str(build), '-G', 'Ninja',
         '-DCMAKE_PREFIX_PATH='+str(prefix), '-DPOCKET_ENABLE_TEST_HOOKS=ON', '-DCMAKE_BUILD_TYPE=Debug'],
        ['cmake', '--build', str(build), '--target', 'pocket_core', '--parallel', '2'],
        ['c++', '-std=c++17', '-DPOCKET_ENABLE_TEST_HOOKS=1', *common,
         'tests/multiwatch13_native.cpp', str(library), *link, '-o', str(build/'multiwatch13_native')],
        ['cc', '-c', 'app/src/main/cpp/generated/PocketTypes.c', '-I'+str(prefix/'include'),
         '-o', str(out/'plot-types.o')],
        ['c++', '-std=c++17', *common, 'tests/device_plot_writer.cpp', str(out/'plot-types.o'),
         *link, '-o', str(root/'build/plot-stamped-writer')],
        *[[sys.executable, 'tests/run_multiwatch13.py', '--image', image,
           '--binary', str(build/'multiwatch13_native'), '--output', str(out/'multiwatch'), '--rmw', rmw]
          for rmw in ('rmw_fastrtps_cpp', 'rmw_cyclonedds_cpp')],
        [sys.executable, 'tests/multiwatch13_mutation_controls.py'],
        [sys.executable, 'tests/run_plot_host.py', '--image', image,
         '--toolchains', str(toolchains), '--out', str(out/'plots')],
        ['./gradlew', '--offline', '--no-daemon', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx1g',
         ':app:testDebugUnitTest', '--rerun-tasks'],
    ]


def verify_replay(path):
    suite = ET.parse(path).getroot()
    cases = suite.findall('testcase')
    expected = 'bothRealRmwStreamsTraverseModelWindowAndCompletedCanvas'
    if (suite.tag != 'testsuite' or int(suite.get('tests', '-1')) != len(cases) or not cases
            or any(suite.get(k) != '0' for k in ('failures', 'errors', 'skipped'))
            or sum(case.get('name') == expected for case in cases) != 1
            or any(case.find(k) is not None for case in cases for k in ('failure','error','skipped'))):
        raise ValueError('Actual two-RMW DDS/native Canvas replay did not pass unskipped')


def verify_multiwatch(record):
    required = {'mixed_rates', 'fifth_rejected', 'negative_qos', 'qos_recovery',
                'topic_failure_0', 'reader_failure_0', 'topic_failure_2', 'reader_failure_2',
                'same_topic', 'remove_same_topic', 'recreate', 'rosout_clear', 'stop'}
    result = record.get('result', {})
    cases = result.get('cases')
    if (record.get('cleanupVerified') is not True or type(record.get('exitCode')) is not int
            or record['exitCode'] != 0 or not isinstance(cases, list)
            or not all(isinstance(case, str) for case in cases)
            or len(cases) != len(required) or set(cases) != required
            or result.get('classification') != 'host-isolated-real-dds-not-android'):
        raise ValueError('Incomplete real multiwatch DDS result')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True, help='Existing immutable/local image; never pull')
    args = parser.parse_args()
    toolchains = Path(os.environ.get('POCKET_TOOLCHAINS', str(Path.home()/'.cache/ros2-pocket/toolchains'))).resolve(strict=True)
    out = ROOT / 'build/host-campaigns' / ('run-'+uuid.uuid4().hex)
    out.mkdir(parents=True)
    (out/'multiwatch').mkdir()
    env = dict(os.environ, POCKET_TOOLCHAINS=str(toolchains), POCKET_PLOT_DDS_RECORDS=str(out/'plots'))
    # Snapshot the actual working source, not only its branch label.
    def source():
        return subprocess.run([sys.executable, 'tools/artifact_metadata.py', '--source'], cwd=ROOT,
                              env=env, check=True, capture_output=True, text=True, timeout=20).stdout
    before = source()
    (out/'source-before.json').write_text(before)
    for index, command in enumerate(commands(ROOT, toolchains, args.image, out)):
        print('INTEGRATED_HOST_GATE', index, json.dumps(command), flush=True)
        subprocess.run(command, cwd=ROOT, env=env, check=True, timeout=600)
    records = list((out/'multiwatch').glob('*.json'))
    rmws = set()
    for path in records:
        record = json.loads(path.read_text())
        verify_multiwatch(record)
        rmws.add(record['rmw'])
    expected = {'rmw_fastrtps_cpp', 'rmw_cyclonedds_cpp'}
    if len(records) != 2 or rmws != expected:
        raise ValueError('Missing two-RMW multiwatch campaign')
    for rmw in expected:
        record = json.loads((out/'plots'/rmw/'result.json').read_text())
        if record.get('passed') is not True or record.get('rmw') != rmw:
            raise ValueError('Incomplete real plot DDS result')
    xml = ROOT/'app/build/test-results/testDebugUnitTest'
    verify_replay(xml/'TEST-com.lamggm.rospocket.PlotDdsReplayTest.xml')
    reports = list(xml.glob('TEST-*.xml'))
    if not reports or any(int(ET.parse(p).getroot().get(k, '-1')) != 0
                          for p in reports for k in ('failures','errors')):
        raise ValueError('Missing/failing integrated Java suite')
    shutil.copytree(xml, out/'java-xml')
    after = source()
    (out/'source-after.json').write_text(after)
    if before != after:
        raise ValueError('Source changed during integrated host campaigns')
    report = dict(status='passed', classification='host-dds-native-canvas-NOT-Android', rmws=sorted(rmws),
                  output=str(out), replay='unskipped', physicalAccepted=False)
    (out/'result.json').write_text(json.dumps(report, indent=2)+'\n')
    print('INTEGRATED_HOST_RESULT', json.dumps(report), flush=True)


if __name__ == '__main__':
    main()
