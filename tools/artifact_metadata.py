#!/usr/bin/env python3
"""Inspect the built debug APK. No source-default versions or signing secrets."""
import re
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def output(args, cwd=None):
    return subprocess.check_output([str(arg) for arg in args], cwd=cwd, text=True)


def source_provenance(root):
    head = output(['git', 'rev-parse', 'HEAD'], root).strip()
    dirty = bool(output(['git', 'status', '--porcelain', '--untracked-files=all'], root))
    paths = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root).split(b'\0')
    snapshot = hashlib.sha256()
    for name in sorted(set(paths) - {b''}):
        path = root / os.fsdecode(name)
        snapshot.update(name + b'\0')
        if path.is_file():
            snapshot.update(hashlib.sha256(path.read_bytes()).digest())
        else:
            snapshot.update(b'deleted')
        snapshot.update(b'\0')
    return dict(head_sha=head, dirty=dirty, snapshot_sha256=snapshot.hexdigest())


def collect_metadata(apk, root, aapt2, apksigner):
    data = parse_badging(output([aapt2, 'dump', 'badging', apk]))
    verified = output([apksigner, 'verify', '--verbose', '--print-certs', apk])
    certificate = re.search(r'^Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})$', verified, re.M)
    dn = re.search(r'^Signer #1 certificate DN: (.+)$', verified, re.M)
    if not certificate or not dn or 'CN=Android Debug' not in dn[1] or not data['debuggable']:
        raise ValueError('Expected a verified debuggable APK signed by Android Debug')
    with zipfile.ZipFile(apk) as archive:
        abis = sorted({name.split('/')[1] for name in archive.namelist()
                       if re.match(r'^lib/[^/]+/[^/]+\.so$', name)})
    if not abis:
        raise ValueError('APK has no native ABI libraries')
    data.update(schema_version=1, apk=apk.name, size_bytes=apk.stat().st_size,
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), abis=abis,
                signing=dict(class_='debug', certificate_sha256=certificate[1].lower(), certificate_dn=dn[1]),
                source=source_provenance(root))
    data['signing']['class'] = data['signing'].pop('class_')
    return data


def parse_badging(text):
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text, re.M)
    if not match:
        raise ValueError('APK package/version badging is missing')
    return dict(package=match[1], version_code=int(match[2]), version_name=match[3],
                debuggable='application-debuggable' in text.splitlines())


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/debug/app-debug.apk'))
    parser.add_argument('--output', type=Path, default=Path('build/artifacts/app-debug.json'))
    sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Android/Sdk'))))
    parser.add_argument('--aapt2', type=Path, default=sdk / 'build-tools/35.0.0/aapt2')
    parser.add_argument('--apksigner', type=Path, default=sdk / 'build-tools/35.0.0/apksigner')
    parser.add_argument('--source', action='store_true', help='Print current source provenance only')
    parser.add_argument('--expected-source', type=Path, help='Reject a source snapshot changed during build')
    args = parser.parse_args()
    if args.source:
        print(json.dumps(source_provenance(args.repo), sort_keys=True))
        return
    data = collect_metadata(args.apk, args.repo, args.aapt2, args.apksigner)
    if args.expected_source and data['source'] != json.loads(args.expected_source.read_text()):
        raise SystemExit('Sources changed during build; rebuild before identifying the artifact')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix('.json.part')
    temporary.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    temporary.replace(args.output)
    print(json.dumps(data, indent=2))


if __name__ == '__main__':
    main()
