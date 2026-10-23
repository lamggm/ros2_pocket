"""Real physical-tablet acceptance. All publishers are isolated synthetic ROS fixtures."""
import json,re,subprocess,time,os,uuid,shlex,ipaddress
ROS_IMAGE=os.environ.get("POCKET_ROS_IMAGE", "")
FIXTURE_NAME=""
FIXTURE_OWNER=""
EXPLORER_EMPTY_TEXT="Nenhum tópico corresponde aos filtros (ou descoberta vazia)."
from pathlib import Path
from tablet_ui import adb,dump,node_by,field,scroll,OUT,ROOT,PKG
import tablet_ui as ui
results=[]
def tap(ident):
 direction='up' if ident in ('connect','domain','peer','explorer_search','qos_reliable') or ident.startswith('Tópico ') else 'down'
 ui.client().find(ident,direction=direction)
 ui.tap(ident)

def select_topic(name):
 ui.client().find('explorer_search',direction='up')
 field('explorer_search',name)
 ui.client().hide_ime()
 ui.client().find('Tópico '+name,direction='down')
 ui.tap('Tópico '+name)

def check(name,fn,timeout=25,view=None,direction=None):
 if view:
  try:
   direction=direction or ('up' if view in ('diagnostics','status','domain','peer') else 'down')
   ui.client().find(view,direction=direction)
  except RuntimeError as e:
   results.append({'test':name,'passed':False,'classification':'harness_blocked','error':str(e)})
   raise
 end=time.monotonic()+timeout;last=None
 while time.monotonic()<end:
  try:
   ns=dump(name.replace(" ","-"))
   if view:
    try:node_by(ns,view)
    except ui.MissingTarget:
     scroll(direction);continue
   value=fn(ns)
   if value:
    results.append({'test':name,'passed':True,'observed':value});print('PASS',name,value,flush=True);return value
  except (ui.MissingTarget,StopIteration) as e:last=str(e)
  except RuntimeError as e:
   results.append({'test':name,'passed':False,'classification':'harness_blocked','error':str(e)})
   raise
  time.sleep(.7)
 results.append({'test':name,'passed':False,'classification':'observation_timeout','error':last})
 raise AssertionError((name,last))
def content(ns,ident):
 try:return node_by(ns,ident).get('text','')
 except ui.MissingTarget:return ''
def state_is(ns,ident,state):
 return content(ns,ident).split('·',1)[0].strip()==state

def waiting_for_match(ns):
 text=content(ns,'counts');match=re.search(r'(?m)^([0-9]{1,10}) publicador\(es\) compatível\(is\)(?: ·|$)',text)
 return text if state_is(ns,'counts','AGUARDANDO') and match and int(match[1])==0 else False

def reliable_checkbox_selected(ns):
 try:return node_by(ns,'qos_reliable').get('checked')=='true'
 except ui.MissingTarget:return False

def nonnegative_long(value):
 return type(value) is int and 0<=value<(1<<63)

def reliable_unmatched_sample(ns,qa):
 # Cumulative zero on one stable reader is stronger than a waiting label.
 text=content(ns,'counts')
 if not waiting_for_match(ns) or not re.match(r'^AGUARDANDO · 0 msgs(?: · [0-9]+[.][0-9]+ Hz recebidos)?$',text.splitlines()[0]):return False
 if type(qa) is not dict:return False
 if type(qa.get('schemaVersion')) is not int or qa['schemaVersion']!=1:return False
 if qa.get('valid') is not True or qa.get('foreground') is not True or qa.get('clock')!='java.nanoTime':return False
 if qa.get('applicationId')!=PKG or qa.get('debuggable') is not True:return False
 for key in ('pid','captureAgeNs','maxCaptureAgeNs','capturedMonotonicNs','exportedMonotonicNs','lifecycleGeneration','policyGeneration','sessionGeneration'):
  if not nonnegative_long(qa.get(key)):return False
 if qa['pid']==0 or qa['maxCaptureAgeNs']!=2000000000:return False
 if not 0<=qa['captureAgeNs']<=qa['maxCaptureAgeNs'] or qa['exportedMonotonicNs']-qa['capturedMonotonicNs']!=qa['captureAgeNs']:return False
 watches=qa.get('watches')
 if type(watches) is not list or len(watches)!=4:return False
 for slot,w in enumerate(watches):
  if type(w) is not dict or type(w.get('slot')) is not int or w['slot']!=slot or w.get('valid') is not True:return False
 w=watches[0]
 for key in ('count','matched','requestVersion','appliedVersion','readerGeneration'):
  if not nonnegative_long(w.get(key)):return False
 return bool(w.get('requestedReliable') is True and w.get('requestedTransientLocal') is False
  and w.get('pending') is False and w['appliedVersion']==w['requestVersion']
  and w.get('readerActive') is True and w.get('state')=='waiting'
  and w['count']==0 and w['matched']==0 and w.get('hasError') is False
  and w.get('nativeFresh') is False and w.get('freshAtExport') is False)

