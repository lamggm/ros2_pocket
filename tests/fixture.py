"""Explicit synthetic fixture: publishes only test topics in isolated domain."""
import os
import rclpy
from rclpy.node import Node
from std_msgs.msg import String, Float64
from rclpy.qos import QoSProfile, ReliabilityPolicy
from rclpy.qos import DurabilityPolicy
rclpy.init()
node=Node("pocket_fixture")
q=QoSProfile(depth=10, reliability=ReliabilityPolicy.BEST_EFFORT if os.environ.get("POCKET_BEST_EFFORT")=="1" else ReliabilityPolicy.RELIABLE)
pub=node.create_publisher(String,"/pocket_test/chatter",q)
number=node.create_publisher(Float64,"/pocket_test/value",q)
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
 pub.publish(String(data=f"pocket-real-jazzy seq={sequence}"))
 number.publish(Float64(data=sequence/10.0))
node.create_timer(0.2,publish)
try:rclpy.spin(node)
finally:node.destroy_node(); rclpy.shutdown()
