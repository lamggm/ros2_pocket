#!/usr/bin/env python3
"""Separate-process domain-70 loopback test, including actual RTPS lease expiry."""
import json
import selectors
import subprocess
import sys
import time


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def receive(process):
    with selectors.DefaultSelector() as selector:
        selector.register(process.stdout, selectors.EVENT_READ)
        check(bool(selector.select(timeout=8)), "timed out reading child JSON")
    line = process.stdout.readline()
    check(bool(line), "fixture closed stdout unexpectedly")
    return json.loads(line)


def command(process, text):
    process.stdin.write(text + "\n")
    process.stdin.flush()
    return receive(process)


def poll_until(inspector, predicate, label, seconds=9):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        snapshot = command(inspector, "poll")
        if predicate(snapshot):
            print(json.dumps({"stage": label, "snapshot": snapshot}, ensure_ascii=False))
            return snapshot
        time.sleep(0.04)
    raise RuntimeError("timed out: " + label + ": " + json.dumps(snapshot))


def endpoint(snapshot, identifier):
    for row in snapshot["topics"]:
        for e in row["endpoints"]:
            if e["id"] == identifier:
                return e
    return None


def main():
    children = []
    try:
        def spawn(mode):
            child = subprocess.Popen([sys.argv[1], mode], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
            children.append(child)
            return child
        inspector = spawn("inspector")
        check(receive(inspector)["ok"], "inspector starts")
        producer = spawn("producer")
        ids = receive(producer)
        print(json.dumps({"ids": ids}))
        first = poll_until(inspector, lambda s: (endpoint(s, ids["writer"]) or {}).get("nodes") == ["/wire/producer"], "cross_process_discovery_graph")
        e = endpoint(first, ids["writer"])
        check(e["participant"] == ids["participant"] and e["writer"], "actual wire endpoint/participant identity")
        q = e["qos"]
        expected = {"reliability": "best_effort", "durability": "volatile", "deadlineNs": 750000000, "liveliness": "manual_by_participant", "leaseDurationNs": 3000000000, "ownership": "shared", "destinationOrder": "by_reception_timestamp"}
        check(all(q[k] == v for k, v in expected.items()), "real serialized announced QoS mismatch: " + str(q))
        check((q["history"], q["depth"]) in (("unknown", -1), ("keep_last", 7)), "unadvertised wire history must not be invented")
        check(e["compatibility"] == {"state": "unknown", "reasons": []}, "no mismatch must not be called fully compatible")
        command(inspector, "reliable")
        mismatch = poll_until(inspector, lambda s: (endpoint(s, ids["writer"]) or {}).get("compatibility", {}).get("state") == "incompatible", "wire_reliability_mismatch")
        check(mismatch["matched"] == 0, "real reliable reader must reject best-effort writer")
        check(endpoint(mismatch, ids["writer"])["compatibility"]["reasons"] == ["Reliability: writer offers best_effort; reader requests reliable"], "wire mismatch stable reason")
        command(inspector, "best_effort")
        poll_until(inspector, lambda s: s["matched"] == 1, "wire_reader_recovery")
        check(command(producer, "write")["ok"], "wire producer writes a synthetic sample")
        live = poll_until(inspector, lambda s: s["count"] > 0, "wire_real_utf8_sample")
        check(live["last"] == "wire sample UTF-8: α", "CDR/UTF-8 sample received exactly")
        command(inspector, "invalid")
        for _ in range(3):
            failed = command(inspector, "poll")
            check(failed["subscription"] == "" and failed["last"] == "" and failed["count"] == 0 and failed["matched"] == 0 and failed["age"] == -1 and bool(failed["error"]), "real topic creation failure must clear telemetry and retain its error")
        print(json.dumps({"stage": "wire_real_creation_failure", "snapshot": failed}, ensure_ascii=False))
        command(inspector, "best_effort")
        poll_until(inspector, lambda s: s["matched"] == 1 and not s["error"], "wire_failure_recovery")
        # SIGKILL prevents graceful DDS disposal; only RTPS lease expiry can clean up.
        producer.kill()
        producer.wait(timeout=5)
        poll_until(inspector, lambda s: not s["topics"] and not s["nodes"] and s["ddsPeers"] == 0 and s["matched"] == 0, "wire_abrupt_participant_lease_expiry")
        restarted = spawn("producer")
        new_ids = receive(restarted)
        check(new_ids["participant"] != ids["participant"] and new_ids["writer"] != ids["writer"], "new process must have new GUIDs")
        fresh = poll_until(inspector, lambda s: (endpoint(s, new_ids["writer"]) or {}).get("nodes") == ["/wire/producer"], "wire_process_restart_graph")
        check(endpoint(fresh, ids["writer"]) is None, "old endpoint must not survive process restart")
        command(restarted, "write")
        poll_until(inspector, lambda s: s["count"] > 0, "wire_fresh_restart_sample")
        restarted.stdin.write("exit\n")
        restarted.stdin.flush()
        check(restarted.wait(timeout=5) == 0, "producer orderly shutdown")
        poll_until(inspector, lambda s: not s["topics"] and not s["nodes"] and s["ddsPeers"] == 0, "wire_graceful_participant_removal")
        inspector.stdin.write("exit\n")
        inspector.stdin.flush()
        check(inspector.wait(timeout=5) == 0, "inspector orderly/idempotent stop")
        print("PASS separate-process RTPS/CDR QoS/graph, real mismatch/UTF-8, failure recovery, abrupt lease expiry and restart")
    finally:
        for child in children:
            if child.poll() is None:
                child.kill()
                child.wait(timeout=5)
            errors = child.stderr.read()
            if errors:
                print(errors, end="", file=sys.stderr)


if __name__ == "__main__":
    main()
