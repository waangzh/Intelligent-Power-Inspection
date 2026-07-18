#!/usr/bin/env python3
"""Local, no-network smoke test for the outbound robot protocol."""
import hashlib
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from app.store import Store

urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()


class PlatformStub:
    def __init__(self):
        self.route = {"version": 3, "active_route_id": "route-1", "routes": [{"id": "route-1"}], "targets": []}
        self.route_hash = hashlib.sha256(canonical(self.route)).hexdigest()
        self.pgm = b"P5\n1 1\n255\n\0"
        self.map_hash = hashlib.sha256(self.pgm).hexdigest()
        self.upload_identity = ""
        self.upload_requests = 0
        self.inspection_upload_requests = 0
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                return

            def do_GET(self):
                if self.path == "/api/v1/route-deployments/deploy-1":
                    return self.respond_json({"id": "deploy-1", "robotId": "robot-001", "routeRevisionId": "route-r1"})
                if self.path == "/api/v1/route-revisions/route-r1":
                    return self.respond_json({"id": "route-r1", "executorJson": owner.route, "contentSha256": owner.route_hash, "mapAssetId": "map-1"})
                if self.path == "/api/v1/map-assets/map-1":
                    return self.respond_json({"id": "map-1", "yamlName": "site.yaml", "pgmName": "site.pgm", "pgmSha256": owner.map_hash})
                if self.path == "/api/v1/map-assets/map-1/yaml":
                    return self.respond_bytes(b"image: site.pgm\n", "application/yaml")
                if self.path == "/api/v1/map-assets/map-1/pgm":
                    return self.respond_bytes(owner.pgm, "image/x-portable-graymap")
                self.send_response(404)
                self.end_headers()

            def do_POST(self):
                if self.path == "/api/v1/internal/robot-inspection-images":
                    length = int(self.headers.get("Content-Length", "0"))
                    body = self.rfile.read(length)
                    assert self.headers.get("Authorization") == "Bearer platform-placeholder"
                    assert self.headers.get("X-Bridge-Robot-Id") == "robot-001"
                    assert self.headers.get("Idempotency-Key") == "inspection-upload-smoke"
                    assert b'name="image"' in body
                    assert b'name="executionId"' in body and b'execution-1' in body
                    assert b'name="taskId"' in body and b'task-1' in body
                    assert b'name="checkpointId"' in body and b'checkpoint-1' in body
                    owner.inspection_upload_requests += 1
                    return self.respond_json({"data": {
                        "id": "rimg-smoke-1", "source": "ROBOT_BRIDGE",
                        "executionId": "execution-1", "taskId": "task-1",
                        "checkpointId": "checkpoint-1", "status": "AVAILABLE",
                    }}, status=201)
                if self.path == "/api/v1/internal/robot-map-assets":
                    length = int(self.headers.get("Content-Length", "0"))
                    body = self.rfile.read(length)
                    assert self.headers.get("Authorization") == "Bearer platform-placeholder"
                    assert self.headers.get("X-Bridge-Robot-Id") == "robot-001"
                    assert self.headers.get("Idempotency-Key") == "map-upload-smoke"
                    assert b"name=\"yaml\"" in body and b"name=\"pgm\"" in body
                    owner.upload_requests += 1
                    return self.respond_json({"data": {
                        "id": "map-upload-1", "status": "PENDING_REVIEW",
                        "contentIdentitySha256": owner.upload_identity,
                        "yamlSha256": "a" * 64, "pgmSha256": owner.map_hash,
                    }}, status=201)
                self.send_response(404)
                self.end_headers()

            def respond_json(self, value, status=200):
                self.respond_bytes(json.dumps(value).encode(), "application/json", status)

            def respond_bytes(self, value, content_type, status=200):
                self.send_response(status)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(value)))
                self.end_headers()
                self.wfile.write(value)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    @property
    def base_url(self):
        return f"http://127.0.0.1:{self.server.server_address[1]}"

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)


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


