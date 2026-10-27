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


DIAGNOSTIC_CONTRACT = 'ros-pocket-dds-fault-v1'


def exact_records(binary, prefix):
    """Count every marker prefix, including empty, unknown and unterminated records."""
    records = []
    at = 0
    while (at := binary.find(prefix, at)) >= 0:
        start = at + len(prefix)
        end = binary.find(b'\0', start)
        if end < 0:
            raise ValueError('unterminated native capability marker')
        records.append(binary[start:end])
        at = start
    return records


def validate_hook_capability(binary, diagnostic=False):
    hooks = exact_records(binary, b'POCKET_TEST_HOOKS=')
    off = hooks.count(b'OFF') if len(hooks) == 1 else -1
    on = hooks.count(b'ON') if len(hooks) == 1 else -1
    policies = exact_records(binary, b'POCKET_DIAGNOSTIC_POLICY=')
    if diagnostic:
        if (not binary.startswith(b'\x7fELF') or off != 0 or on != 1
                or policies != [DIAGNOSTIC_CONTRACT.encode()]):
            raise ValueError('unknown or contradictory diagnostic ELF capability')
        return True
    if off != 1 or on != 0 or policies:
        raise ValueError("APK native test-hook capability is enabled or unknown")
    return False


def collect_metadata(apk, root, aapt2, apksigner, diagnostic=False):
    data = parse_badging(output([aapt2, 'dump', 'badging', apk]))
    verified = output([apksigner, 'verify', '--verbose', '--print-certs', apk])
    certificate = re.search(r'^Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})$', verified, re.M)
    dn = re.search(r'^Signer #1 certificate DN: (.+)$', verified, re.M)
    if not certificate or not dn or 'CN=Android Debug' not in dn[1] or not data['debuggable']:
        raise ValueError('Expected a verified debuggable APK signed by Android Debug')
    if diagnostic and data['package'] != 'com.lamggm.rospocket.dev':
        raise ValueError('diagnostic policy requires exact DEV package')
    libraries = []
    with zipfile.ZipFile(apk) as archive:
        abis = sorted({name.split('/')[1] for name in archive.namelist()
                       if re.match(r'^lib/[^/]+/[^/]+\.so$', name)})
        native_candidates = [name for name in archive.namelist() if re.match(r'^lib/[^/]+/librospocket\.so$', name)]
        if not native_candidates:
            raise ValueError('APK is missing the ROS Pocket native library')
        if len(set(archive.namelist())) != len(archive.namelist()):
            raise ValueError('duplicate APK entries')
        if sorted(name.split('/')[1] for name in native_candidates) != abis:
            raise ValueError('every packaged ABI requires its own inspector library')
        for name in sorted(native_candidates):
            binary = archive.read(name)
            enabled = validate_hook_capability(binary, diagnostic=diagnostic)
            libraries.append(dict(abi=name.split('/')[1], path=name,
                                  sha256=hashlib.sha256(binary).hexdigest(),
                                  test_hooks=enabled, policy=DIAGNOSTIC_CONTRACT if enabled else None))
    if not abis:
        raise ValueError('APK has no native ABI libraries')
    data.update(schema_version=1, apk=apk.name, size_bytes=apk.stat().st_size,
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), abis=abis,
                signing=dict(class_='debug', certificate_sha256=certificate[1].lower(), certificate_dn=dn[1]),
                source=source_provenance(root), test_hooks=False)
    if diagnostic:
        data.update(schema_version=2, test_hooks=True, diagnostic_policy=DIAGNOSTIC_CONTRACT, native_libraries=libraries)
    data['signing']['class'] = data['signing'].pop('class_')
    return data


