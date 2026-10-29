#!/usr/bin/env python3
"""Real Jazzy publishers and native reader, exclusively inside a network-none container."""
import array, json, os, random, select, struct, subprocess, sys, time, zlib
import rclpy
from sensor_msgs.msg import Image, CompressedImage, LaserScan
from std_msgs.msg import Float64
from rclpy.qos import QoSProfile, ReliabilityPolicy, DurabilityPolicy

def png(width,height):
    def chunk(kind, data):
        return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
    rng=random.Random(42)
    rows=b''.join(b'\0'+rng.randbytes(width*3) for _ in range(height))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')

def main():
    rclpy.init(domain_id=75);node=rclpy.create_node('pocket_v04_fixture')
    qos=QoSProfile(depth=1,reliability=ReliabilityPolicy.BEST_EFFORT,durability=DurabilityPolicy.VOLATILE)
    pubs=[node.create_publisher(t,'/v04/'+n,qos) for t,n in [(LaserScan,'scan'),(Image,'image'),(CompressedImage,'compressed'),(Float64,'numeric')]]
    scan=LaserScan();scan.header.frame_id='laser';scan.angle_min=0.;scan.angle_increment=.25;scan.angle_max=1.25;scan.range_min=.1;scan.range_max=10.;scan.ranges=[2.,3.,float('nan'),float('inf'),.01,100.]
    image=Image();image.header.frame_id='camera';image.width=320;image.height=240;image.encoding='rgb8';image.step=964;image.data=array.array('B',b'\xff\0\0'*320+b'\x99'*4)*240
    compressed=CompressedImage();compressed.header.frame_id='camera';compressed.format='rgb8; png compressed rgb8';compressed.data=array.array('B',png(320,240));assert len(compressed.data)>65536
    number=Float64();messages=[scan,image,compressed,number]
    child=subprocess.Popen([sys.argv[1]],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,bufsize=1)
    def send(command):
        child.stdin.write(command+'\n');child.stdin.flush()
        ready,_,_=select.select([child.stdout],[],[],5)
        if not ready:raise AssertionError('native probe response timeout: '+command)
        line=child.stdout.readline();assert line, 'native probe exited'
        return json.loads(line)
    def await_sample(predicate, seconds=12):
        deadline=time.monotonic()+seconds;last=None
        while time.monotonic()<deadline:
            number.data+=1
            for pub,msg in zip(pubs,messages):pub.publish(msg)
            rclpy.spin_once(node,timeout_sec=.05);last=send('poll')
            if predicate(last):return last
        raise AssertionError('sample predicate timeout: '+json.dumps(last))
    results=[]
    try:
        assert send('scan')['ok'];row=await_sample(lambda x:x['bytes']==16 and x['telemetry']['count']>0);assert row['sensor']['frameId']=='laser';baseline=row['telemetry']['count'];results.append('scan geometry/invalid ranges')
        assert send('image')['ok'];row=await_sample(lambda x:x['bytes']==231360 and x['telemetry']['count']>baseline);assert row['hex'].startswith('ff0000');baseline=row['telemetry']['count'];results.append('fragmented raw RGB/row padding >64KiB')
        image.encoding='16UC1';row=await_sample(lambda x:'Encoding' in x['sensor']['error']);assert row['bytes']==0;image.encoding='rgb8';row=await_sample(lambda x:x['bytes']==231360 and not x['sensor']['error']);results.append('unsupported encoding and same-reader recovery')
        assert send('compressed')['ok'];row=await_sample(lambda x:x['bytes']>65536 and x['telemetry']['count']>baseline);assert row['hex'].startswith('89504e470d0a1a0a');results.append('fragmented PNG >64KiB')
        original=compressed.data;compressed.data=array.array('B',b'x'*(1048576+1));row=await_sample(lambda x:'1MiB' in x['sensor']['error']);assert row['bytes']==0;compressed.data=original;await_sample(lambda x:x['bytes']>65536 and not x['sensor']['error']);results.append('compressed payload cap and recovery')
        assert send('stop')['ok'];row=send('poll');assert row['bytes']==0 and not row['sensor']['running'];results.append('stop clears sensor; telemetry alive')
        print(json.dumps({'passed':True,'rmw':os.environ['RMW_IMPLEMENTATION'],'checks':results},indent=2))
    finally:
        try:send('quit')
        except (AssertionError,BrokenPipeError,json.JSONDecodeError):pass
        child.wait(timeout=5);assert child.returncode==0
        node.destroy_node();rclpy.shutdown()
if __name__=='__main__':main()
