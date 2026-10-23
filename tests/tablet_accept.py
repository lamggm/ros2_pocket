"""Authorized-device Wi-Fi discovery check; not full v0.2 qualification."""
import ipaddress
import re
import json
import os
import uuid
import tablet_ui as ui


def run():
    client = ui.client()
    result = {"kind": client.kind, "scope": "wifi_discovery_only", "passed": False}
    try:
        expected = str(ipaddress.IPv4Address(os.environ["POCKET_EXPECT_WIFI_IPV4"]))
        client.find("diagnostics", direction="up")
        nodes = client.find("Tópico /pocket_test/chatter", direction="down")
        ui.node_by(nodes, "Tópico /pocket_test/chatter")
        status = ui.node_by(nodes, "status")["text"]
        if status.split("·", 1)[0].strip() != "CONECTADO":
            raise RuntimeError("Inspector is not connected in the discovery observation")
        addresses = re.findall(r"\bWi-Fi ([^\s]+)", status)
        if len(addresses) != 1 or ipaddress.IPv4Address(addresses[0]) != ipaddress.IPv4Address(expected):
            raise RuntimeError("Inspector did not report the explicitly expected tablet LAN address")
        result["passed"] = True
        return result
    except (RuntimeError, KeyError, ValueError) as exc:
        result["error"] = str(exc)
        raise
    finally:
        (client.out / ("accept-result-" + uuid.uuid4().hex + ".json")).write_text(json.dumps(result, indent=2), encoding="utf-8")


if __name__ == "__main__":
    print(json.dumps(run()))
