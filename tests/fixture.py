"""Explicit synthetic fixture: publishes only test topics in isolated domain."""
import os
import rclpy
from rclpy.node import Node
from std_msgs.msg import String, Float64
from rclpy.qos import QoSProfile, ReliabilityPolicy
rclpy.init()
node=Node("pocket_fixture")
q=QoSProfile(depth=10, reliability=ReliabilityPolicy.BEST_EFFORT if os.environ.get("POCKET_BEST_EFFORT")=="1" else ReliabilityPolicy.RELIABLE)
pub=node.create_publisher(String,"/pocket_test/chatter",q)
number=node.create_publisher(Float64,"/pocket_test/value",q)
sequence=0
def publish():
 global sequence
 sequence+=1
 pub.publish(String(data=f"pocket-real-jazzy seq={sequence}"))
 number.publish(Float64(data=sequence/10.0))
node.create_timer(0.2,publish)
try:rclpy.spin(node)
finally:node.destroy_node(); rclpy.shutdown()
