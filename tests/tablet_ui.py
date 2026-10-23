"""Scoped UI automation for our development package only; never inspect other apps."""
import json, math, os, re, subprocess, sys, time, uuid, xml.etree.ElementTree as ET
from pathlib import Path
ADB=os.environ.get("ADB","adb")
SERIAL=os.environ.get("POCKET_TABLET","")
if not SERIAL:raise RuntimeError("Set POCKET_TABLET to your authorized adb device serial or Wi-Fi endpoint")
PKG="com.lamggm.rospocket.dev"
EMPTY_FIELD_HINTS={"peer":"Peer IPv4 opcional — deixe vazio para descoberta automática","explorer_search":"Buscar tópico, tipo ou nó","explorer_namespace":"Namespace (ex.: /robot)","explorer_type":"Filtro de tipo (ex.: std_msgs/msg)"}
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"build"/"tablet-tests";OUT.mkdir(parents=True,exist_ok=True)
def adb(*args,timeout=25):
 return client().adb(*args,timeout=timeout)
def focus():
 return client().focus()
def dump(label="current"):
 return client().dump(label)
class MissingTarget(RuntimeError):
 pass

class _PostwriteDump137(RuntimeError):
 """Boundary-qualified failure, never successful output or accepted XML."""
 def __init__(self, remote):
  super().__init__("ADB command failed (exit 137)")
  self.remote = remote

def node_by(nodes,ident):
 scoped = [n for n in nodes if n.get("package") == PKG and n.get("visible-to-user") != "false"]
 predicates = (
  lambda n: n.get("resource-id") == PKG + ":id/" + ident,
  lambda n: n.get("content-desc") == ident,
  lambda n: n.get("text") == ident,
  lambda n: "/" not in ident and n.get("text", "").casefold() == ident.casefold(),
 )
 for predicate in predicates:
  matches = [n for n in scoped if predicate(n)]
  if len(matches) > 1: raise RuntimeError("Ambiguous visible target: " + ident)
  if matches: return matches[0]
 raise MissingTarget("Visible target not found: "+ident)
def tap(ident):
 return client().tap(ident)
def field(ident,value):
 return client().field(ident,value)
def scroll(direction):
 return client().scroll(direction)
def summary(label="current"):
 ns=dump(label)
 data=[{k:n.get(k) for k in ["resource-id","text","content-desc","bounds","checked","enabled"]} for n in ns if n.get("text") or n.get("content-desc")]
 (client().out/(label+"-summary-"+uuid.uuid4().hex+".json")).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")
 print(json.dumps(data,ensure_ascii=False,indent=2))
