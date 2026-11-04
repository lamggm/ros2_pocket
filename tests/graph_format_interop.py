"""Real rclpy/native graph interop, private-loopback-only; not Android acceptance."""
import argparse,json,os,pathlib,socket,subprocess,hashlib,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('--probe',type=pathlib.Path,required=True);p.add_argument('--mode',choices=['humble','jazzy'],required=True);p.add_argument('--distro',choices=['humble','jazzy'],required=True);p.add_argument('--out',type=pathlib.Path,required=True);p.add_argument('--expect-incompatible',action='store_true');a=p.parse_args()
 if {name for _,name in socket.if_nameindex()}!={'lo'}:raise RuntimeError('Run only inside an owned network-none/private-loopback namespace')
 if os.environ.get('ROS_DISTRO')!=a.distro:raise RuntimeError('Actual sourced ROS distro differs from request')
 if os.environ.get('RMW_IMPLEMENTATION') not in ['rmw_fastrtps_cpp','rmw_cyclonedds_cpp']:raise RuntimeError('Explicit tested RMW required')
 out=a.out/uuid.uuid4().hex;out.mkdir(parents=True,exist_ok=False)
 env=os.environ.copy();env['ROS_DOMAIN_ID']='86';native_env=env.copy();native_env.pop('LD_LIBRARY_PATH',None)
 ldd=subprocess.run(['ldd',str(a.probe)],env=native_env,capture_output=True,text=True,check=True);(out/'native-ldd.log').write_text(ldd.stdout+ldd.stderr)
 if 'not found' in ldd.stdout or 'libddsc.so' not in ldd.stdout:raise RuntimeError('Native DDS loader unresolved')
 with (out/'producer.log').open('w') as stdout,(out/'producer.err').open('w') as stderr:
  producer=subprocess.Popen(['/usr/bin/python3',str(pathlib.Path(__file__).with_name('graph_format_fixture.py'))],env=env,stdout=stdout,stderr=stderr)
  try:
   native=subprocess.run([str(a.probe),a.mode],env=native_env,capture_output=True,text=True,timeout=20)
   (out/'native.jsonl').write_text(native.stdout);(out/'native.err').write_text(native.stderr);alive=producer.poll() is None
  except subprocess.TimeoutExpired as error:
   # TimeoutExpired may expose bytes even when subprocess.run(text=True).
   for name,body in [('native.jsonl',error.stdout),('native.err',error.stderr)]:
    (out/name).write_bytes(body if isinstance(body,bytes) else (body or '').encode('utf-8'))
   raise
  finally:
   if producer.poll() is None:producer.terminate()
   try:producer.wait(timeout=5)
   except subprocess.TimeoutExpired:producer.kill();producer.wait(timeout=3)
 rows=[json.loads(line) for line in native.stdout.splitlines() if line.strip()]
 assert len(rows)>=5 and all(row.get('ok') is True for row in rows[:3]),rows[:3]
 snapshots=rows[3:];last=snapshots[-1]
 advancing=[s for s in snapshots if s['count']>0 and s['watches'][1]['count']>0 and s['rosout']['count']>0];assert len(advancing)>=2
 first=advancing[0];node='/spike_v08/pocket_v08_fixture';topics=['/pocket_test/chatter','/pocket_test/value']
 associations=all(any(ep['writer'] and node in ep['nodes'] for row in last['topics'] if row['name']==topic for ep in row['endpoints']) for topic in topics)
 node_present=node in last['nodes'];discovered={row['name'] for row in last['topics'] if row['publishers']>0}
 result={'classification':'real ROS/native isolated loopback; no Android/LAN/soak','distro':a.distro,'mode':a.mode,'rmw':env['RMW_IMPLEMENTATION'],'snapshots':len(snapshots),'discovery':set(topics+['/rosout'])<=discovered,'String':last['count']>first['count'] and ('pocket-real-'+a.distro+' seq=') in last['last'],'Float64':last['watches'][1]['count']>first['watches'][1]['count'] and float(last['watches'][1]['last'])%1==0.25,'rosout':last['rosout']['count']>first['rosout']['count'] and any(e['logger']=='pocket_v08_fixture' and e['message'].startswith('real-'+a.distro+'-log-') for e in last['rosout']['entries']),'node':node_present,'associations':associations,'graph':last['graph'],'source_probe_sha256':hashlib.sha256(a.probe.read_bytes()).hexdigest(),'producer_alive_at_end':alive,'native_exit':native.returncode,'raw_output':str(out)}
 (out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
 assert native.returncode==0 and alive and all(result[k] for k in ['discovery','String','Float64','rosout']),result
 assert last['graph']['format']==a.mode and last['graph']['selection']=='manual'
 if a.expect_incompatible:
  assert not node_present and not associations and last['graph']['state'] in ['unconfirmed','unsupported'],result
 else:assert node_present and associations and last['graph']['state']=='received',result

if __name__=='__main__':main()
