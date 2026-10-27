"""Explicit offline integration, actual ELF/APK, no device. Build fixtures are not candidates."""
import argparse, hashlib, json, os, subprocess, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--evidence',type=Path,required=True);args=parser.parse_args();args.evidence.mkdir(parents=True,exist_ok=True)
    library=ROOT/'app/src/main/jniLibs/arm64-v8a/librospocket.so';original=library.read_bytes()
    def run(label,command,success=True):
        p=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=300)
        (args.evidence/(label+'.log')).write_text(p.stdout+p.stderr)
        assert (p.returncode==0) is success,(label,p.returncode,(p.stdout+p.stderr)[-4000:])
        return p.stdout+p.stderr
    def native(hooks):
        run('configure-'+hooks,['cmake','-S','app/src/main/cpp','-B','build/native-android','-DPOCKET_ENABLE_TEST_HOOKS='+hooks])
        run('native-'+hooks,['cmake','--build','build/native-android','--parallel','4'])
        binary=ROOT/'build/native-android/librospocket.so'
        data=binary.read_bytes()
        tc=Path(os.environ.get('POCKET_TOOLCHAINS',str(Path.home()/'.cache/ros2-pocket/toolchains')))
        nm=tc/'android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
        symbols=run('jni-symbols-'+hooks,[str(nm),'-D','--defined-only',str(binary)])
        for name in ('beginLease','endLease','readerIdentity','armFailure','cancelFailure','failureReceipt'):
            assert ('Java_com_lamggm_rospocket_DeviceTestHooks_'+name in symbols) is (hooks=='ON'),(hooks,name)
        library.write_bytes(data);return data
    def gradle(label,*tasks,success=True):return run(label,['./gradlew','--offline','--no-daemon',*tasks],success)
    try:
        native('OFF');gradle('ordinary-off',':app:packageDebug')
        other=ROOT/'app/src/main/jniLibs/x86_64/libddsc.so';other.parent.mkdir(parents=True,exist_ok=True)
        try:
            other.write_bytes((ROOT/'app/src/main/jniLibs/arm64-v8a/libddsc.so').read_bytes())
            gradle('missing-inspector-abi',':app:packageDebug',success=False)
        finally:
            other.unlink();other.parent.rmdir()
        on=native('ON');gradle('ordinary-on-rejected',':app:packageDebug',success=False)
        gradle('diagnostic-on','-PpocketDiagnostic=true',':app:assembleDebug',':app:assembleDebugAndroidTest')
        log=gradle('diagnostic-cached','-PpocketDiagnostic=true',':app:packageDebug')
        assert ':app:packageDebug UP-TO-DATE' in log and 'Verified native diagnostic policy:' in log
        apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
        with zipfile.ZipFile(apk) as z:
            data=z.read('lib/arm64-v8a/librospocket.so');assert b'POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0' in data
        run('diagnostic-metadata',['python3','tools/artifact_metadata.py','--diagnostic','--output',str(args.evidence/'diagnostic.json')])
        run('ordinary-metadata-reject',['python3','tools/artifact_metadata.py','--output',str(args.evidence/'ordinary.json')],False)
        library.write_bytes(on.replace(b'ros-pocket-dds-fault-v1',b'ros-pocket-dds-fault-v9'))
        gradle('unknown-policy-rejected','-PpocketDiagnostic=true',':app:packageDebug',success=False)
        native('OFF');gradle('ordinary-off-restored',':app:packageDebug')
    finally:
        library.write_bytes(original)
    print('PASS actual debug OFF / ON opt-in / cached guard / unknown-policy rejection / OFF same cache')
if __name__=='__main__':main()
