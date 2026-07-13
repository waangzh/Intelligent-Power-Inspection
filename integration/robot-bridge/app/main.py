import hashlib
import json
import os
import mimetypes
import urllib.error
import urllib.request
from datetime import datetime, timezone
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from .store import Store

STATE_MAP = {"idle": "CREATED", "starting": "DISPATCHING", "running": "RUNNING", "paused": "PAUSED", "manual_takeover": "MANUAL_TAKEOVER", "returning_home": "RUNNING", "waiting_loop": "RUNNING", "succeeded": "COMPLETED", "failed": "FAILED", "canceled": "CANCELLED"}


class BridgeError(Exception):
    def __init__(self, code, message, status=400, details=None):
        self.code, self.message, self.status, self.details = code, message, status, details


def now():
    return datetime.now(timezone.utc).isoformat()


def unwrap(payload: Any):
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


class Response:
    def __init__(self, status, content): self.status_code, self.content = status, content
    def json(self): return json.loads(self.content.decode("utf-8"))


def multipart(files):
    boundary = "----robotbridge" + os.urandom(8).hex()
    body = bytearray()
    for field, (name, content, content_type) in files.items():
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{name}\"\r\nContent-Type: {content_type or mimetypes.guess_type(name)[0] or 'application/octet-stream'}\r\n\r\n".encode())
        body.extend(content); body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode())
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def create_app() -> FastAPI:
    app = FastAPI(title="Robot Platform Bridge", version="1.0")
    endpoints = json.loads(os.environ.get("ROBOT_ENDPOINTS_JSON", "{}"))
    tokens = json.loads(os.environ.get("ROBOT_TOKENS_JSON", "{}"))
    platform_url = os.environ.get("PLATFORM_BASE_URL", "").rstrip("/")
    platform_token = os.environ.get("PLATFORM_BEARER_TOKEN", "")
    bridge_token = os.environ.get("BRIDGE_API_TOKEN", "")
    store = Store(os.environ.get("ROBOT_BRIDGE_STORAGE_PATH", "~/.local/share/ylhb/robot-bridge.db"))
    timeout = float(os.environ.get("BRIDGE_READ_TIMEOUT_SEC", "10"))

    @app.middleware("http")
    async def auth(request: Request, call_next):
        if request.url.path.startswith("/bridge/v1") and (not bridge_token or request.headers.get("authorization") != f"Bearer {bridge_token}"):
            return JSONResponse(status_code=401, content={"code": "AUTH_FAILED", "message": "Bearer token required", "requestId": ""})
        return await call_next(request)

    @app.exception_handler(BridgeError)
    async def bridge_error(_request, exc):
        body = {"code": exc.code, "message": exc.message, "requestId": ""}
        if exc.details is not None: body["details"] = exc.details
        return JSONResponse(status_code=exc.status, content=body)

    def robot(robot_id):
        if robot_id not in endpoints or robot_id not in tokens:
            raise BridgeError("INVALID_REQUEST", "robotId is not configured")
        return endpoints[robot_id].rstrip("/"), tokens[robot_id]

    async def request_json(method, url, *, headers=None, **kwargs):
        retries = 2 if method in ("GET", "PUT") else 1
        for attempt in range(retries):
            try:
                request_headers = dict(headers or {})
                data = None
                if "json" in kwargs:
                    data = json.dumps(kwargs["json"]).encode(); request_headers["Content-Type"] = "application/json"
                if "files" in kwargs:
                    data, request_headers["Content-Type"] = multipart(kwargs["files"])
                request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
                with urllib.request.urlopen(request, timeout=timeout) as response:
                    return Response(response.status, response.read())
            except urllib.error.HTTPError as exc:
                raise BridgeError("ROBOT_UNREACHABLE" if "/api/platform/" in url else "PLATFORM_UNREACHABLE", exc.read().decode("utf-8", "replace"), exc.code) from exc
            except OSError as exc:
                if attempt + 1 == retries:
                    raise BridgeError("ROBOT_UNREACHABLE" if "/api/platform/" in url else "PLATFORM_UNREACHABLE", str(exc), 503) from exc

    async def platform_get(path):
        if not platform_url or not platform_token:
            raise BridgeError("PLATFORM_UNREACHABLE", "platform configuration is missing", 503)
        response = await request_json("GET", platform_url + path, headers={"Authorization": f"Bearer {platform_token}"})
        return unwrap(response.json())

    @app.get("/bridge/v1/health")
    async def health():
        return {"ok": True, "robots": sorted(endpoints)}

    @app.post("/bridge/v1/deployments/{deployment_id}/sync")
    async def sync(deployment_id: str):
        deployment = await platform_get(f"/api/v1/route-deployments/{deployment_id}")
        robot_id, revision_id = deployment.get("robotId"), deployment.get("routeRevisionId")
        if not robot_id or not revision_id: raise BridgeError("INVALID_REQUEST", "platform deployment is incomplete")
        store.save_deployment(deployment_id, robot_id, "TRANSFERRING", {}, now())
        revision = await platform_get(f"/api/v1/route-revisions/{revision_id}")
        route = revision.get("executorJson")
        route_bytes = json.dumps(route, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
        route_hash = hashlib.sha256(route_bytes).hexdigest()
        if route_hash != revision.get("contentSha256"): raise BridgeError("ROUTE_HASH_MISMATCH", "platform route revision hash mismatch")
        asset_id = revision.get("mapAssetId")
        asset = await platform_get(f"/api/v1/map-assets/{asset_id}")
        headers = {"Authorization": f"Bearer {platform_token}"}
        yaml_response = await request_json("GET", platform_url + f"/api/v1/map-assets/{asset_id}/yaml", headers=headers)
        pgm_response = await request_json("GET", platform_url + f"/api/v1/map-assets/{asset_id}/pgm", headers=headers)
        if hashlib.sha256(pgm_response.content).hexdigest() != asset.get("pgmSha256"): raise BridgeError("MAP_HASH_MISMATCH", "platform map hash mismatch")
        endpoint, token = robot(robot_id)
        manifest = {"schemaVersion": "1.0", "robotId": robot_id, "routeRevisionId": revision_id, "routeContentSha256": route_hash, "mapAssetId": asset_id, "mapImageSha256": asset["pgmSha256"], "yamlName": asset["yamlName"], "pgmName": asset["pgmName"]}
        files = {"manifest": ("manifest.json", json.dumps(manifest).encode(), "application/json"), "route": ("route.json", route_bytes, "application/json"), "yaml": (asset["yamlName"], yaml_response.content, "application/yaml"), "pgm": (asset["pgmName"], pgm_response.content, "image/x-portable-graymap")}
        response = await request_json("PUT", endpoint + f"/api/platform/v1/deployments/{deployment_id}", headers={"Authorization": f"Bearer {token}"}, files=files)
        result = response.json()
        if result.get("routeContentSha256", route_hash) != route_hash or result.get("mapImageSha256", asset["pgmSha256"]) != asset["pgmSha256"]: raise BridgeError("DEPLOYMENT_CONFLICT", "robot returned different deployment hashes", 409)
        result["bridgeState"] = "DEPLOYED"
        store.save_deployment(deployment_id, robot_id, "DEPLOYED", result, now())
        return result

    async def control(execution_id, action, request):
        body = await request.json()
        robot_id, deployment_id, request_id = body.get("robotId"), body.get("deploymentId"), body.get("requestId")
        if not robot_id or not request_id: raise BridgeError("INVALID_REQUEST", "robotId and requestId are required")
        existing = store.execution(execution_id)
        if action == "start":
            if not deployment_id: raise BridgeError("INVALID_REQUEST", "deploymentId is required")
            store.save_execution(execution_id, robot_id, deployment_id, now())
        elif existing:
            deployment_id = existing["deployment_id"]
            robot_id = existing["robot_id"]
        else: raise BridgeError("EXECUTION_NOT_FOUND", "execution mapping not found", 404)
        endpoint, token = robot(robot_id)
        robot_body = {key: value for key, value in body.items() if key != "robotId"}
        response = await request_json("POST", endpoint + f"/api/platform/v1/executions/{execution_id}/{action}", headers={"Authorization": f"Bearer {token}"}, json=robot_body)
        return response.json()

    @app.post("/bridge/v1/executions/{execution_id}/{action}")
    async def execution_control(execution_id: str, action: str, request: Request):
        if action not in ("start", "pause", "resume", "takeover", "cancel"):
            raise HTTPException(status_code=404, detail="control not found")
        return await control(execution_id, action, request)

    @app.get("/bridge/v1/executions/{execution_id}")
    async def execution(execution_id):
        mapping = store.execution(execution_id)
        if not mapping: raise BridgeError("EXECUTION_NOT_FOUND", "execution mapping not found", 404)
        endpoint, token = robot(mapping["robot_id"])
        payload = (await request_json("GET", endpoint + f"/api/platform/v1/executions/{execution_id}", headers={"Authorization": f"Bearer {token}"})).json()
        payload["suggestedPlatformState"] = STATE_MAP.get(payload.get("state"), "CREATED")
        return payload

    @app.get("/bridge/v1/executions/{execution_id}/events")
    async def events(execution_id: str, afterSequence: int = 0, limit: int = 100):
        mapping = store.execution(execution_id)
        if not mapping: raise BridgeError("EXECUTION_NOT_FOUND", "execution mapping not found", 404)
        endpoint, token = robot(mapping["robot_id"])
        payload = (await request_json("GET", endpoint + f"/api/platform/v1/events?afterSequence={afterSequence}&limit={limit}", headers={"Authorization": f"Bearer {token}"})).json()
        events = [event for event in payload.get("events", []) if event.get("execution_id") == execution_id]
        if events: store.save_cursor(execution_id, max(event["sequence"] for event in events))
        return {"events": events, "cursor": store.cursor(execution_id)}

    return app


app = create_app()
