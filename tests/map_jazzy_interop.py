#!/usr/bin/env python3
"""Real ROS publisher -> production native map/TF reader; isolated fixture only."""
import argparse,json,math,os,pathlib,signal,struct,subprocess,time

def main():
 import rclpy
 from rclpy.qos import QoSProfile,ReliabilityPolicy,DurabilityPolicy,HistoryPolicy
 from nav_msgs.msg import OccupancyGrid
 from tf2_msgs.msg import TFMessage
 from geometry_msgs.msg import TransformStamped
 p=argparse.ArgumentParser();p.add_argument('--probe',required=True);p.add_argument('--out',required=True);p.add_argument('--loader-dir');a=p.parse_args();out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True)
 # Private network and Domain81 are established by the runner. Avoid redundant
 # ROS_LOCALHOST_ONLY + explicit Cyclone interface injection in Cyclone fixtures.
 os.environ['ROS_DOMAIN_ID']='81'
 if os.environ.get('RMW_IMPLEMENTATION')=='rmw_cyclonedds_cpp':
  os.environ.pop('ROS_LOCALHOST_ONLY',None)
  os.environ['CYCLONEDDS_URI']='<CycloneDDS><Domain Id="any"><General><Interfaces><NetworkInterface address="127.0.0.1" multicast="true"/></Interfaces><AllowMulticast>spdp</AllowMulticast></General><Discovery><ParticipantIndex>auto</ParticipantIndex></Discovery></Domain></CycloneDDS>'
 rclpy.init();node=rclpy.create_node('pocket_location_fixture',enable_rosout=False,start_parameter_services=False);child=None
 try:
  qos=QoSProfile(history=HistoryPolicy.KEEP_LAST,depth=1,reliability=ReliabilityPolicy.RELIABLE,durability=DurabilityPolicy.TRANSIENT_LOCAL)
  pub=node.create_publisher(OccupancyGrid,'/location/map',qos);tf=node.create_publisher(TFMessage,'/location/tf',QoSProfile(depth=8,reliability=ReliabilityPolicy.BEST_EFFORT))
  grid=OccupancyGrid();grid.header.frame_id='map';grid.header.stamp.sec=8;grid.header.stamp.nanosec=2;grid.info.width=3;grid.info.height=2;grid.info.resolution=.2;grid.info.origin.position.x=1.5;grid.info.origin.position.y=-2.;grid.info.origin.orientation.z=math.sin(.2);grid.info.origin.orientation.w=math.cos(.2);grid.data=[-1,0,25,50,75,100]
  pub.publish(grid) # Exactly once, BEFORE the native map reader starts.
  def publish_tf():
   t=TransformStamped();t.header.frame_id='map';t.child_frame_id='base_link';t.header.stamp=node.get_clock().now().to_msg();t.transform.translation.x=1.7;t.transform.translation.y=-1.8;t.transform.rotation.w=1.;message=TFMessage();message.transforms=[t];tf.publish(message)
  node.create_timer(.05,publish_tf)
  command=[a.probe,str(out/'map.packet'),str(out/'tf.json')]
  if a.loader_dir:command=[str(pathlib.Path(a.loader_dir)/'ld-linux-x86-64.so.2'),'--library-path',a.loader_dir]+command
  child_env=os.environ.copy();child_env.pop('LD_LIBRARY_PATH',None);child_env.pop('CYCLONEDDS_URI',None)
  with (out/'probe.stdout').open('wb') as stdout,(out/'probe.stderr').open('wb') as stderr:
   child=subprocess.Popen(command,stdout=stdout,stderr=stderr,env=child_env,start_new_session=True);deadline=time.monotonic()+12
   while child.poll() is None and time.monotonic()<deadline:rclpy.spin_once(node,timeout_sec=.02)
   if child.poll() is None:raise TimeoutError('Native location probe exceeded original12s bound')
   assert child.returncode==0,('native probe failure',child.returncode)
  raw=(out/'map.packet').read_bytes();n=struct.unpack('<I',raw[:4])[0];meta=json.loads(raw[4:4+n]);payload=raw[4+n:]
  assert meta['kind']=='map' and meta['frameId']=='map' and meta['width']==3 and meta['height']==2 and payload==bytes([255,0,25,50,75,100]);assert meta['stampSec']==8 and meta['stampNanosec']==2;assert math.isclose(meta['resolution'],.2,rel_tol=1e-6) and meta['originX']==1.5 and meta['originY']==-2 and math.isclose(meta['originYaw'],.4,abs_tol=1e-6)
  pose=json.loads((out/'tf.json').read_text());assert pose['running'] and not pose['error'];rows=[r for r in pose['rows'] if r['parent']=='map' and r['child']=='base_link'];assert rows and rows[0]['translation']==[1.7,-1.8,0] and not rows[0]['static']
  result={'passed':True,'rmw':os.environ.get('RMW_IMPLEMENTATION'),'map_published_before_reader':True,'map_publications':1,'pose_tf_without_urdf':True,'native_map_bytes':len(raw),'no_robot_hardware':True};(out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
 finally:
  if child is not None and child.poll() is None:os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=5)
  node.destroy_node();rclpy.shutdown()
if __name__=='__main__':main()