def collect_test_metadata(apk, candidate, aapt2, apksigner):
    tree=output([aapt2,'dump','xmltree','--file','AndroidManifest.xml',apk])
    package=re.findall(r'^\s*A: package="([^"]+)"',tree,re.M)
    elements=list(re.finditer(r'^( *)E: ([^\s]+)',tree,re.M))
    instrumentation=[(i,e) for i,e in enumerate(elements) if e[2]=='instrumentation']
    if candidate.get('schema_version')!=2 or package!=[candidate['package']+'.test'] or not 1<=len(instrumentation)<=2:
        raise ValueError('distinct diagnostic test APK manifest required')
    primary='com.lamggm.rospocket.ReaderFailureInstrumentation'
    allowed={primary,'com.lamggm.rospocket.MultiwatchInstrumentation'}
    registrations={}
    for i,element in instrumentation:
        end=len(tree)
        for following in elements[i+1:]:
            if len(following[1])<=len(element[1]):end=following.start();break
        block=tree[element.end():end]
        targets=re.findall(r'android:targetPackage\(0x[0-9a-f]+\)="([^"]+)"',block)
        runners=re.findall(r'android:name\(0x[0-9a-f]+\)="([^"]+)"',block)
        if (targets!=[candidate['package']] or len(runners)!=1
                or runners[0] not in allowed or runners[0] in registrations):
            raise ValueError('test instrumentation runner/target mismatch')
        registrations[runners[0]]=targets[0]
    if primary not in registrations:
        raise ValueError('required diagnostic runner missing')
    verified=output([apksigner,'verify','--verbose','--print-certs',apk])
    certs=re.findall(r'^Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})$',verified,re.M)
    if len(certs)!=1 or certs[0].lower()!=candidate['signing']['certificate_sha256'] or 'CN=Android Debug' not in verified:
        raise ValueError('test and candidate debug signer mismatch')
    data=apk.read_bytes();digest=hashlib.sha256(data).hexdigest()
    with zipfile.ZipFile(apk) as z:
        if any(re.match(r'^lib/[^/]+/[^/]+\.so$',name) for name in z.namelist()):
            raise ValueError('test APK must not replace candidate native libraries')
    if digest==candidate['sha256']:raise ValueError('test APK must be separate bytes')
    return dict(schema_version=1,apk=apk.name,package=package[0],runner=primary,target_package=registrations[primary],
                sha256=digest,size_bytes=len(data),certificate_sha256=certs[0].lower(),
                target_sha256=candidate['sha256'],target_source_snapshot=candidate['source']['snapshot_sha256'])


def parse_badging(text):
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text, re.M)
    if not match:
        raise ValueError('APK package/version badging is missing')
    return dict(package=match[1], version_code=int(match[2]), version_name=match[3],
                debuggable='application-debuggable' in text.splitlines())


def validate_metadata_pair(candidate, instrumentation):
    """A shared source snapshot alone cannot bind different APK bytes."""
    try:
        hashes = (candidate['sha256'], candidate['source']['snapshot_sha256'])
        valid = (all(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value) for value in hashes)
                 and instrumentation['target_sha256'] == hashes[0]
                 and instrumentation['target_source_snapshot'] == hashes[1])
    except (KeyError, TypeError):
        valid = False
    if not valid:
        raise ValueError('candidate/instrumentation hash/source binding mismatch')


