"""Real physical-tablet acceptance. All publishers are isolated synthetic ROS fixtures."""
import json,re,subprocess,time,os
ROS_IMAGE=os.environ["POCKET_ROS_IMAGE"]
from pathlib import Path
from tablet_ui import adb,dump,node_by,tap,field,scroll,OUT,ROOT,PKG
results=[]
def check(name,fn,timeout=25,view=None):
 end=time.monotonic()+timeout;last=None
 while time.monotonic()<end:
  try:
   ns=dump(name.replace(" ","-"))
   if view and not any(n.get('resource-id')==PKG+':id/'+view or n.get('content-desc')==view for n in ns):
    scroll('up' if view.startswith('Tópico ') else 'down');continue
   value=fn(ns)
   if value:
    results.append({'test':name,'passed':True,'observed':value});print('PASS',name,value,flush=True);return value
  except (RuntimeError,StopIteration) as e:last=str(e)
  time.sleep(.7)
 raise AssertionError((name,last))
def content(ns,ident):
 try:return node_by(ns,ident).get('text','')
 except RuntimeError:return ''
def receiving(ns):
 c=content(ns,'counts');m=content(ns,'message')
 return {'counts':c,'message':m} if 'RECEBENDO' in c and m.startswith('pocket-real-jazzy seq=') else False
def launch():adb('shell','am','start','-W','-n',PKG+'/com.lamggm.rospocket.MainActivity')
def owned_fixture_stop():
 p=subprocess.run(['docker','inspect','ros2-pocket-fixture'],capture_output=True,text=True)
 if p.returncode:return
 d=json.loads(p.stdout)[0]
 assert any(m.get('Source')==str(ROOT/'tests/fixture.py') for m in d['Mounts'])
 assert 'ROS_DOMAIN_ID=67' in d['Config']['Env']
 subprocess.run(['docker','stop','-t','5','ros2-pocket-fixture'],check=True,capture_output=True)
 for _ in range(30):
  inspection=subprocess.run(['docker','inspect','ros2-pocket-fixture'],capture_output=True,text=True)
  if inspection.returncode:return
  assert not json.loads(inspection.stdout)[0]['State']['Running']
  time.sleep(.2)
 raise AssertionError('Fixture stopped but auto-removal did not complete')

def fixture_start(rmw='rmw_fastrtps_cpp',best_effort=False):
 cmd=['docker','run','-d','--rm','--name','ros2-pocket-fixture','--label','io.lamggm.task=ros2-pocket-test','--network','host','--cpus','1','-e','ROS_DOMAIN_ID=67','-e','RMW_IMPLEMENTATION='+rmw,'-e','ROS_AUTOMATIC_DISCOVERY_RANGE=SUBNET','-e','ROS_LOCALHOST_ONLY=0','-e','POCKET_BEST_EFFORT='+('1' if best_effort else '0'),'-v',str(ROOT/'tests/fixture.py')+':/pocket-fixture.py:ro','--entrypoint','bash',ROS_IMAGE,'-lc','source /opt/ros/jazzy/setup.bash; timeout 1800 python3 /pocket-fixture.py']
 subprocess.run(cmd,check=True,capture_output=True)

try:
 a=check('FastDDS String receive',receiving,view='message')
 first=int(a['message'].split('=')[-1])
 check('message sequence advances',lambda ns: content(ns,'message') if content(ns,'message').startswith('pocket-real-jazzy seq=') and int(content(ns,'message').split('=')[-1])>first else False)
 check('ROS node graph',lambda ns:content(ns,'nodes') if '/pocket_fixture' in content(ns,'nodes') else False,view='nodes')
 # Lifecycle: do not inspect the launcher or any unrelated foreground application.
 before_home=int(content(dump('before-home'),'message').split('=')[-1])
 adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
 time.sleep(1);launch()
 check('Home return resumes selected reader',receiving,view='message')
 check('Home return receives new sample',lambda ns:content(ns,'message') if content(ns,'message').startswith('pocket-real-jazzy seq=') and int(content(ns,'message').split('=')[-1])>before_home else False,view='message')
 # Peer removal must age the visible data; no fabricated offline samples.
 owned_fixture_stop()
 check('publisher loss marks data stale',lambda ns:content(ns,'counts') if 'SEM DADOS RECENTES' in content(ns,'counts') else False)
 check('departed node removed',lambda ns:'node absent' if '/pocket_fixture' not in content(ns,'nodes') and content(ns,'nodes') else False,timeout=35,view='nodes')
 fixture_start('rmw_cyclonedds_cpp',True)
 check('CycloneDDS replacement automatically received',receiving,timeout=35,view='message')
 tap('qos_reliable')
 check('Reliable rejects best effort publisher',lambda ns:content(ns,'counts') if 'AGUARDANDO' in content(ns,'counts') and '0 publicador' in content(ns,'counts') else False)
 tap('qos_reliable');check('best effort restores live data',receiving,view='message')
 # Change domain only after explicit disconnect; peer remains empty.
 tap('connect');field('domain','68');tap('connect')
 check('wrong domain isolates fixture',lambda ns:content(ns,'status') if 'domínio 68' in content(ns,'status') and 'Descoberta em andamento' in content(ns,'status') else False)
 time.sleep(3)
 ns=dump('domain-isolation-final');assert not any('/pocket_test/' in n.get('text','') for n in ns)
 tap('connect');field('domain','67');tap('connect')
 check('correct domain rediscovers',lambda ns:'fixture discovered' if any(n.get('content-desc')=='Tópico /pocket_test/chatter' for n in ns) else False)
 tap('Tópico /pocket_test/value');scroll('down')
 check('Float64 decoder receives numeric sample',lambda ns:{'counts':content(ns,'counts'),'value':content(ns,'message')} if 'RECEBENDO' in content(ns,'counts') and re.fullmatch(r'-?[0-9]+([.][0-9]+)?',content(ns,'message')) else False)
 # Fresh process retains domain/empty-peer selection but deliberately requires a new topic selection.
 adb('shell','am','force-stop',PKG);launch()
 check('process restart persists domain',lambda ns:content(ns,'domain') if content(ns,'domain')=='67' and 'domínio 67' in content(ns,'status') else False)
 check('process restart rediscovers',lambda ns:'fixture discovered' if any(n.get('content-desc')=='Tópico /pocket_test/chatter' for n in ns) else False)
 tap('Tópico /pocket_test/chatter');scroll('down');check('reader after process restart',receiving,view='message')
finally:
 (OUT/'regression-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
print('COMPLETE',len(results),'real tablet checks')
