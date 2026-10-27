#!/usr/bin/env python3
"""Owned loopback DDS fixture; no robot, network service or physical device."""
import json
import subprocess
import sys


def check(value, message):
    if not value:
        raise RuntimeError(message)


def topic(snapshot):
    rows = [row for row in snapshot["topics"] if row["wireName"] == "rt/explorer_native/value"]
    check(len(rows) == 1, "fixture topic must appear exactly once")
    return rows[0]


def main():
    mode = sys.argv[2]
    run = subprocess.run([sys.argv[1], mode], capture_output=True, text=True, timeout=35)
    print(run.stdout, end="")
    print(run.stderr, end="", file=sys.stderr)
    check(run.returncode == 0, "native fixture exited unsuccessfully")
    lines = [json.loads(line) for line in run.stdout.splitlines() if line.startswith("{")]
    ids = lines[0]["ids"]
    stages = {line["stage"]: line["snapshot"] for line in lines[1:]}
    if mode == "qos":
        snapshot = stages["qos"]
        row = topic(snapshot)
        check((row["publishers"], row["subscribers"]) == (2, 1), "deduplicated external endpoint counts")
        check("endpoints" in row, "missing real builtin endpoint details")
        endpoints = {e["id"]: e for e in row["endpoints"]}
        check(set(endpoints) == {ids["fast"], ids["durable"], ids["reader"]}, "exact endpoint GUIDs")
        for name in ("fast", "durable", "reader"):
            e = endpoints[ids[name]]
            check(e["participant"] == ids["participant"], "actual DDS participant GUID")
            check(e["writer"] == (name != "reader"), "actual endpoint role")
            check(e["nodes"] == [], "DDS participant must not be inferred as a ROS node")
            q = e["qos"]
            check(set(q) == {"reliability", "durability", "history", "depth", "deadlineNs", "liveliness", "leaseDurationNs", "ownership", "destinationOrder"}, "additive complete QoS contract")
            check(type(q["deadlineNs"]) is int and type(q["leaseDurationNs"]) is int, "nanoseconds must be JSON integers")
            if name == "fast":
                expected = {"reliability": "best_effort", "durability": "volatile", "deadlineNs": 250000000, "liveliness": "manual_by_topic", "leaseDurationNs": 2000000000, "ownership": "exclusive", "destinationOrder": "by_source_timestamp"}
                history, depth = "keep_last", 7
            else:
                expected = {"reliability": "reliable", "durability": "transient_local", "deadlineNs": -1, "liveliness": "automatic", "leaseDurationNs": -1, "ownership": "shared", "destinationOrder": "by_reception_timestamp"}
                history, depth = "keep_all", 1
            check(all(q[k] == v for k, v in expected.items()), "announced QoS does not match real entity: " + str(q))
            # History is not an interoperable discovery policy. Never invent it
            # when this Cyclone builtin sample does not actually contain it.
            check((q["history"] == "unknown" and q["depth"] == -1) or (q["history"] == history and q["depth"] == depth), "history absent must be explicit unknown")
        check(snapshot["nodes"] == [], "unknown graph must retain empty legacy node list")
        print("PASS builtin GUIDs, roles, unknown ROS association, raw QoS and infinity integers")
    elif mode == "graph":
        snapshot = stages["graph"]
        endpoints = {e["id"]: e for e in topic(snapshot)["endpoints"]}
        check(endpoints[ids["fast"]]["nodes"] == ["/lab/alpha", "/lab/beta"], "writer GID must associate only matching writer role, deduplicated and sorted")
        check(endpoints[ids["reader"]]["nodes"] == ["/lab/beta"], "reader_gid_seq must identify actual reader")
        check(endpoints[ids["durable"]]["nodes"] == [], "participant sharing never implies node sharing")
        check(snapshot["nodes"] == ["/lab/alpha", "/lab/beta", "/wrong_role"], "legacy node strings retained independently of endpoint links")
        replacement = stages["replacement"]
        endpoints = {e["id"]: e for e in topic(replacement)["endpoints"]}
        check(endpoints[ids["fast"]]["nodes"] == [], "graph replacement must clear old endpoint associations")
        check(endpoints[ids["durable"]]["nodes"] == ["/lab/replacement"], "replacement graph GID association")
        check(endpoints[ids["reader"]]["nodes"] == ["/lab/replacement"], "replacement reader graph association")
        check(replacement["nodes"] == ["/lab/replacement"], "old graph nodes removed on update")
        print("PASS real graph payload reader/writer GIDs, role matching, unknowns and replacement")
    elif mode == "lifecycle":
        def endpoints(stage):
            return {e["id"]: e for e in topic(stages[stage])["endpoints"]}
        check(all(e["nodes"] == ["/lab/start"] for e in endpoints("before").values()), "precondition: graph associations")
        for stage in ("graph_disposed", "graph_writer_dead"):
            check(stages[stage]["nodes"] == [], "dead/disposed graph node names must clear: " + stage)
            check(all(e["nodes"] == [] for e in endpoints(stage).values()), "dead/disposed graph endpoint associations must clear: " + stage)
        check(all(e["nodes"] == ["/lab/renew"] for e in endpoints("renewed").values()), "disposed graph can publish a fresh incarnation")
        removed = endpoints("endpoint_removed")
        check(set(removed) == {ids["durable"], ids["reader"]}, "disposed endpoint gone without deleting other writer")
        check((topic(stages["endpoint_removed"])["publishers"], topic(stages["endpoint_removed"])["subscribers"]) == (1, 1), "accurate counts after endpoint removal")
        restarted = endpoints("endpoint_restarted")
        new_writer = [e for e in restarted.values() if e["writer"] and e["id"] != ids["durable"]]
        check(len(new_writer) == 1 and new_writer[0]["id"] != ids["fast"] and new_writer[0]["nodes"] == [], "restarted endpoint unknown until actual new GID arrives")
        check(all(e["nodes"] == ["/lab/restarted"] for e in endpoints("restart_graph").values()), "new GID graph binds restarted endpoint")
        check(all(e["nodes"] == ["/lab/back"] for e in endpoints("graph_writer_returned").values()), "new graph writer restores associations")
        check(stages["participant_dead"]["nodes"] == [] and stages["participant_dead"]["topics"] == [] and stages["participant_dead"]["ddsPeers"] == 0, "participant death clears graph, all endpoints and peer identity")
        restart = endpoints("participant_restarted")
        check(len(restart) == 1 and all(e["participant"] != ids["participant"] and e["nodes"] == [] for e in restart.values()), "new participant must not inherit associations")
        check(stages["participant_restarted"]["nodes"] == [], "new participant must not inherit ROS names")
        check(all(e["nodes"] == ["/lab/new_process"] for e in endpoints("participant_restart_graph").values()), "new participant actual GID graph")
        print("PASS graph disposal/death, endpoint removal/restart, multiple writers and participant death/restart")
    elif mode == "identity":
        def endpoints(stage):
            return {e["id"]: e for e in topic(stages[stage])["endpoints"]}
        check(endpoints("owner")[ids["fast"]]["nodes"] == ["/lab/owner"], "precondition: primary owner")
        check(endpoints("spoofed")[ids["fast"]]["nodes"] == ["/lab/owner"] and stages["spoofed"]["nodes"] == ["/lab/owner"], "graph payload participant must match actual graph writer participant")
        foreign = endpoints("foreign")
        check(foreign[ids["fast"]]["nodes"] == ["/lab/owner"] and foreign[ids["reader"]]["nodes"] == ["/lab/owner"], "foreign participant GIDs must not claim primary endpoints")
        other = [e for e in foreign.values() if e["participant"] != ids["participant"]]
        check(len(other) == 1 and other[0]["nodes"] == ["/lab/foreign"], "foreign participant can own its actual endpoint")
        check(stages["owner_disposed"]["nodes"] == ["/lab/foreign"], "unkeyed graph dispose must clear correct writer, not last shared instance")
        disposed = endpoints("owner_disposed")
        check(disposed[ids["fast"]]["nodes"] == [] and disposed[other[0]["id"]]["nodes"] == ["/lab/foreign"], "per-writer graph disposal association isolation")
        dead = endpoints("foreign_graph_dead")
        check(stages["foreign_graph_dead"]["nodes"] == ["/lab/owner_again"] and dead[ids["fast"]]["nodes"] == ["/lab/owner_again"], "graph writer death must not remove surviving participant graph")
        check(dead[other[0]["id"]]["nodes"] == [], "graph writer death while another writer lives must clear associations")
        check(set(endpoints("foreign_participant_dead")) == {ids["fast"], ids["durable"], ids["reader"]}, "only dead participant endpoints removed")
        check(stages["foreign_participant_dead"]["nodes"] == ["/lab/owner_again"], "remaining graph survives foreign participant death")
        print("PASS source participant verification, foreign GID rejection and multi-writer unkeyed graph lifecycle")
    elif mode == "compatibility":
        default = {e["id"]: e for e in topic(stages["prospective_default"])["endpoints"]}
        check(all("compatibility" in e for e in default.values()), "missing per-endpoint offered/requested compatibility")
        plain = [e["id"] for e in default.values() if e["writer"] and e["id"] not in (ids["fast"], ids["durable"])]
        check(len(plain) == 1, "one best-effort/volatile shared writer")
        for stage, snapshot in stages.items():
            endpoints = {e["id"]: e for e in topic(snapshot)["endpoints"]}
            mode = int(stage[-1]) if stage.startswith("reader_") and stage[-1].isdigit() else 0
            reliable, transient = bool(mode & 1), bool(mode & 2)
            for endpoint in endpoints.values():
                expected = []
                if endpoint["writer"]:
                    if reliable and endpoint["qos"]["reliability"] == "best_effort":
                        expected.append("Reliability: writer offers best_effort; reader requests reliable")
                    if transient and endpoint["qos"]["durability"] == "volatile":
                        expected.append("Durability: writer offers volatile; reader requests transient_local")
                    if endpoint["qos"]["ownership"] == "exclusive":
                        expected.append("Ownership: writer offers exclusive; reader requests shared")
                check(endpoint["compatibility"] == {"state": "incompatible" if expected else "unknown", "reasons": expected}, "stable conservative compatibility for " + stage + ": " + str(endpoint))
            if stage.startswith("reader_"):
                check(snapshot["matched"] == (1 if reliable or transient else 2), "real DDS requested/offered match count must agree with reliability/durability selection")
            check(topic(snapshot)["publishers"] == 3 and topic(snapshot)["subscribers"] == 1, "own selected reader excluded from discovery")
        print("PASS prospective/default, all reliability/durability selections, ownership mismatch, real matches and recovery")
    else:
        raise RuntimeError("unknown test mode")


if __name__ == "__main__":
    main()
