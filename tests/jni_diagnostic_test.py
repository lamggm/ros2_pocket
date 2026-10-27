"""Explicit Linux JNI/ddsc gate. Invoke inside unshare -Urn with private lo up.
Does not run Android native code, Android instrumentation, Docker, or device commands.
"""
import argparse,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--evidence',type=Path,required=True);args=parser.parse_args()
    out=args.evidence.resolve()
    if not out.is_relative_to((ROOT/'build').resolve()):raise SystemExit('evidence must stay under build/')
    out.mkdir(parents=True,exist_ok=True)
    tc=Path(os.environ.get('POCKET_TOOLCHAINS',str(Path.home()/'.cache/ros2-pocket/toolchains')));jdk=Path(os.environ['JAVA_HOME'])
    def run(label,command,env=None):
        p=subprocess.run(list(map(str,command)),cwd=ROOT,env=env,capture_output=True,text=True,timeout=300)
        (out/(label+'.log')).write_text(p.stdout+p.stderr)
        if p.returncode:raise RuntimeError(f'{label} exit {p.returncode}: '+(p.stdout+p.stderr)[-2000:])
    run('host-config',['cmake','-S','app/src/main/cpp','-B','build/native-host','-DCMAKE_PREFIX_PATH='+str(tc/'host'),'-DPOCKET_ENABLE_TEST_HOOKS=ON'])
    run('host-core',['cmake','--build','build/native-host','--target','pocket_core','--parallel','4'])
    core=ROOT/'build/native-host/libpocket_core.a';link=[core,'-L'+str(tc/'host/lib'),'-Wl,-rpath,'+str(tc/'host/lib'),'-lddsc']
    run('writer',['c++','-std=c++17','tests/android_diagnostic_writer.cpp','-Iapp/src/main/cpp/generated','-I'+str(tc/'host/include'),*link,'-o',out/'writer'])
    run('jni',['c++','-std=c++17','-shared','-fPIC','-DPOCKET_ENABLE_TEST_HOOKS=1','-I'+str(jdk/'include'),'-I'+str(jdk/'include/linux'),'-Iapp/src/main/cpp','app/src/main/cpp/jni.cpp',*link,'-o',out/'librospocket.so'])
    env=dict(os.environ,POCKET_REAL_JNI_LIBDIR=str(out),POCKET_REAL_JNI_WRITER=str(out/'writer'))
    run('real-jni',['./gradlew','--offline','--no-daemon',':app:testDebugUnitTest','--tests','*RealJniSessionTest','--rerun-tasks'],env)
    report=ROOT/'app/build/test-results/testDebugUnitTest/TEST-com.lamggm.rospocket.RealJniSessionTest.xml'
    suite=ET.parse(report).getroot()
    if any(suite.get(k)!='0' for k in ('failures','errors','skipped')) or suite.get('tests')!='1':raise RuntimeError('real JNI test did not actually pass unskipped')
    shutil.copy2(report,out/'real-jni.xml')
    print('PASS 1 real Linux JNI/Session test: four real topic/reader failures, primary+extra, persistent polls, advancing other watches+rosout, recovery, lease/Stop; NOT Android DDS acceptance')
if __name__=='__main__':main()