def best_effort_writer_visible(ns,topic):
 for node in ns:
  if node.get('package')!=PKG or node.get('content-desc')!='Tópico '+topic:continue
  text=node.get('text','')
  return bool(text.startswith(topic+'\n') and re.search(r'(?m)^([1-9][0-9]*) pub · [0-9]+ sub$',text))
 return False

def best_effort_offered(ns,topic):
 try:text=node_by(ns,'explorer_details').get('text','')
 except ui.MissingTarget:return False
 if not text.startswith(topic+'\nstd_msgs/msg/String\n') or not FIXTURE_OWNER:return False
 matches=[]
 for section in text.split('\n\n'):
  lines=section.splitlines()
  if not lines or lines[0]!='PUBLICADOR (oferecido)' or 'reliability: best_effort' not in lines:continue
  gids=[line[5:] for line in lines if line.startswith('GID: ')]
  nodes=[line[len('Nó(s) por GID: '):] for line in lines if line.startswith('Nó(s) por GID: ')]
  if len(gids)==1 and re.fullmatch(r'[0-9a-f]{32}',gids[0]) and len(nodes)==1 and '/pocket_fixture_'+FIXTURE_OWNER in nodes[0].split(', '):matches.append(gids[0])
 return matches[0] if len(matches)==1 else False

def publication_events_valid(events,gid):
 if type(gid) is not str or type(events) is not list or not events or len(events)>128:return False
 for event in events:
  if type(event) is not dict:return False
  if not nonnegative_long(event.get('sequence')) or not nonnegative_long(event.get('monotonicNs')):return False
  if event.get('owner')!=FIXTURE_OWNER or not FIXTURE_OWNER:return False
  if event.get('writerGid')!=gid or not re.fullmatch(r'[0-9a-f]{32}',gid):return False
  if event.get('topic')!=fixture_topic('chatter') or event.get('node')!='/pocket_fixture_'+FIXTURE_OWNER:return False
  if event.get('reliability')!='best_effort':return False
 return all(b['sequence']==a['sequence']+1 and b['monotonicNs']>a['monotonicNs'] for a,b in zip(events,events[1:]))

def sustained_qos_unmatched(ns,state,events,now_ns,qa,gid):
 # Fixture and harness share the host Linux monotonic clock. Java's clock
 # is used only for strictly advancing QA captures, never compared to host.
 if not reliable_unmatched_sample(ns,qa) or not nonnegative_long(now_ns) or not publication_events_valid(events,gid):
  state.clear();return False
 latest=events[-1]
 if not 0<=now_ns-latest['monotonicNs']<=1500000000:
  state.clear();return False
 w=qa['watches'][0]
 identity=tuple(qa[k] for k in ('pid','lifecycleGeneration','policyGeneration','sessionGeneration'))+tuple(w[k] for k in ('slot','requestVersion','appliedVersion','readerGeneration'))
 capture=qa['capturedMonotonicNs']
 if state and (state['identity']!=identity or state['gid']!=gid or capture<=state['capture'] or now_ns<=state['observedNs']):
  state.clear();return False
 if not state:
  state.update(startedNs=now_ns,observedNs=now_ns,capture=capture,initialCaptureNs=capture,identity=identity,gid=gid,anchor=dict(latest),observations=1)
  return False
 history=[event for event in events if event['sequence']>=state['anchor']['sequence']]
 if not history or history[0]!=state['anchor'] or any(b['monotonicNs']-a['monotonicNs']>1500000000 for a,b in zip(history,history[1:])):
  state.clear();return False
 state.update(observedNs=now_ns,capture=capture,observations=state['observations']+1)
 if state['observations']<3 or latest['monotonicNs']-state['startedNs']<5000000000 or len(history)<4:return False
 if 'closingFenceNs' not in state:
  # This export happened AFTER the qualifying publication history read.
  # A later cached capture is insufficient: require a worker capture newer
  # than this export, using only the Android process's own clock.
  state['closingFenceNs']=qa['exportedMonotonicNs']
  state['qualified']=dict(durationNs=now_ns-state['startedNs'],publicationSpanNs=latest['monotonicNs']-history[0]['monotonicNs'],publicationEvents=len(history),sequenceStart=history[0]['sequence'],sequenceEnd=latest['sequence'],writerGid=gid,qaIdentity=identity)
  return False
 if capture<=state['closingFenceNs'] or capture-state['initialCaptureNs']<5000000000:return False
 # Keep the qualified producer interval fixed. This new zero-count capture
 # closes THAT interval, not a later log event that may postdate the capture.
 return dict(state['qualified'],qaObservations=state['observations'],qaCaptureSpanNs=capture-state['initialCaptureNs'],closingFenceNs=state['closingFenceNs'],closingCaptureNs=capture)

