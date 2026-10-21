import json,os,ipaddress
expected=str(ipaddress.IPv4Address(os.environ["POCKET_EXPECT_WIFI_IPV4"]))
from tablet_ui import dump, node_by, OUT
import time
end=time.monotonic()+25
while True:
 nodes=dump("lan-binding")
 if any(n.get("content-desc")=="Tópico /pocket_test/chatter" for n in nodes):break
 if time.monotonic()>=end:break
 time.sleep(1)
status=node_by(nodes,"status")["text"]
assert "Wi-Fi "+expected in status,"Inspector selected something other than the tablet LAN"
assert any(n.get("content-desc")=="Tópico /pocket_test/chatter" for n in nodes),"ROS fixture topic not discovered through physical Wi-Fi"
print("PASS physical Android Wi-Fi discovery")
