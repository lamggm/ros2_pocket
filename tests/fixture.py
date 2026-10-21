"""Explicit synthetic fixture: publishes only test topics in isolated domain."""
import os
import rclpy
from rclpy.node import Node
from std_msgs.msg import String, Bool, Int32, Int64, Float32, Float64
from nav_msgs.msg import Odometry
from geometry_msgs.msg import Vector3, Quaternion, Twist, Pose, Point
from rclpy.qos import QoSProfile, ReliabilityPolicy
from rclpy.qos import DurabilityPolicy
rclpy.init()
node=Node("pocket_fixture")
q=QoSProfile(depth=10, reliability=ReliabilityPolicy.BEST_EFFORT if os.environ.get("POCKET_BEST_EFFORT")=="1" else ReliabilityPolicy.RELIABLE)
pub=node.create_publisher(String,"/pocket_test/chatter",q)
number=node.create_publisher(Float64,"/pocket_test/value",q)
vector=node.create_publisher(Vector3,"/pocket_test/vector3",q)
quaternion=node.create_publisher(Quaternion,"/pocket_test/quaternion",q)
twist=node.create_publisher(Twist,"/pocket_test/twist",q)
pose=node.create_publisher(Pose,"/pocket_test/pose",q)
odometry=node.create_publisher(Odometry,"/pocket_test/odometry",q)
special=node.create_publisher(Vector3,"/pocket_test/special",q)
long_frame=node.create_publisher(Odometry,"/pocket_test/long_frame",q)
primitives=[(node.create_publisher(t,"/pocket_test/primitive_"+name,q),t(data=value)) for name,t,value in [
 ("long",String,"a"*8191+"🤖"*512),("string",String,"ação_世界_🤖"),("bool",Bool,True),("int32",Int32,-2147483647),
 ("int64",Int64,-9223372036854775808),("float32",Float32,0.125),("float64",Float64,1.2345678901234567)]]
rosout_pub=None
rosout_sequence=0
if os.environ.get("POCKET_ROSOUT_FIXTURE")=="1":
    from rcl_interfaces.msg import Log
    rosout_qos=QoSProfile(depth=512,reliability=ReliabilityPolicy.RELIABLE,durability=DurabilityPolicy.TRANSIENT_LOCAL)
    rosout_pub=node.create_publisher(Log,"/rosout",rosout_qos)
    def publish_rosout():
        global rosout_sequence
        # Wait for the independently built DDS Log reader to match; never rely on
        # a fixed startup sleep or volatile one-shot samples before discovery.
        if rosout_pub.get_subscription_count()==0:return
        if rosout_sequence>=800:return
        rosout_sequence+=1
        now=node.get_clock().now().to_msg()
        text=("🚀 ROSOUT_UTF8_LONG_"+"ação λ "*1200+"_TRAILER") if rosout_sequence==790 else f"fixture-sample-{rosout_sequence}"
        sample=Log();sample.stamp=now;sample.level=(10,20,30,40,50)[(rosout_sequence-1)%5];sample.name="pocket_fixture.logger";sample.msg=text;sample.file="fixture_ação.py";sample.function="publish_rosout";sample.line=rosout_sequence
        if rosout_sequence==800:sample.msg="FINAL_SENTINEL"
        rosout_pub.publish(sample)
    node.create_timer(0.025,publish_rosout)
sequence=0
def publish():
 global sequence
 sequence+=1
 for publisher,msg in primitives: publisher.publish(msg)
 special.publish(Vector3(x=float("nan"),y=float("inf"),z=-float("inf")))
 odom=Odometry()
 odom.header.stamp.sec=1700000123
 odom.header.stamp.nanosec=sequence
 odom.header.frame_id="map_ação_世界_🤖"
 odom.child_frame_id="base_ç_🚀"
 odom.pose.pose=Pose(position=Point(x=1.25,y=-2.5,z=3.75),orientation=Quaternion(x=1.25,y=-2.5,z=3.75,w=0.125))
 odom.pose.covariance=[i/4.0 for i in range(36)]
 odom.twist.twist=Twist(linear=Vector3(x=1.25,y=-2.5,z=3.75),angular=Vector3(x=1.25,y=-2.5,z=3.75))
 odom.twist.covariance=[-i/8.0 for i in range(36)]
 odometry.publish(odom)
 odom.header.frame_id="a"*511+"🤖"*4096
 odom.child_frame_id="界"*4096
 long_frame.publish(odom)
 pose.publish(Pose(position=Point(x=1.25,y=-2.5,z=3.75),orientation=Quaternion(x=1.25,y=-2.5,z=3.75,w=0.125)))
 vector.publish(Vector3(x=1.25,y=-2.5,z=3.75))
 quaternion.publish(Quaternion(x=1.25,y=-2.5,z=3.75,w=0.125))
 twist.publish(Twist(linear=Vector3(x=1.25,y=-2.5,z=3.75),angular=Vector3(x=1.25,y=-2.5,z=3.75)))
 pub.publish(String(data=f"pocket-real-jazzy seq={sequence}"))
 number.publish(Float64(data=sequence/10.0))
node.create_timer(0.2,publish)
try:rclpy.spin(node)
finally:node.destroy_node(); rclpy.shutdown()