def fixture_heartbeats():
 if not FIXTURE_NAME or not FIXTURE_OWNER:raise RuntimeError('No owned fixture is active')
 inspection=subprocess.run(['docker','inspect',FIXTURE_NAME],check=True,capture_output=True,text=True,timeout=5)
 try:
  row=json.loads(inspection.stdout)[0];config=row['Config'];labels=config.get('Labels') or {}
  owned=labels.get('io.lamggm.pocket.harness-owner')==FIXTURE_OWNER and row['State']['Running'] is True
  owned=owned and any(m.get('Source')==str(ROOT/'tests/fixture.py') for m in row['Mounts']) and 'ROS_DOMAIN_ID=67' in config['Env']
  owned=owned and 'POCKET_QOS_HEARTBEAT='+FIXTURE_OWNER in config['Env']
  container_id=row['Id']
 except (KeyError,TypeError,ValueError,IndexError,AttributeError):raise RuntimeError('Invalid fixture ownership metadata') from None
 if not owned or type(container_id) is not str or not re.fullmatch(r'[0-9a-f]{64}',container_id):raise RuntimeError('Fixture ownership mismatch; refusing heartbeat')
 result=subprocess.run(['docker','logs','--tail','128',container_id],check=True,capture_output=True,text=True,timeout=5)
 if len(result.stdout.encode('utf-8'))>98304:raise RuntimeError('Oversized fixture heartbeat history')
 try:events=[json.loads(line[len('POCKET_QOS_EVENT '):]) for line in result.stdout.splitlines() if line.startswith('POCKET_QOS_EVENT ')]
 except (ValueError,RecursionError):raise RuntimeError('Malformed fixture publication heartbeat') from None
 if not events:raise RuntimeError('Fixture publication heartbeat is missing')
 return events

def observe_qos_window(ns,state,gid):
 events=fixture_heartbeats()
 qa=ui.client().qa()
 return sustained_qos_unmatched(ns,state,events,time.monotonic_ns(),qa,gid)

def fixture_topics_absent(ns):
 own=[n for n in ns if n.get('package')==PKG]
 return any(n.get('text')==EXPLORER_EMPTY_TEXT for n in own) and not any(n.get('content-desc','').startswith('Tópico /pocket_test/') for n in own)

def string_after(ns,baseline):
 observed=receiving(ns)
 if not observed or not state_is(ns,'counts','RECEBENDO'):return False
 match=re.fullmatch(r'pocket-real-jazzy seq=([0-9]{1,18})',observed['message'])
 return dict(observed,sequence=int(match[1])) if match and int(match[1])>baseline else False

def numeric_after(ns,baseline):
 value=ui.finite_number(content(ns,'message'))
 return {'counts':content(ns,'counts'),'value':value} if state_is(ns,'counts','RECEBENDO') and value is not None and value>baseline else False

def receiving(ns):
 c=content(ns,'counts');m=content(ns,'message')
 return {'counts':c,'message':m} if 'RECEBENDO' in c and m.startswith('pocket-real-jazzy seq=') else False
def diagnostics(ns,connected=True):
 text=content(ns,'diagnostics');peers=re.search(r'DDS peers: (\d+)',text);ros=re.search(r'Nós ROS: (\d+)',text)
 if not peers or not ros or 'Interface:' not in text or 'domínio 67' not in text:return False
 if connected and int(peers[1])<1:return False
 if not connected and (int(peers[1])!=0 or int(ros[1])!=0):return False
 return {'ddsPeers':int(peers[1]),'rosNodes':int(ros[1]),'interfaceReported':True}