def finite_number(text):
 if not isinstance(text, str) or not re.fullmatch(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?", text.strip()):
  return None
 try:
  value = float(text)
  return value if math.isfinite(value) else None
 except (ValueError, OverflowError):
  return None

def int64_matches(text, expected):
 if not isinstance(text, str) or len(text) > 21 or not re.fullmatch(r"[+-]?[0-9]+", text):
  return False
 return type(expected) is int and -(1 << 63) <= expected < (1 << 63) and int(text) == expected

def float_matches(text, expected):
 value = finite_number(text)
 return value is not None and type(expected) in (float, int) and math.isfinite(expected) and math.isclose(value, expected, rel_tol=1e-6, abs_tol=1e-9)

def parse_qa(raw):
 """Decode metadata, NOT a healthy-intake/soak/rendering acceptance oracle."""
 def unique(pairs):
  result = {}
  for key, value in pairs:
   if key in result: raise ValueError("Duplicate JSON key")
   result[key] = value
  return result
 try:
  if len(raw.encode("utf-8")) > 65536: raise ValueError("Oversized Activity output")
  begin, end = "ROS_POCKET_QA_V1_BEGIN", "ROS_POCKET_QA_V1_END"
  if raw.count(begin) != 1 or raw.count(end) != 1: raise ValueError("Missing or duplicate QA frame")
  lines = [line.strip() for line in raw.splitlines()]
  first, last = lines.index(begin), lines.index(end)
  if last <= first: raise ValueError("Truncated QA frame")
  payload = "\n".join(lines[first + 1:last])
  if len(payload.encode("utf-8")) > 24576: raise ValueError("Oversized QA JSON")
  data = json.loads(payload, object_pairs_hook=unique)
  stack = [(data, 0)]
  while stack:
   value, depth = stack.pop()
   if depth > 12: raise ValueError("Excessive QA nesting")
   if isinstance(value, (dict, list)):
    stack.extend((child, depth + 1) for child in (value.values() if isinstance(value, dict) else value))
   elif type(value) is float and not math.isfinite(value): raise ValueError("Nonfinite QA number")
   elif type(value) is int and not -(1 << 63) <= value < (1 << 63): raise ValueError("QA integer outside signed long")
  if type(data) is not dict or type(data.get("schemaVersion")) is not int or data["schemaVersion"] != 1:
   raise ValueError("Unknown QA schema")
  if data.get("applicationId") != PKG or data.get("debuggable") is not True:
   raise ValueError("Wrong QA application identity")
  if type(data.get("pid")) is not int or data["pid"] <= 0 or type(data.get("valid")) is not bool:
   raise ValueError("Missing QA process/state")
  watches = data.get("watches")
  if type(watches) is not list or len(watches) not in (0, 4) or (data["valid"] and len(watches) != 4):
   raise ValueError("Invalid QA slot projection")
  for slot, watch in enumerate(watches):
   if type(watch) is not dict or type(watch.get("slot")) is not int or watch["slot"] != slot or "count" not in watch:
    raise ValueError("Wrong QA slot order or missing count")
   count = watch["count"]
   if count is not None and (type(count) is not int or count < 0): raise ValueError("Invalid cumulative count")
  return data
 except (ValueError, TypeError, RecursionError, UnicodeError) as exc:
  raise RuntimeError("Invalid QA frame: " + str(exc)) from exc


class Client:
    """Device-scoped transport; injected host transports are explicit test doubles."""
    def __init__(self, serial, *, out=OUT, runner=None):
        if not isinstance(serial, str) or not re.fullmatch(r"[A-Za-z0-9._:\-]{1,160}", serial):
            raise RuntimeError("Explicit valid POCKET_TABLET serial is required")
        self.serial, self.out = serial, Path(out) / uuid.uuid4().hex
        self.out.mkdir(parents=True, exist_ok=False)
        self.kind = "synthetic_transport_test" if runner is not None else "adb_observation"
        self.runner = runner or self._process
        self.verified = False

    @staticmethod
    def _process(args, timeout):
        try:
            result = subprocess.run(args, capture_output=True, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise RuntimeError("ADB transport failed: " + type(exc).__name__) from exc
        if result.returncode:
            if (result.returncode == 137 and len(args) == 7 and args[0] == ADB
                    and args[1] == "-s" and re.fullmatch(r"[A-Za-z0-9._:\-]{1,160}", args[2])
                    and args[3:6] == ["shell", "uiautomator", "dump"]
                    and re.fullmatch(r"/data/local/tmp/ros-pocket-[0-9a-f]{32}/hierarchy\.xml", args[6])
                    and result.stdout == ("UI hierchary dumped to: " + args[6] + "\n").encode("utf-8")
                    and result.stderr == b""):
                raise _PostwriteDump137(args[6])
            raise RuntimeError("ADB command failed (exit " + str(result.returncode) + ")")
        return result.stdout.decode("utf-8", errors="strict")

    def _raw(self, *args, timeout=25):
        output = self.runner([ADB, "-s", self.serial, *args], timeout)
        if not isinstance(output, str) or len(output.encode("utf-8")) > 2 * 1024 * 1024:
            raise RuntimeError("Invalid or oversized ADB output")
        return output

    def verify(self):
        if self.verified:
            return
        if self._raw("get-state").strip() != "device":
            raise RuntimeError("Authorized ADB device is not online")
        model = self._raw("shell", "getprop", "ro.product.model").strip()
        if not model or len(model) > 200:
            raise RuntimeError("Device model unavailable")
        if self._raw("shell", "am", "get-current-user").strip() != "0":
            raise RuntimeError("Only verified Android user 0 is in scope")
        packages = self._raw("shell", "pm", "list", "packages", "--user", "0", PKG).splitlines()
        if "package:" + PKG not in packages:
            raise RuntimeError("Exact DEV package is not installed for user 0")
        self.verified = True

    def adb(self, *args, timeout=25):
        self.verify()
        if args[:2] == ("shell", "am") and len(args) > 2 and args[2] in ("start", "force-stop"):
            if args.count("--user") != 1 or args.index("--user") + 1 >= len(args) or args[args.index("--user") + 1] != "0":
                raise RuntimeError("Lifecycle commands require explicit user 0")
            if self._raw("shell", "am", "get-current-user").strip() != "0":
                raise RuntimeError("Lifecycle stopped: current Android user is no longer user 0")
        return self._raw(*args, timeout=timeout)

    def focus(self, *, allow_popup=False):
        window = self.adb("shell", "dumpsys", "window")
        match = re.search(r"mCurrentFocus=Window\{([^ }\r\n]+) u(\d+) ([^ }\r\n]+)\}", window)
        if match and match[2] == "0":
            if match[3].startswith(PKG + "/"):
                return "activity"
            if allow_popup and match[3].startswith("PopupWindow:"):
                packages = self.adb("shell", "pm", "list", "packages", "-U", "--user", "0", PKG)
                uids = re.findall(r"(?m)^package:" + re.escape(PKG) + r" uid:(\d+)\s*$", packages)
                token = re.escape(match[1] + " u0 " + match[3])
                block = re.search(r"(?ms)^\s*Window #\d+ Window\{" + token + r"\}:\s*\n(.*?)(?=\n\s*Window #\d+ |\Z)", window)
                owner = re.search(r"\bmOwnerUid=(\d+)\b", block[1]) if block else None
                if len(uids) == 1 and owner and owner[1] == uids[0]:
                    return "popup"
        raise RuntimeError("Target Activity or explicitly owned popup is not focused; UI automation stopped")

    def ime_shown(self):
        match = re.search(r"\bmInputShown=(true|false)\b", self.adb("shell", "dumpsys", "input_method"))
        if not match:
            raise RuntimeError("IME visibility is unavailable")
        return match[1] == "true"

    def hide_ime(self):
        self.focus()
        if not self.ime_shown():
            return
        self.adb("shell", "input", "keyevent", "4")
        deadline = time.monotonic() + 4
        while self.ime_shown():
            self.focus()
            if time.monotonic() >= deadline:
                raise RuntimeError("IME did not hide after one Back; no further navigation attempted")
            time.sleep(.15)
        self.focus()

    def tap(self, ident, *, popup=False):
        if popup:
            self.focus(allow_popup=True)
            if self.ime_shown():
                raise RuntimeError("IME overlaps popup; refusing blind Back")
        else:
            self.hide_ime()
        node = node_by(self.dump("tap", allow_popup=popup), ident)
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not match:
            raise RuntimeError("Invalid target bounds")
        left, top, right, bottom = map(int, match.groups())
        if right <= left or bottom <= top or node.get("enabled") == "false" or node.get("visible-to-user") == "false":
            raise RuntimeError("Target is not actionable")
        self.focus(allow_popup=popup)
        self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        if popup:
            for _ in range(20):
                if self.focus(allow_popup=True) == "activity":
                    return
                time.sleep(.1)
            raise RuntimeError("Owned popup did not dismiss; normal Activity guard remains required")

    def field(self, ident, value):
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_./:+@ \-]{0,128}", value):
            raise RuntimeError("Field input must be bounded safe ASCII, not remote shell syntax")
        node = node_by(self.dump("field-target"), ident)
        if node.get("class") != "android.widget.EditText":
            raise RuntimeError("Field target is not an app EditText")
        self.tap(ident)
        self.focus()
        self.adb("shell", "input", "keycombination", "113", "29")
        self.focus()
        if value:
            self.adb("shell", "input", "text", value.replace(" ", "%s"))
        else:
            self.adb("shell", "input", "keyevent", "67")
        observed = node_by(self.dump("field-readback"), ident)
        text = observed.get("text", "")
        if text != value and not (not value and ident in EMPTY_FIELD_HINTS and text == EMPTY_FIELD_HINTS[ident]):
            raise RuntimeError("Field readback differs from requested value")

    def find(self, ident, *, direction="down", max_swipes=24):
        if type(max_swipes) is not int or not 0 <= max_swipes <= 64 or direction not in ("up", "down"):
            raise RuntimeError("Invalid bounded target-search policy")
        self.hide_ime()
        for attempt in range(max_swipes + 1):
            nodes = self.dump("find")
            try:
                node_by(nodes, ident)
                return nodes
            except MissingTarget:
                if attempt < max_swipes:
                    self.scroll(direction)
        raise MissingTarget("Visible target not found within scroll budget: " + ident)

    def scroll(self, direction):
        if direction not in ("down", "up"):
            raise RuntimeError("Scroll direction must be down or up")
        self.hide_ime()
        candidates = []
        for node in self.dump("scroll-bounds"):
            if node.get("class") != "android.widget.ScrollView":
                continue
            match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if match:
                left, top, right, bottom = map(int, match.groups())
                if right - left >= 100 and bottom - top >= 120:
                    candidates.append((left, top, right, bottom))
        if not candidates:
            raise RuntimeError("No current target ScrollView viewport")
        left, top, right, bottom = max(candidates, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
        x = (left + right) // 2
        low, high = top + int((bottom - top) * .70), top + int((bottom - top) * .38)
        start, finish = (low, high) if direction == "down" else (high, low)
        self.focus()
        self.adb("shell", "input", "swipe", str(x), str(start), str(x), str(finish), "650")

    def qa(self):
        attempt = "qa-" + uuid.uuid4().hex
        record = {"kind": self.kind, "observation": "qa_decode_only", "status": "failed"}
        try:
            self.focus()
            raw = self.adb("shell", "dumpsys", "activity", PKG + "/com.lamggm.rospocket.MainActivity", "--pocket-qa-v1", timeout=5)
            data = parse_qa(raw)
            self.focus()
            if self.adb("shell", "pidof", PKG, timeout=5).strip() != str(data["pid"]):
                raise RuntimeError("QA process changed during export")
            record.update(status="passed", qa=data)
            return data
        except RuntimeError as exc:
            record["error"] = str(exc)
            raise
        finally:
            (self.out / (attempt + ".json")).write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")

    def dump(self, label="current", *, allow_popup=False):
        if not re.fullmatch(r"[A-Za-z0-9_.\-]{1,96}", label):
            raise RuntimeError("Invalid evidence label")
        recovery = None
        for index in range(2):
            record = {}
            try:
                return self._capture(label, allow_popup=allow_popup, record=record, recovery=recovery)
            except _PostwriteDump137:
                if index or record.get("failureClassification") != "postwrite_uiautomator_137" or record.get("cleanupFailed"):
                    raise
                recovery = record

    def _capture(self, label, *, allow_popup, record, recovery):
        attempt = label + "-" + uuid.uuid4().hex
        remote_dir = "/data/local/tmp/ros-pocket-" + uuid.uuid4().hex
        remote = remote_dir + "/hierarchy.xml"
        record.update(kind=self.kind, status="failed", label=label, attempt=attempt, remoteDirectory=remote_dir)
        if recovery is not None:
            record["recoveryOf"] = recovery["attempt"]
        remote_created = False
        try:
            if recovery is not None and remote_dir == recovery["remoteDirectory"]:
                raise RuntimeError("Fresh private capture directory repeated")
            self.focus(allow_popup=allow_popup)
            self.adb("shell", "mkdir", "-m", "700", remote_dir)
            remote_created = True
            try:
                result = self.adb("shell", "uiautomator", "dump", remote)
            except _PostwriteDump137 as exc:
                if exc.remote == remote:
                    record.update(failureClassification="postwrite_uiautomator_137", exitCode=137)
                raise
            if result != "UI hierchary dumped to: " + remote + "\n":
                raise RuntimeError("Fresh UI hierarchy was not confirmed")
            raw = self.adb("shell", "cat", remote)
            self.focus(allow_popup=allow_popup)
            root = ET.fromstring(raw)
            elements = list(root.iter("node"))
            if any(n.get("package") != PKG for n in elements):
                raise RuntimeError("Hierarchy contains nodes outside the target package")
            nodes = [dict(n.attrib) for n in elements]
            if not nodes:
                raise RuntimeError("Fresh hierarchy contains no target-package nodes")
            scoped = ET.Element("hierarchy")
            for node in nodes:
                ET.SubElement(scoped, "node", node)
            ET.ElementTree(scoped).write(self.out / (attempt + ".xml"), encoding="utf-8", xml_declaration=True)
            record["status"] = "passed"
            return nodes
        except (RuntimeError, ET.ParseError) as exc:
            record["error"] = str(exc)
            if isinstance(exc, RuntimeError):
                raise
            raise RuntimeError(str(exc)) from exc
        finally:
            cleanup_error = None
            if remote_created:
                try:
                    self.adb("shell", "rm", "-f", remote)
                    self.adb("shell", "rmdir", remote_dir)
                except RuntimeError as exc:
                    cleanup_error = exc
                    record["cleanupFailed"] = True
                    record["status"] = "failed"
            (self.out / (attempt + ".json")).write_text(json.dumps(record, indent=2), encoding="utf-8")
            if cleanup_error is not None and sys.exc_info()[0] is None:
                raise RuntimeError("Private dump cleanup failed") from cleanup_error


_default = None
def client():
 global _default
 if _default is None:
  _default = Client(SERIAL, out=OUT)
 return _default

if __name__=="__main__":
 action=sys.argv[1]
 if action=="domain":field("domain",sys.argv[2])
 elif action=="tap":tap(sys.argv[2])
 elif action=="scroll":scroll(sys.argv[2])
 elif action=="summary":summary(sys.argv[2] if len(sys.argv)>2 else "current")
 else:raise SystemExit("unknown action")
