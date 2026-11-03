"""Real rclpy fixture, only standard read-only-test topics, domain supplied by runner."""
import os,time,json
import rclpy
from rclpy.node import Node
from std_msgs.msg import String,Float64
from rcl_interfaces.msg import Log
from rclpy.qos import QoSProfile,ReliabilityPolicy,DurabilityPolicy
rclpy.init()
node=Node('pocket_v08_fixture',namespace='/spike_v08')
q=QoSProfile(depth=10)
text=node.create_publisher(String,'/pocket_test/chatter',q)
number=node.create_publisher(Float64,'/pocket_test/value',q)
logs=node.create_publisher(Log,'/rosout',QoSProfile(depth=16,reliability=ReliabilityPolicy.RELIABLE,durability=DurabilityPolicy.TRANSIENT_LOCAL))
sequence=0
print('IDENTITY '+json.dumps({'distro':os.environ['ROS_DISTRO'],'rmw':os.environ['RMW_IMPLEMENTATION'],'node':node.get_fully_qualified_name()}),flush=True)
def publish():
    global sequence
    sequence+=1
    text.publish(String(data=f"pocket-real-{os.environ['ROS_DISTRO']} seq={sequence}"))
    number.publish(Float64(data=sequence+0.25))
    entry=Log();entry.stamp=node.get_clock().now().to_msg();entry.level=20;entry.name='pocket_v08_fixture';entry.msg=f'real-{os.environ["ROS_DISTRO"]}-log-{sequence}';entry.file='baseline-fixture.py';entry.function='publish';entry.line=sequence;logs.publish(entry)
node.create_timer(0.1,publish)
try:
    deadline=time.monotonic()+25
    while rclpy.ok() and time.monotonic()<deadline:rclpy.spin_once(node,timeout_sec=0.2)
finally:
    node.destroy_node();rclpy.shutdown()