def launch():adb('shell','am','start','--user','0','-W','-n',PKG+'/com.lamggm.rospocket.MainActivity')
def disconnect_app(timeout=8):
 client=ui.client();client.find('connect',direction='up')
 ns=dump('final-connection');button=content(ns,'connect').casefold()
 if button=='desconectar':tap('connect')
 elif button!='conectar':raise RuntimeError('Unknown disconnect button state')
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  ns=dump('disconnect-readback');button=content(ns,'connect').casefold()
  if button not in ('conectar','desconectar'):raise RuntimeError('Unknown disconnect button state')
  if button=='conectar' and content(ns,'status') in ('Desconectado · nenhum leitor DDS ativo','Desconectado. Use o mesmo Domain ID e Wi-Fi do ROS 2.'):return True
  time.sleep(.2)
 raise RuntimeError('Could not confirm app disconnect within bounded readback')

def docker_missing(result,target):
 return result.returncode==1 and re.fullmatch(r'(?i:(?:error(?: response from daemon)?:\s*)?no such (?:object|container):\s*)'+re.escape(target),result.stderr.strip()) is not None

def owned_fixture_stop():
 if not FIXTURE_NAME:return
 p=subprocess.run(['docker','inspect',FIXTURE_NAME],capture_output=True,text=True,timeout=10)
 if p.returncode:
  if docker_missing(p,FIXTURE_NAME):return
  raise RuntimeError('Could not verify fixture ownership before cleanup')
 d=json.loads(p.stdout)[0]
 labels=d['Config'].get('Labels') or {}
 if labels.get('io.lamggm.pocket.harness-owner')!=FIXTURE_OWNER or not FIXTURE_OWNER or not any(m.get('Source')==str(ROOT/'tests/fixture.py') for m in d['Mounts']) or 'ROS_DOMAIN_ID=67' not in d['Config']['Env']:
  raise RuntimeError('Fixture ownership mismatch; refusing cleanup')
 container_id=d.get('Id','')
 if not re.fullmatch(r'[0-9a-f]{64}',container_id):raise RuntimeError('Fixture ownership metadata has no valid immutable container ID')
 subprocess.run(['docker','stop','-t','5',container_id],check=True,capture_output=True,timeout=15)
 for _ in range(30):
  inspection=subprocess.run(['docker','inspect',container_id],capture_output=True,text=True,timeout=5)
  if inspection.returncode:
   if docker_missing(inspection,container_id):return
   raise RuntimeError('Could not confirm owned fixture removal')
  assert not json.loads(inspection.stdout)[0]['State']['Running']
  time.sleep(.2)
 raise AssertionError('Fixture stopped but auto-removal did not complete')

def fixture_topic(name):
 return '/pocket_test/harness_'+FIXTURE_OWNER+'/'+name

def fixture_start(rmw='rmw_fastrtps_cpp',best_effort=False):
 if not ROS_IMAGE or not FIXTURE_NAME or not FIXTURE_OWNER:
  raise RuntimeError('Explicit existing ROS image and run-scoped fixture ownership are required')
 cmd=['docker','run','--pull=never','-d','--rm','--name',FIXTURE_NAME,'--label','io.lamggm.task=ros2-pocket-test','--label','io.lamggm.pocket.harness-owner='+FIXTURE_OWNER,'--network','host','--cpus','1','--memory','512m','--pids-limit','128','-e','ROS_DOMAIN_ID=67','-e','RMW_IMPLEMENTATION='+rmw,'-e','ROS_AUTOMATIC_DISCOVERY_RANGE=SUBNET','-e','ROS_LOCALHOST_ONLY=0','-e','POCKET_BEST_EFFORT='+('1' if best_effort else '0'),'-e','POCKET_QOS_HEARTBEAT='+(FIXTURE_OWNER if best_effort else ''),'-v',str(ROOT/'tests/fixture.py')+':/pocket-fixture.py:ro','--entrypoint','bash',ROS_IMAGE,'-lc','source /opt/ros/jazzy/setup.bash && exec timeout 1800 python3 /pocket-fixture.py']
 ros_args=['--ros-args','-r','__node:=pocket_fixture_'+FIXTURE_OWNER]
 for name in ('chatter','value'):ros_args+=['-r','/pocket_test/'+name+':='+fixture_topic(name)]
 cmd[-1]+=' '+shlex.join(ros_args)
 if rmw=='rmw_cyclonedds_cpp' and os.environ.get('POCKET_FIXTURE_INTERFACE'):
  interface=os.environ['POCKET_FIXTURE_INTERFACE']
  if not re.fullmatch(r'[A-Za-z0-9_.:\-]{1,15}',interface):raise RuntimeError('Invalid explicit fixture interface')
  uri='<CycloneDDS><Domain><General><Interfaces><NetworkInterface name="'+interface+'"/></Interfaces></General></Domain></CycloneDDS>'
  cmd[2:2]=['-e','CYCLONEDDS_URI='+uri]
 subprocess.run(cmd,check=True,capture_output=True,timeout=45)

