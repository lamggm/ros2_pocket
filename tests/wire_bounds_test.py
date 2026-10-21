"""Admission/recovery over RTPS; run only in the network-none fixture gate."""
import json
import os
import selectors
import subprocess
import sys
import tempfile
import time

import rclpy
from nav_msgs.msg import Odometry
from rcl_interfaces.msg import Log
from rclpy.node import Node
from rclpy.qos import QoSProfile, ReliabilityPolicy
from rclpy.serialization import serialize_message
from std_msgs.msg import String

LIMIT = 64 * 1024
rclpy.init()
node = Node("pocket_wire_bounds", enable_rosout=False)
qos = QoSProfile(depth=10, reliability=ReliabilityPolicy.RELIABLE)
publisher = node.create_publisher(Odometry, "/pocket_test/wire_bounds", qos)
string_pub = node.create_publisher(String, "/pocket_test/wire_bounds_string", qos)
log_pub = node.create_publisher(Log, "/rosout", qos)

with tempfile.TemporaryFile(mode="w+t") as errors:
    child = subprocess.Popen([sys.argv[1]], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=errors, bufsize=0)
    selector = selectors.DefaultSelector()
    selector.register(child.stdout, selectors.EVENT_READ)

    pending = bytearray()

    def read():
        while b"\n" not in pending:
            assert selector.select(4), "native Session response timed out"
            data = os.read(child.stdout.fileno(), 65536)
            assert data, "native Session exited early"
            pending.extend(data)
        line, _, rest = pending.partition(b"\n")
        pending[:] = rest
        return json.loads(line)

    def command(value):
        child.stdin.write((value + "\n").encode())
        child.stdin.flush()
        return read()

    def pump(seconds, publish=None):
        until = time.monotonic() + seconds
        snapshots = []
        while time.monotonic() < until:
            if publish is not None:
                publish()
            rclpy.spin_once(node, timeout_sec=0.02)
            snapshots.append(command("poll"))
            time.sleep(0.03)
        return snapshots

    def fields(snapshot):
        def visit(node):
            yield node
            for item in node.get("children", []):
                yield from visit(item)
        return {n["path"]: n for n in visit(snapshot["message"]["root"])}

    evidence = []
    try:
        assert read()["ok"]
        assert read()["ok"]
        deadline = time.monotonic() + 8
        while publisher.get_subscription_count() == 0:
            assert time.monotonic() < deadline, "Odometry reader never matched"
            pump(0.1)
        normal = Odometry()
        normal.header.frame_id = "wire_normal_ação_🤖"
        normal.child_frame_id = "base"
        normal.twist.twist.linear.x = 12.5
        # Writer-side matching can precede the native reader's usable path.
        # Require real intake, not a fixed sleep after graph discovery.
        deadline = time.monotonic() + 8
        baseline = command("poll")
        while baseline["count"] == 0:
            assert time.monotonic() < deadline, "matched Odometry produced no native intake"
            baseline = pump(0.1, lambda: publisher.publish(normal))[-1]
        assert baseline["count"] > 0 and baseline["readerActive"]
        assert fields(baseline)["twist.twist.linear.x"]["value"] == 12.5
        generation = baseline["readerGeneration"]

        # Above the retained-tree limit, below the wire ceiling: still admitted.
        near = Odometry()
        near.header.frame_id = "b" * (60 * 1024)
        assert 512 < len(serialize_message(near)) < LIMIT
        admitted = pump(0.6, lambda: publisher.publish(near))[-1]
        assert admitted["count"] > baseline["count"], "valid sub-ceiling sample rejected"
        assert fields(admitted)["header.frame_id"]["value"] == "b" * 512
        assert fields(admitted)["header.frame_id"]["truncated"]
        pump(0.3)  # Drain the preceding reliable positive control before counting.

        for field in ("header.frame_id", "child_frame_id"):
            huge = Odometry()
            if field == "header.frame_id":
                huge.header.frame_id = "x" * (128 * 1024)
            else:
                huge.child_frame_id = "y" * (128 * 1024)
            wire_bytes = len(serialize_message(huge))
            assert wire_bytes > LIMIT
            before = command("poll")
            observed = pump(1.0, lambda: publisher.publish(huge)) + pump(0.4)
            assert publisher.get_subscription_count() > 0, "writer disconnected instead of exercising admission"
            assert all(s["count"] == before["count"] for s in observed), (
                f"oversized {field} ({wire_bytes} bytes) admitted before presentation clipping: "
                f"count {before['count']} -> {observed[-1]['count']}")
            assert all(s["readerActive"] and s["readerGeneration"] == generation for s in observed)
            recovered = pump(0.6, lambda: publisher.publish(normal))[-1]
            assert recovered["count"] > before["count"] and recovered["fresh"]
            assert recovered["readerGeneration"] == generation, "recovery replaced the reader"
            assert fields(recovered)["header.frame_id"]["value"] == normal.header.frame_id
            assert fields(recovered)["twist.twist.linear.x"]["value"] == 12.5
            evidence.append({"field": field, "wireBytes": wire_bytes,
                             "rejectedCount": before["count"], "recoveryCount": recovered["count"]})
            pump(0.3)

        # Regular String and the independent dedicated Log reader still work
        # after both rejected samples, on this same Session/domain.
        assert command("string")["ok"]
        deadline = time.monotonic() + 8
        while string_pub.get_subscription_count() == 0 or log_pub.get_subscription_count() == 0:
            assert time.monotonic() < deadline, "String/Log readers never matched"
            pump(0.1)
        log = Log()
        log.level = 20
        log.name = "pocket_wire_bounds"
        log.msg = "WIRE_BOUNDS_LOG_RECOVERY_ação_🤖"
        def publish_regular():
            string_pub.publish(String(data="WIRE_BOUNDS_STRING_RECOVERY_ação_🤖"))
            log_pub.publish(log)
        final = pump(0.8, publish_regular)[-1]
        assert final["last"] == "WIRE_BOUNDS_STRING_RECOVERY_ação_🤖" and final["fresh"]
        assert final["rosout"]["active"] and any(
            entry["message"] == log.msg for entry in final["rosout"]["entries"])
        errors.flush()
        errors.seek(0)
        diagnostics = errors.read()
        assert "dropping oversize" in diagnostics, "no middleware rejection diagnostic"
        print(json.dumps({"wireLimitBytes": LIMIT, "underLimitBytes": len(serialize_message(near)),
                          "oversized": evidence, "stringAndRosoutRecovery": True}))
        print("PASS bounded DDS wire samples rejected before intake, same-reader recovery")
    finally:
        child.stdin.close()
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()
        selector.close()
        node.destroy_node()
        rclpy.shutdown()
    assert child.returncode == 0, child.returncode
