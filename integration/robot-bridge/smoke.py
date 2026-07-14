#!/usr/bin/env python3
"""Local, no-network smoke test for the outbound robot protocol."""
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

from app.store import Store


def request(base_url, method, path, body=None, token=""):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"} if data is not None else {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    item = urllib.request.Request(base_url + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(item, timeout=3) as response:
            content = response.read()
            return response.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as exc:
        content = exc.read()
        return exc.code, json.loads(content) if content else {}


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_ready(process, base_url):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"uvicorn exited with {process.returncode}")
        try:
            status, _ = request(base_url, "GET", "/bridge/v1/health", token="admin-placeholder")
            if status == 200:
                return
        except OSError:
            pass
        time.sleep(0.05)
    raise RuntimeError("uvicorn did not become ready")


def main() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        db_path = str(Path(temporary) / "bridge.db")
        environment = {
            **os.environ,
            "ROBOT_BRIDGE_STORAGE_PATH": db_path,
            "ROBOT_AUTH_TOKENS_JSON": json.dumps({"robot-001": "token-placeholder"}),
            "BRIDGE_API_TOKEN": "admin-placeholder",
        }
        store = Store(db_path)
        store.cache_deployment("deploy-1", "robot-001", {
            "schemaVersion": "1.0", "robotId": "robot-001", "routeRevisionId": "route-r1",
            "routeRevisionContentSha256": "a" * 64, "routePayloadSha256": "b" * 64,
            "mapImageSha256": "c" * 64, "yamlName": "site.yaml", "pgmName": "site.pgm",
        }, b'{"version":2}', b"image: site.pgm\n", b"P5\n1 1\n255\n\0")
        port = free_port()
        base_url = f"http://127.0.0.1:{port}"
        process = subprocess.Popen(
            [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", str(port), "--log-level", "error"],
            cwd=Path(__file__).parent,
            env=environment,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            wait_ready(process, base_url)
            run_smoke(base_url, store)
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
    print("bridge smoke: ok")


def run_smoke(base_url, store) -> None:
    assert request(base_url, "POST", "/robot-api/v1/heartbeat", {})[0] == 401
    heartbeat = {
        "protocolVersion": "1.0", "robotId": "robot-001", "bootId": "boot-1",
        "softwareVersion": "smoke", "state": "idle", "latestLocalEventSequence": 0, "health": {},
    }
    status, reply = request(base_url, "POST", "/robot-api/v1/heartbeat", heartbeat, "token-placeholder")
    assert status == 200 and reply["command"] is None
    assert request(base_url, "POST", "/robot-api/v1/heartbeat", {**heartbeat, "robotId": "robot-unknown"}, "token-placeholder")[0] == 401
    assert request(base_url, "POST", "/robot-api/v1/heartbeat", {**heartbeat, "health": []}, "token-placeholder")[0] == 400
    body = {"robotId": "robot-001", "deploymentId": "deploy-1", "executorRouteId": "route-1", "requestId": "request-1"}
    status, queued = request(base_url, "POST", "/bridge/v1/executions/execution-1/start", body, "admin-placeholder")
    assert status == 202
    status, duplicate = request(base_url, "POST", "/bridge/v1/executions/execution-1/start", body, "admin-placeholder")
    assert status == 202 and duplicate["commandId"] == queued["commandId"]
    command = request(base_url, "POST", "/robot-api/v1/heartbeat", heartbeat, "token-placeholder")[1]["command"]
    assert command["commandId"] == queued["commandId"]
    store.expire_leases_for_smoke()
    command = request(base_url, "POST", "/robot-api/v1/heartbeat", heartbeat, "token-placeholder")[1]["command"]
    ack = {"robotId": "robot-001", "leaseToken": command["leaseToken"], "status": "RECEIVED", "executionId": "execution-1"}
    assert request(base_url, "POST", f"/robot-api/v1/commands/{command['commandId']}/ack", ack, "token-placeholder")[0] == 200
    assert request(base_url, "POST", f"/robot-api/v1/commands/{command['commandId']}/ack", ack, "token-placeholder")[1]["state"] == "ACKED"
    assert request(base_url, "POST", "/robot-api/v1/heartbeat", heartbeat, "token-placeholder")[1]["command"] is None
    assert request(base_url, "GET", "/robot-api/v1/deployments/deploy-1/manifest", token="token-placeholder")[1]["yamlName"] == "site.yaml"
    events = [{"robot_id": "robot-001", "boot_id": "boot-1", "sequence": 2, "event": "route_started", "execution_id": "execution-1", "deployment_id": "deploy-1", "request_id": "request-1", "command_id": command["commandId"], "occurred_at": "2026-01-01T00:00:00Z"}]
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": events}, "token-placeholder")[1]["acceptedThroughSequence"] == 0
    events.insert(0, {**events[0], "sequence": 1, "event": "initial_pose_published"})
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": events}, "token-placeholder")[1]["acceptedThroughSequence"] == 2
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": events}, "token-placeholder")[1]["acceptedThroughSequence"] == 2
    assert request(base_url, "GET", "/bridge/v1/executions/execution-1", token="admin-placeholder")[1]["state"] == "RUNNING"
    assert store.command(command["commandId"])["state"] == "APPLIED"
    terminal = [{**events[-1], "sequence": 3, "event": "route_finished"}, {**events[-1], "sequence": 4, "event": "route_started"}]
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": terminal}, "token-placeholder")[1]["acceptedThroughSequence"] == 4
    assert request(base_url, "GET", "/bridge/v1/executions/execution-1", token="admin-placeholder")[1]["state"] == "COMPLETED"
    robot = request(base_url, "GET", "/bridge/v1/robots/robot-001", token="admin-placeholder")[1]
    assert robot["configured"] is True and robot["online"] is True and robot["acceptedEventSequence"] == 4
    second = {**body, "requestId": "request-2"}
    assert request(base_url, "POST", "/bridge/v1/executions/execution-2/start", second, "admin-placeholder")[0] == 202
    cancel = {"robotId": "robot-001", "requestId": "request-3"}
    assert request(base_url, "POST", "/bridge/v1/executions/execution-2/cancel", cancel, "admin-placeholder")[0] == 202
    assert request(base_url, "POST", "/robot-api/v1/heartbeat", heartbeat, "token-placeholder")[1]["command"]["type"] == "CANCEL"


if __name__ == "__main__":
    main()