def run():
 global results,FIXTURE_NAME,FIXTURE_OWNER,ROS_IMAGE
 results=[]
 FIXTURE_OWNER=uuid.uuid4().hex
 FIXTURE_NAME='ros2-pocket-tablet-'+FIXTURE_OWNER[:16]
 ROS_IMAGE=os.environ.get('POCKET_ROS_IMAGE','')
 client=ui.client()
 report={'kind':client.kind,'scope':'basic_regression_not_final_v02_qualification','passed':False,'checks':results}
 failure=None
 fixture_attempted=False
 app_prepared=False
 try:
  peer=os.environ.get('POCKET_BENCH_PEER','')
  if peer:ipaddress.IPv4Address(peer)
  report['connectionConfig']={'peerMode':'explicit' if peer else 'automatic','peerIpv4':peer}
  client.verify()
  app_prepared=True
  launch()
  client.find('connect',direction='up')
  if node_by(dump('initial-connection'),'connect').get('text','').casefold()=='desconectar':tap('connect')
  field('domain','67');field('peer',peer)
  tap('connect')
  fixture_attempted=True
  fixture_start()
  client.find('diagnostics',direction='up')
  for ident in ('explorer_search','explorer_namespace','explorer_type'):
   client.find(ident,direction='down');field(ident,fixture_topic('chatter') if ident=='explorer_search' else '')
  client.hide_ime()
  if node_by(client.find('explorer_favorites',direction='down'),'explorer_favorites').get('checked')=='true':ui.tap('explorer_favorites')
  tap('watch_select_0')
  select_topic(fixture_topic('chatter'))
  for ident in ('qos_reliable','qos_transient'):
   if node_by(client.find(ident,direction='down'),ident).get('checked')=='true':ui.tap(ident)
  a=check('FastDDS String receive',receiving,view='message')
  first=int(a['message'].split('=')[-1])
  check('message sequence advances',lambda ns:string_after(ns,first),view='message')
  check('ROS node graph',lambda ns:content(ns,'nodes') if '/pocket_fixture_'+FIXTURE_OWNER in content(ns,'nodes') else False,view='nodes')
  check('connection diagnostics separate DDS and ROS',diagnostics,view='diagnostics')
  # Lifecycle: do not inspect the launcher or any unrelated foreground application.
  before_home=int(check('message before Home',receiving,view='message')['message'].split('=')[-1])
  adb('shell','am','start','--user','0','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
  time.sleep(1);launch()
  home=check('Home return resumes selected reader',receiving,view='message')
  check('Home return receives new sample',lambda ns:string_after(ns,max(before_home,int(home['message'].split('=')[-1]))),view='message')
  # Peer removal must age the visible data; no fabricated offline samples.
  owned_fixture_stop()
  check('publisher loss marks data stale',lambda ns:content(ns,'counts') if 'SEM DADOS RECENTES' in content(ns,'counts') else False,view='counts',direction='up')
  check('departed node removed',lambda ns:'node absent' if '/pocket_fixture_'+FIXTURE_OWNER not in content(ns,'nodes') and content(ns,'nodes') else False,timeout=35,view='nodes')
  check('peer loss diagnostics reconnecting',lambda ns:diagnostics(ns,False) if state_is(ns,'status','RECONECTANDO') else False,timeout=35,view='diagnostics')
  fixture_start('rmw_cyclonedds_cpp',True)
  replacement=check('CycloneDDS replacement automatically received',receiving,timeout=35,view='message')
  check('replacement produces a new sample',lambda ns:string_after(ns,int(replacement['message'].split('=')[-1])),view='message')
  check('peer return diagnostics connected',lambda ns:diagnostics(ns) if state_is(ns,'status','CONECTADO') else False,view='diagnostics')
  check('QoS control visible after diagnostics',lambda ns:any(n.get('resource-id')==PKG+':id/qos_reliable' for n in ns),view='qos_reliable')
  tap('qos_reliable')
  check('Reliable checkbox confirms selection',lambda ns:'checked' if reliable_checkbox_selected(ns) else False,view='qos_reliable')
  topic=fixture_topic('chatter')
  check('Best-effort writer remains announced',lambda ns:'one best-effort writer' if best_effort_writer_visible(ns,topic) else False,view='Tópico '+topic,direction='up')
  details=('Detalhes / QoS · '+topic).upper() # Android's visible Button caption; ROS identity checks stay exact.
  check('QoS details control is visible',lambda ns:'exact-topic QoS control' if node_by(ns,details) is not None else False,view=details,direction='down')
  tap(details)
  writer_gid=check('DDS discovery confirms owned writer offers best_effort',lambda ns:best_effort_offered(ns,topic),view='explorer_details',direction='up')
  qos_window={}
  check('Reliable stays unmatched while fixture publishes',lambda ns:observe_qos_window(ns,qos_window,writer_gid),timeout=25,view='counts',direction='down')
  tap('qos_reliable');restored=check('best effort restores live data',receiving,view='message')
  check('restored reader advances',lambda ns:string_after(ns,int(restored['message'].split('=')[-1])),view='message')
  # Change domain only after explicit disconnect; peer remains empty.
  tap('connect');field('domain','68');tap('connect')
  check('wrong domain isolates fixture',lambda ns:content(ns,'status') if 'domínio 68' in content(ns,'status') and 'Descoberta em andamento' in content(ns,'status') else False,view='status')
  client.find('explorer_search',direction='up');field('explorer_search','/pocket_test/');client.hide_ime()
  check('wrong domain fixture list is explicitly empty',fixture_topics_absent,view=EXPLORER_EMPTY_TEXT)
  time.sleep(3)
  check('wrong domain fixture list remains empty',fixture_topics_absent,view=EXPLORER_EMPTY_TEXT)
  tap('connect');field('domain','67');tap('connect')
  check('correct domain rediscovers',lambda ns:'fixture discovered' if any(n.get('content-desc')=='Tópico '+fixture_topic('chatter') for n in ns) else False,view='Tópico '+fixture_topic('chatter'))
  select_topic(fixture_topic('value'))
  check('Float64 decoder receives numeric sample',lambda ns:{'counts':content(ns,'counts'),'value':content(ns,'message')} if 'RECEBENDO' in content(ns,'counts') and ui.finite_number(content(ns,'message')) is not None else False,view='message')
  # Returning from Home must preserve a numeric reader as well.
  adb('shell','am','start','--user','0','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
  time.sleep(1);launch()
  numeric_home=check('Home return preserves Float64 reader',lambda ns:{'counts':content(ns,'counts'),'value':content(ns,'message')} if state_is(ns,'counts','RECEBENDO') and ui.finite_number(content(ns,'message')) is not None else False,view='message')
  check('Float64 after Home advances numerically',lambda ns:numeric_after(ns,ui.finite_number(numeric_home['value'])),view='message')
  # Fresh process retains domain/empty-peer selection but deliberately requires a new topic selection.
  adb('shell','am','force-stop','--user','0',PKG);launch()
  check('process restart persists domain',lambda ns:content(ns,'domain') if content(ns,'domain')=='67' and 'domínio 67' in content(ns,'status') else False,view='domain')
  check('process restart rediscovers',lambda ns:'fixture discovered' if any(n.get('content-desc')=='Tópico '+fixture_topic('chatter') for n in ns) else False,view='Tópico '+fixture_topic('chatter'))
  select_topic(fixture_topic('chatter'));restarted=check('reader after process restart',receiving,view='message')
  check('restarted process receives a new sample',lambda ns:string_after(ns,int(restarted['message'].split('=')[-1])),view='message')
  report['passed']=True
 except BaseException as exc:
  failure=exc
  report['error']=str(exc)
 finally:
  for stage,enabled in (('fixture',fixture_attempted),('app',app_prepared)):
   if not enabled:continue
   try:
    if stage=='fixture':owned_fixture_stop()
    else:disconnect_app()
   except BaseException as exc:
    report.setdefault('cleanupErrors',[]).append({'stage':stage,'error':str(exc)})
    report['passed']=False
    if failure is None:failure=exc
  path=client.out/('regression-results-'+uuid.uuid4().hex+'.json')
  path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
  print('RESULT',path,flush=True)
 if failure is not None:raise failure
 print('COMPLETE',len(results),'basic tablet checks; not final v0.2 qualification')
 return report


if __name__ == "__main__":
 run()