def read_metadata_pair(destination):
    """Named-sidecar compatibility adapter; admit one coherent pair or fail closed."""
    import fcntl
    try:
        from qualification import load_json
    except ModuleNotFoundError:
        from tools.qualification import load_json
    destination = Path(destination)
    directory = os.open(destination.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        fcntl.flock(directory, fcntl.LOCK_SH)
        sidecar = destination.with_name(destination.stem+'-androidTest.json')
        if destination.is_symlink() or sidecar.is_symlink():
            raise ValueError('metadata pair must use regular files, not symlinks')
        candidate, instrumentation = load_json(destination), load_json(sidecar)
        validate_metadata_pair(candidate, instrumentation)
        return candidate, instrumentation
    finally:
        os.close(directory)


def recover_metadata_pair(destination, recovery_directory):
    """Explicitly restore a retained precommit backup, only if it binds the candidate.

    Never guess among leftovers or roll a committed candidate back to older bytes.
    """
    import fcntl
    try:
        from qualification import load_json
    except ModuleNotFoundError:
        from tools.qualification import load_json
    destination, recovery = Path(destination), Path(recovery_directory)
    if (recovery.is_symlink() or recovery.parent.resolve() != destination.parent.resolve()
            or not recovery.name.startswith('.'+destination.name+'.recovery-')):
        raise ValueError('invalid metadata recovery directory')
    directory = os.open(destination.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        fcntl.flock(directory, fcntl.LOCK_EX)
        backup = recovery/'sidecar'
        if destination.is_symlink() or backup.is_symlink():
            raise ValueError('metadata recovery requires regular files')
        validate_metadata_pair(load_json(destination), load_json(backup))
        backup.replace(destination.with_name(destination.stem+'-androidTest.json'))
        recovery.rmdir()
    finally:
        os.close(directory)


def publish_metadata(destination, data, test_data=None):
    """Serialize cooperating publishers and recover catchable publication failures.

    The candidate replacement is last; actual staged/destination inode identity
    distinguishes a committed rename even when its call raises. This is exception
    recovery, NOT a crash-atomic pair: uncoordinated readers must validate bindings.
    """
    import fcntl
    import shutil
    import tempfile
    try:
        from qualification import validate_json_depth, JSON_MAX_BYTES
    except ModuleNotFoundError:
        from tools.qualification import validate_json_depth, JSON_MAX_BYTES
    if test_data is not None:
        validate_metadata_pair(data, test_data)
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    directory = os.open(destination.parent, os.O_RDONLY | os.O_DIRECTORY)
    staged = []
    backup_directory = None
    preserve_backup = False
    try:
        fcntl.flock(directory, fcntl.LOCK_EX)
        sidecar = destination.with_name(destination.stem+'-androidTest.json')
        documents = [(destination, data)]
        if test_data is not None:
            documents.insert(0, (sidecar, test_data))
        for target, document in documents:
            validate_json_depth(document)
            serialized = (json.dumps(document, indent=2, allow_nan=False)+'\n').encode('utf-8')
            if len(serialized) > JSON_MAX_BYTES:
                raise ValueError('JSON exceeds bounded 4 MiB metadata output limit')
            with tempfile.NamedTemporaryFile(mode='wb', dir=target.parent,
                    prefix='.'+target.name+'.', suffix='.part', delete=False) as stream:
                temporary = Path(stream.name)
                staged.append((temporary, target))
                stream.write(serialized)
                stream.flush()
                os.fsync(stream.fileno())
        if test_data is not None:
            sidecar_inode = staged[0][0].stat()
            candidate_inode = staged[1][0].stat()
            # Link the inode itself, never follow an external destination symlink.
            # The absent backup name is inside an exclusive mode-0700 directory.
            backup_directory = Path(tempfile.mkdtemp(dir=destination.parent,
                                                     prefix='.'+destination.name+'.recovery-'))
            backup = backup_directory / 'sidecar'
            existed = sidecar.exists() or sidecar.is_symlink()
            if existed:
                os.link(sidecar, backup, follow_symlinks=False)
            # Retain by default throughout mutation and rollback. An interrupted
            # restoration must not delete the only sidecar binding the survivor.
            preserve_backup = True
            try:
                staged[0][0].replace(sidecar)
                staged[1][0].replace(destination)
            except BaseException:
                try:
                    # A rename may commit before its Python call raises SIGINT.
                    # Inspect the actual destination inode, never a post-return flag
                    # or matching JSON (a previous candidate may have identical data).
                    try:
                        committed = os.path.samestat(candidate_inode, destination.lstat())
                    except FileNotFoundError:
                        committed = False
                    if not committed:
                        try:
                            sidecar_changed = os.path.samestat(sidecar_inode, sidecar.lstat())
                        except FileNotFoundError:
                            sidecar_changed = False
                        if sidecar_changed:
                            if existed:
                                backup.replace(sidecar)
                            else:
                                sidecar.unlink(missing_ok=True)
                except OSError as error:
                    raise OSError(f'metadata recovery required; retained {backup_directory}') from error
                preserve_backup = False
                raise
            else:
                preserve_backup = False
        else:
            staged[0][0].replace(destination)
    finally:
        try:
            for temporary, _ in staged:
                temporary.unlink(missing_ok=True)
            if backup_directory is not None and not preserve_backup:
                shutil.rmtree(backup_directory)
        finally:
            os.close(directory)


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/debug/app-debug.apk'))
    parser.add_argument('--output', type=Path, default=Path('build/artifacts/app-debug.json'))
    sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Android/Sdk'))))
    parser.add_argument('--aapt2', type=Path, default=sdk / 'build-tools/35.0.0/aapt2')
    parser.add_argument('--apksigner', type=Path, default=sdk / 'build-tools/35.0.0/apksigner')
    parser.add_argument('--test-apk', type=Path, help='Verify separate instrumentation test APK and write a bound sidecar')
    parser.add_argument('--diagnostic', action='store_true', help='Explicit DEV DEBUG versioned diagnostic artifact policy')
    parser.add_argument('--source', action='store_true', help='Print current source provenance only')
    parser.add_argument('--expected-source', type=Path, help='Reject a source snapshot changed during build')
    args = parser.parse_args()
    if args.source:
        print(json.dumps(source_provenance(args.repo), sort_keys=True))
        return
    data = collect_metadata(args.apk, args.repo, args.aapt2, args.apksigner, diagnostic=args.diagnostic)
    if args.expected_source and data['source'] != json.loads(args.expected_source.read_text()):
        raise SystemExit('Sources changed during build; rebuild before identifying the artifact')
    test_data = collect_test_metadata(args.test_apk, data, args.aapt2, args.apksigner) if args.test_apk else None
    publish_metadata(args.output, data, test_data)
    print(json.dumps(data, indent=2))


if __name__ == '__main__':
    main()
