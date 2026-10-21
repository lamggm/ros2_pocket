"""Real typed Jazzy publisher -> separate native process in private container."""
import json
import subprocess
import sys

binary, kind = sys.argv[1:]
long_frame = kind == "long_frame"
types = {"lifecycle": "geometry_msgs::msg::dds_::Vector3_", "special": "geometry_msgs::msg::dds_::Vector3_", "primitive_long": "std_msgs::msg::dds_::String_", "primitive_string": "std_msgs::msg::dds_::String_", "primitive_bool": "std_msgs::msg::dds_::Bool_", "primitive_int32": "std_msgs::msg::dds_::Int32_", "primitive_int64": "std_msgs::msg::dds_::Int64_", "primitive_float32": "std_msgs::msg::dds_::Float32_", "primitive_float64": "std_msgs::msg::dds_::Float64_", "vector3": "geometry_msgs::msg::dds_::Vector3_", "quaternion": "geometry_msgs::msg::dds_::Quaternion_",
         "long_frame": "nav_msgs::msg::dds_::Odometry_", "odometry": "nav_msgs::msg::dds_::Odometry_", "pose": "geometry_msgs::msg::dds_::Pose_", "twist": "geometry_msgs::msg::dds_::Twist_"}
result = subprocess.run([binary, kind, types[kind]], capture_output=True, text=True, timeout=18)
assert result.returncode == 0, (result.stdout, result.stderr)
def invalid(value):
    raise AssertionError("invalid JSON constant: " + value)
snapshots = [json.loads(line, parse_constant=invalid) for line in result.stdout.splitlines()]
received = [s for s in snapshots if s.get("count", 0) > 0]
assert received, "no real typed sample received: " + result.stdout[-4000:]
sample = received[-1]
assert sample["message"]["schemaVersion"] == 1
root = sample["message"]["root"]
assert root["kind"] == "object"
def flatten(node):
    yield node
    for child in node.get("children", []):
        yield from flatten(child)
fields = {n["path"]: n for n in flatten(root)}
vector = {"x": 1.25, "y": -2.5, "z": 3.75}
expected = dict(vector)
if kind == "primitive_long":
    expected = {}
    assert fields["data"]["value"] == "a"*512 and fields["data"]["truncated"]
    assert sample["last"] == "a"*8191
elif kind.startswith("primitive_"):
    value = {"primitive_string": "ação_世界_🤖", "primitive_bool": True, "primitive_int32": -2147483647,
             "primitive_int64": -9223372036854775808, "primitive_float32": 0.125, "primitive_float64": 1.2345678901234567}[kind]
    expected = {} if kind in ("primitive_bool", "primitive_string") else {"data": value}
    assert fields["data"]["value"] == value
elif kind == "special":
    expected = {}
    for path, state, display in [("x", "nan", "NaN"), ("y", "positive_infinity", "+Inf"), ("z", "negative_infinity", "-Inf")]:
        assert fields[path]["value"] is None and fields[path]["numberState"] == state
        assert fields[path]["display"] == display
    assert sample["message"]["numericPaths"] == ["x", "y", "z"]
elif kind == "quaternion":
    expected["w"] = 0.125
elif kind == "twist":
    expected = {p+"."+f: v for p in ["linear", "angular"] for f,v in vector.items()}
elif kind == "pose":
    expected = {"position."+f: v for f,v in vector.items()}
    expected.update({"orientation."+f: v for f,v in dict(vector, w=0.125).items()})
elif kind in ("odometry", "long_frame"):
    expected = {"header.stamp.sec": 1700000123, "header.stamp.nanosec": fields["header.stamp.nanosec"]["value"]}
    assert 0 < expected["header.stamp.nanosec"] < 1000000000
    expected.update({"pose.pose.position."+f: v for f,v in vector.items()})
    expected.update({"pose.pose.orientation."+f: v for f,v in dict(vector, w=0.125).items()})
    expected.update({f"pose.covariance[{i}]": i/4.0 for i in range(36)})
    expected.update({"twist.twist."+p+"."+f: v for p in ["linear", "angular"] for f,v in vector.items()})
    expected.update({f"twist.covariance[{i}]": -i/8.0 for i in range(36)})
    if long_frame:
        assert fields["header.frame_id"]["value"] == "a"*511
        assert fields["header.frame_id"]["truncated"] is True
        assert fields["child_frame_id"]["value"] == "界"*170
        assert fields["child_frame_id"]["truncated"] is True
        assert sample["message"]["truncated"] is True
        assert len(sample["last"].encode()) <= 8192
        assert len(list(flatten(root))) <= 192
    else:
        assert fields["header.frame_id"]["value"] == "map_ação_世界_🤖"
    if not long_frame:
        assert fields["child_frame_id"]["value"] == "base_ç_🚀"
    for path in ["pose.covariance", "twist.covariance"]:
        assert fields[path]["kind"] == "array" and len(fields[path]["children"]) == 36
    assert received[-1]["message"]["root"]["children"][0]["children"][0] != received[0]["message"]["root"]["children"][0]["children"][0], "source stamp did not advance"
for path, value in expected.items():
    assert fields[path]["kind"] == "number", path
    assert fields[path]["value"] == value, (path, fields[path])
if kind != "special":
    assert sample["message"]["numericPaths"] == list(expected)
assert sample["message"]["type"] == types[kind].replace("::msg::dds_::", "/msg/")[:-1]
assert sample["readerActive"] and sample["matched"] > 0
assert snapshots[-1]["message"] is None and snapshots[-1]["last"] == "" and not snapshots[-1]["running"]
if kind == "lifecycle":
    failures = [s for s in snapshots if s.get("running") and s.get("error")]
    assert len(failures) == 3
    assert failures[0]["readerActive"] and failures[0]["message"]["type"] == "geometry_msgs/msg/Vector3"
    assert not failures[0]["fresh"] and failures[0]["subscriptionState"] == "error"
    for failure in failures[1:]:
        assert failure["message"] is None and not failure["readerActive"] and failure["last"] == ""
        assert failure["count"] == 0 and failure["matched"] == 0 and failure["age"] == -1
    assert failures[1]["error"] == failures[2]["error"]
    assert sample["error"] == "" and sample["fresh"]
    assert sample["readerGeneration"] > failures[0]["readerGeneration"]
# Keep real-value evidence below CTest's passing-output cap (no full raw snapshots).
message=sample["message"]
evidence={"type": message["type"], "numericValues": [fields[p]["value"] for p in message["numericPaths"]],
          "truncated": message["truncated"], "count": sample["count"]}
if "header.frame_id" in fields:
    evidence["frames"]=[fields[p]["value"][:8] for p in ["header.frame_id", "child_frame_id"]]
    evidence["frameBytes"]=[len(fields[p]["value"].encode()) for p in ["header.frame_id", "child_frame_id"]]
print(json.dumps(evidence, ensure_ascii=False, allow_nan=False, separators=(",", ":")))
assert received[-1]["count"] > received[0]["count"], "no advancing real samples"
print("typed structured fixture:", kind, "PASS")
