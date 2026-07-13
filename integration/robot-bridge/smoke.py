#!/usr/bin/env python3
"""Local, no-network smoke test for the outbound robot protocol."""
import json
import os
import tempfile
from pathlib import Path

from fastapi.testclient import TestClient


def main() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        os.environ.update({
            "ROBOT_BRIDGE_STORAGE_PATH": str(Path(temporary) / "bridge.db"),
            "ROBOT_AUTH_TOKENS_JSON": json.dumps({"robot-001": "token-placeholder"}),
            "BRIDGE_API_TOKEN": "admin-placeholder",
        })
        from app.main import create_app

        client = TestClient(create_app())
        assert client.post("/robot-api/v1/heartbeat", json={}).status_code == 401
        headers = {"Authorization": "Bearer token-placeholder"}
        heartbeat = {
            "protocolVersion": "1.0", "robotId": "robot-001", "bootId": "boot-1",
            "softwareVersion": "smoke", "state": "idle", "latestLocalEventSequence": 0,
            "health": {},
        }
        reply = client.post("/robot-api/v1/heartbeat", headers=headers, json=heartbeat)
        assert reply.status_code == 200 and reply.json()["command"] is None

        app = client.app
        app.state.store.cache_deployment("deploy-1", "robot-001", {
            "schemaVersion": "1.0", "robotId": "robot-001", "routeRevisionId": "route-r1",
            "routeRevisionContentSha256": "a" * 64, "routePayloadSha256": "b" * 64,
            "mapImageSha256": "c" * 64, "yamlName": "site.yaml", "pgmName": "site.pgm",
        }, b'{"version":2}', b"image: site.pgm\n", b"P5\n1 1\n255\n\0")
        body = {"robotId": "robot-001", "deploymentId": "deploy-1", "executorRouteId": "route-1", "requestId": "request-1"}
        queued = client.post("/bridge/v1/executions/execution-1/start", headers={"Authorization": "Bearer admin-placeholder"}, json=body)
        assert queued.status_code == 202
        duplicate = client.post("/bridge/v1/executions/execution-1/start", headers={"Authorization": "Bearer admin-placeholder"}, json=body)
        assert duplicate.status_code == 202 and duplicate.json()["commandId"] == queued.json()["commandId"]
        reply = client.post("/robot-api/v1/heartbeat", headers=headers, json=heartbeat).json()
        command = reply["command"]
        assert command and command["commandId"] == queued.json()["commandId"]
        app.state.store.expire_leases_for_smoke()
        command = client.post("/robot-api/v1/heartbeat", headers=headers, json=heartbeat).json()["command"]
        assert command["commandId"] == queued.json()["commandId"]
        ack = client.post(f"/robot-api/v1/commands/{command['commandId']}/ack", headers=headers, json={"robotId": "robot-001", "leaseToken": command["leaseToken"], "status": "RECEIVED", "executionId": "execution-1"})
        assert ack.status_code == 200
        assert client.post("/robot-api/v1/heartbeat", headers=headers, json=heartbeat).json()["command"] is None
        assert client.get("/robot-api/v1/deployments/deploy-1/manifest", headers=headers).json()["yamlName"] == "site.yaml"
        events = [{"robot_id": "robot-001", "boot_id": "boot-1", "sequence": 2, "event": "route_started", "execution_id": "execution-1", "deployment_id": "deploy-1", "request_id": "request-1", "command_id": command["commandId"], "occurred_at": "2026-01-01T00:00:00Z"}]
        assert client.post("/robot-api/v1/events/batch", headers=headers, json={"robotId": "robot-001", "events": events}).json()["acceptedThroughSequence"] == 0
        events.insert(0, {**events[0], "sequence": 1, "event": "route_paused"})
        assert client.post("/robot-api/v1/events/batch", headers=headers, json={"robotId": "robot-001", "events": events}).json()["acceptedThroughSequence"] == 2
        assert client.post("/robot-api/v1/events/batch", headers=headers, json={"robotId": "robot-001", "events": events}).json()["acceptedThroughSequence"] == 2
    print("bridge smoke: ok")


if __name__ == "__main__":
    main()