def multipart_request(base_url, path, fields, files, token="", idempotency_key=""):
    boundary = "ylhb-smoke-boundary"
    body = bytearray()
    for name, value in fields.items():
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    for name, (filename, content, content_type) in files.items():
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\nContent-Type: {content_type}\r\n\r\n'.encode())
        body.extend(content)
        body.extend(b"\r\n")
    body.extend(f'--{boundary}--\r\n'.encode())
    item = urllib.request.Request(base_url + path, data=bytes(body), headers={
        "Authorization": f"Bearer {token}", "Idempotency-Key": idempotency_key,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    }, method="POST")
    try:
        with urllib.request.urlopen(item, timeout=3) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as exc:
        content = exc.read()
        try:
            return exc.code, json.loads(content)
        except json.JSONDecodeError:
            return exc.code, {"raw": content.decode(errors="replace"), "reason": str(exc)}


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_ready(process, base_url):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if process.poll() is not None:
            details = process.stderr.read().decode(errors="replace").strip() if process.stderr else ""
            raise RuntimeError(f"uvicorn exited with {process.returncode}: {details}")
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
        platform = PlatformStub()
        environment = {
            **os.environ,
            "ROBOT_BRIDGE_STORAGE_PATH": db_path,
            "ROBOT_AUTH_TOKENS_JSON": json.dumps({"robot-001": "token-placeholder"}),
            "BRIDGE_API_TOKEN": "admin-placeholder",
            "PLATFORM_BASE_URL": platform.base_url,
            "PLATFORM_BEARER_TOKEN": "platform-placeholder",
            "BRIDGE_MAP_UPLOAD_ENABLED": "true",
            "BRIDGE_MAP_UPLOAD_TEMP_DIR": str(Path(temporary) / "uploads"),
            "BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED": "true",
            "BRIDGE_INSPECTION_IMAGE_UPLOAD_TEMP_DIR": str(Path(temporary) / "inspection-uploads"),
            "NO_PROXY": "127.0.0.1,localhost",
            "no_proxy": "127.0.0.1,localhost",
        }
        store = Store(db_path)
        port = free_port()
        base_url = f"http://127.0.0.1:{port}"
        process = subprocess.Popen(
            [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", str(port), "--log-level", "error"],
            cwd=Path(__file__).parent,
            env=environment,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        try:
            wait_ready(process, base_url)
            run_smoke(base_url, store, platform)
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            if sys.exc_info()[0] is not None and process.stderr:
                details = process.stderr.read().decode(errors="replace").strip()
                if details:
                    print(details, file=sys.stderr)
            platform.close()
    print("bridge smoke: ok")


def run_smoke(base_url, store, platform) -> None:
    status, synced = request(base_url, "POST", "/bridge/v1/deployments/deploy-1/sync", token="admin-placeholder")
    assert status == 200 and synced["state"] == "READY_FOR_ROBOT"
    assert synced["deploymentId"] == "deploy-1" and synced["robotId"] == "robot-001"
    assert synced["routeRevisionContentSha256"] == synced["routePayloadSha256"] == synced["routeContentSha256"]
    status, retried = request(base_url, "POST", "/bridge/v1/deployments/deploy-1/sync", token="admin-placeholder")
    assert status == 200 and retried == synced
    cached = store.deployment("deploy-1")
    assert cached and cached["robot_id"] == "robot-001" and cached["manifest"]["mapImageSha256"] == synced["mapImageSha256"]
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
    events = [{"schema_version": "1.0", "robot_id": "robot-001", "boot_id": "boot-1", "sequence": 2, "event": "route_started", "execution_id": "execution-1", "deployment_id": "deploy-1", "request_id": "request-1", "command_id": command["commandId"], "occurred_at": "2026-01-01T00:00:00Z"}]
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": events}, "token-placeholder")[1]["acceptedThroughSequence"] == 0
    events.insert(0, {**events[0], "sequence": 1, "event": "initial_pose_published"})
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": events}, "token-placeholder")[1]["acceptedThroughSequence"] == 2
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": events}, "token-placeholder")[1]["acceptedThroughSequence"] == 2
    assert request(base_url, "GET", "/bridge/v1/executions/execution-1", token="admin-placeholder")[1]["state"] == "RUNNING"
    pulled = request(base_url, "GET", "/bridge/v1/executions/execution-1/events?afterSequence=0", token="admin-placeholder")[1]["events"]
    assert [event["sequence"] for event in pulled] == [1, 2]
    assert request(base_url, "GET", "/bridge/v1/executions/execution-1/events?afterSequence=2", token="admin-placeholder")[1]["events"] == []
    assert store.command(command["commandId"])["state"] == "APPLIED"
    terminal = [{**events[-1], "sequence": 3, "event": "route_finished"}, {**events[-1], "sequence": 4, "event": "route_started"}]
    assert request(base_url, "POST", "/robot-api/v1/events/batch", {"robotId": "robot-001", "events": terminal}, "token-placeholder")[1]["acceptedThroughSequence"] == 4
    assert request(base_url, "GET", "/bridge/v1/executions/execution-1", token="admin-placeholder")[1]["state"] == "COMPLETED"
    upload_yaml = b"image: floor.pgm\nresolution: 0.05\norigin: [0, 0, 0]\n"
    upload_pgm = platform.pgm
    identity = hashlib.sha256(canonical({
        "pgmSha256": hashlib.sha256(upload_pgm).hexdigest(), "resolution": "0.05",
        "origin": ["0", "0", "0"], "negate": 0, "occupiedThresh": "0.65",
        "freeThresh": "0.25", "mode": "trinary",
    })).hexdigest()
    platform.upload_identity = identity
    fields = {
        "contentIdentitySha256": identity,
        "yamlSha256": hashlib.sha256(upload_yaml).hexdigest(),
        "pgmSha256": hashlib.sha256(upload_pgm).hexdigest(),
    }
    files = {
        "yaml": ("floor.yaml", upload_yaml, "application/x-yaml"),
        "pgm": ("floor.pgm", upload_pgm, "image/x-portable-graymap"),
    }
    assert multipart_request(base_url, "/robot-api/v1/map-assets", fields, files)[0] == 401
    status, uploaded = multipart_request(
        base_url, "/robot-api/v1/map-assets", fields, files,
        token="token-placeholder", idempotency_key="map-upload-smoke")
    assert status == 201 and uploaded.get("mapAssetId") == "map-upload-1", (status, uploaded)
    assert uploaded["contentIdentitySha256"] == identity and platform.upload_requests == 1
    inspection = b"\xff\xd8\xff\xdbsmoke-inspection-image"
    inspection_fields = {
        "executionId": "execution-1", "taskId": "task-1", "checkpointId": "checkpoint-1",
        "capturedAt": "2026-07-18T00:00:00Z", "imageSha256": hashlib.sha256(inspection).hexdigest(),
    }
    inspection_files = {"image": ("inspection.jpg", inspection, "image/jpeg")}
    assert multipart_request(base_url, "/robot-api/v1/inspection-images", inspection_fields, inspection_files)[0] == 401
    status, inspection_uploaded = multipart_request(
        base_url, "/robot-api/v1/inspection-images", inspection_fields, inspection_files,
        token="token-placeholder", idempotency_key="inspection-upload-smoke")
    assert status == 201 and inspection_uploaded.get("imageId") == "rimg-smoke-1", (status, inspection_uploaded)
    assert inspection_uploaded["status"] == "AVAILABLE" and platform.inspection_upload_requests == 1
    robot = request(base_url, "GET", "/bridge/v1/robots/robot-001", token="admin-placeholder")[1]
    assert robot["configured"] is True and robot["online"] is True and robot["acceptedEventSequence"] == 4
    second = {**body, "requestId": "request-2"}
    assert request(base_url, "POST", "/bridge/v1/executions/execution-2/start", second, "admin-placeholder")[0] == 202
    cancel = {"robotId": "robot-001", "requestId": "request-3"}
    assert request(base_url, "POST", "/bridge/v1/executions/execution-2/cancel", cancel, "admin-placeholder")[0] == 202
    assert request(base_url, "POST", "/robot-api/v1/heartbeat", heartbeat, "token-placeholder")[1]["command"]["type"] == "CANCEL"


if __name__ == "__main__":
    main()
