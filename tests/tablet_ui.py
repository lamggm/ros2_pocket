"""Scoped UI automation for our development package only; never inspect other apps."""
import json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path
ADB=os.environ.get("ADB","adb")
SERIAL=os.environ.get("POCKET_TABLET","")
if not SERIAL:raise RuntimeError("Set POCKET_TABLET to your authorized adb device serial or Wi-Fi endpoint")
PKG="com.lamggm.rospocket.dev"
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"build"/"tablet-tests";OUT.mkdir(parents=True,exist_ok=True)
def adb(*args,timeout=25):
 p=subprocess.run([ADB,"-s",SERIAL,*args],capture_output=True,timeout=timeout)
 if p.returncode:raise RuntimeError(p.stderr.decode(errors="replace")[:300])
 return p.stdout.decode(errors="replace")
def focus():
 w=adb("shell","dumpsys","window")
 line=next((l for l in w.splitlines() if "mCurrentFocus=" in l),"")
 if PKG+"/" not in line:raise RuntimeError("Target app is not focused; UI automation stopped: "+line)
def dump(label="current"):
 focus(); remote="/sdcard/ros-pocket-test.xml"
 p=adb("shell","uiautomator","dump",remote)
 if "dumped to:" not in p:raise RuntimeError("Fresh UI hierarchy unavailable: "+p)
 dest=OUT/(label+".xml");adb("pull",remote,str(dest))
 root=ET.parse(dest)
 return [n.attrib for n in root.iter("node") if n.get("package")==PKG]
def node_by(nodes,ident):
 for n in nodes:
  if n.get("resource-id")==PKG+":id/"+ident or n.get("content-desc")==ident or n.get("text")==ident:return n
 raise RuntimeError("Visible target not found: "+ident)
def tap(ident):
 n=node_by(dump(),ident); bounds=list(map(int,re.findall(r"\d+",n["bounds"])));
 if len(bounds)!=4 or bounds[2]<=bounds[0] or bounds[3]<=bounds[1]:raise RuntimeError("Invalid target bounds")
 focus();adb("shell","input","tap",str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
def field(ident,value):
 tap(ident);adb("shell","input","keycombination","113","29")
 if value:adb("shell","input","text",value)
 else:adb("shell","input","keyevent","67")
 actual=node_by(dump(),ident).get("text")
 assert actual==value or (ident=="peer" and value=="" and actual.startswith("Peer IPv4 opcional")),(actual,value)
def scroll(direction):
 ns=dump("scroll-bounds")
 bounds=[list(map(int,re.findall(r"\d+",n["bounds"]))) for n in ns]
 w=max(b[2] for b in bounds);h=max(b[3] for b in bounds)
 x=w//2;lo=int(h*.85);hi=int(h*.35)
 adb("shell","input","swipe",str(x),str(lo if direction=="down" else hi),str(x),str(hi if direction=="down" else lo),"400")
def summary(label="current"):
 ns=dump(label)
 data=[{k:n.get(k) for k in ["resource-id","text","content-desc","bounds","checked","enabled"]} for n in ns if n.get("text") or n.get("content-desc")]
 (OUT/(label+".json")).write_text(json.dumps(data,ensure_ascii=False,indent=2))
 print(json.dumps(data,ensure_ascii=False,indent=2))
if __name__=="__main__":
 action=sys.argv[1]
 if action=="domain":field("domain",sys.argv[2])
 elif action=="tap":tap(sys.argv[2])
 elif action=="scroll":scroll(sys.argv[2])
 elif action=="summary":summary(sys.argv[2] if len(sys.argv)>2 else "current")
 else:raise SystemExit("unknown action")
