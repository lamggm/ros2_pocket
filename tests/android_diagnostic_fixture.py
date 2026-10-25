"""Parent-operated isolated domain67 fixture for the actual instrumentation APK.
Not run by portable host tests; never use against a robot or ordinary DDS domain.
"""
import os
if os.environ.get('ROS_DOMAIN_ID') != '67':
    raise SystemExit('explicit isolated ROS_DOMAIN_ID=67 required')
import rclpy
from std_msgs.msg import String
from rcl_interfaces.msg import Log
from rclpy.qos import QoSProfile, ReliabilityPolicy
rclpy.init()
node=rclpy.create_node('pocket_diagnostic_fixture',enable_rosout=False)
qos=QoSProfile(depth=64,reliability=ReliabilityPolicy.RELIABLE)
pubs=[node.create_publisher(String,f'/pocket_diagnostic/watch{i}',qos) for i in range(4)]
logs=node.create_publisher(Log,'/rosout',qos)
sequence=0

def publish():
    global sequence
    sequence+=1
    for slot,pub in enumerate(pubs):
        pub.publish(String(data=f'diagnostic-slot-{slot} seq={sequence}'))
    log=Log();log.stamp=node.get_clock().now().to_msg();log.level=20
    log.name='pocket_diagnostic.logger';log.msg=f'diagnostic-log seq={sequence}';log.line=sequence
    logs.publish(log)

node.create_timer(0.5,publish)
try:
    rclpy.spin(node)
finally:
    node.destroy_node();rclpy.shutdown()
