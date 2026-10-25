#!/usr/bin/env python3
"""Inspect REAL compiled front-13 test APK, not a source manifest assertion."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile


def inspect(apk, build_tools):
    apk=Path(apk).resolve(strict=True);build_tools=Path(build_tools)
    runners=['com.lamggm.rospocket.ReaderFailureInstrumentation','com.lamggm.rospocket.MultiwatchInstrumentation']
    manifest=subprocess.run([str(build_tools/'aapt2'),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],capture_output=True,text=True,check=True).stdout
    records=manifest.split('E: instrumentation ')[1:]
    assert len(records)==2, 'both real runner registrations required'
    for runner in runners:
        matches=[r for r in records if '="'+runner+'"' in r]
        assert len(matches)==1 and ':targetPackage' in matches[0] and '="com.lamggm.rospocket.dev"' in matches[0]
    with zipfile.ZipFile(apk) as z:
        dex=[z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n)]
        assert dex and all(any(('L'+r.replace('.','/')+';').encode() in d for d in dex) for r in runners)
    signer=subprocess.run([str(build_tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],capture_output=True,text=True,check=True).stdout
    assert 'Verified' in signer or 'Verifies' in signer
    return dict(apk=str(apk),sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),size=apk.stat().st_size,
                manifest=manifest,signer=signer,compiledRunnerClasses=runners,
                scope='built-and-packaged-only; Android instrumentation NOT executed')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('build_tools',type=Path)
    args=parser.parse_args()
    print(json.dumps(inspect(args.apk,args.build_tools),indent=2))
