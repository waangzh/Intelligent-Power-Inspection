import hashlib
import hmac
import json
import os
import secrets
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import yaml
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response

from .store import Store, now


class BridgeError(Exception):
    def __init__(self, code, message, status=400):
        self.code, self.message, self.status = code, message, status


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def safe_name(value, suffix):
    path = Path(str(value or ""))
    if not value or path.name != value or path.is_absolute() or ".." in path.parts or not str(value).lower().endswith(suffix):
        raise BridgeError("INVALID_REQUEST", f"invalid {suffix} file name")
    return str(value)


def create_app() -> FastAPI:
    app = FastAPI(title="Robot Platform Bridge", version="1.0")
    try:
        robot_tokens = json.loads(os.environ.get("ROBOT_AUTH_TOKENS_JSON", "{}"))
    except json.JSONDecodeError as exc:
        raise RuntimeError("ROBOT_AUTH_TOKENS_JSON must be JSON") from exc
    platform_url = os.environ.get("PLATFORM_BASE_URL", "").rstrip("/")
    platform_token = os.environ.get("PLATFORM_BEARER_TOKEN", "")
    bridge_token = os.environ.get("BRIDGE_API_TOKEN", "")
    timeout = float(os.environ.get("BRIDGE_READ_TIMEOUT_SEC", "10"))
    store = Store(os.environ.get("ROBOT_BRIDGE_STORAGE_PATH", "~/.local/share/ylhb/robot-bridge.db"))
    app.state.store = store

    @app.middleware("http")
    async def admin_auth(request: Request, call_next):
        if request.url.path.startswith("/bridge/v1") and (not bridge_token or not hmac.compare_digest(request.headers.get("authorization", ""), f"Bearer {bridge_token}")):
            return JSONResponse(status_code=401, content={"code": "AUTH_FAILED", "message": "Bearer token required", "requestId": ""})
        return await call_next(request)

    @app.exception_handler(BridgeError)
    async def bridge_error(_request, exc):
        return JSONResponse(status_code=exc.status, content={"code": exc.code, "message": exc.message, "requestId": ""})

    def token_robot(request, body=None):
        value = request.headers.get("authorization", "")
        token = value[7:] if value.startswith("Bearer ") else ""
        requested = str((body or {}).get("robotId") or "")
        candidates = [robot_id for robot_id, expected in robot_tokens.items() if token and hmac.compare_digest(token, str(expected))]
        robot_id = requested or (candidates[0] if len(candidates) == 1 else "")
        if robot_id not in robot_tokens or not token or not hmac.compare_digest(token, str(robot_tokens[robot_id])):
            raise BridgeError("AUTH_FAILED", "Bearer token required", 401)
        return robot_id

    def platform_get(path, binary=False):
        if not platform_url or not platform_token:
            raise BridgeError("PLATFORM_UNREACHABLE", "platform configuration is missing", 503)
        request = urllib.request.Request(platform_url + path, headers={"Authorization": f"Bearer {platform_token}"})
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                content = response.read()
        except (urllib.error.HTTPError, OSError) as exc:
            raise BridgeError("PLATFORM_UNREACHABLE", str(exc), 503) from exc
        if binary:
            return content
        payload = json.loads(content.decode())
        return payload.get("data", payload) if isinstance(payload, dict) else payload

    @app.get("/bridge/v1/health")
    async def health():
        return {"ok": True, "robots": sorted(robot_tokens)}

    @app.post("/bridge/v1/deployments/{deployment_id}/sync")
    async def sync(deployment_id: str):
        deployment = platform_get(f"/api/v1/route-deployments/{deployment_id}")
        robot_id, revision_id = deployment.get("robotId"), deployment.get("routeRevisionId")
        if not robot_id or not revision_id or robot_id not in robot_tokens:
            raise BridgeError("INVALID_REQUEST", "platform deployment is incomplete")
        revision = platform_get(f"/api/v1/route-revisions/{revision_id}")
        route = revision.get("executorJson")
        route_revision_hash = hashlib.sha256(canonical(route)).hexdigest()
        if route_revision_hash != revision.get("contentSha256"):
            raise BridgeError("ROUTE_HASH_MISMATCH", "platform route revision hash mismatch")
        asset_id = revision.get("mapAssetId")
        asset = platform_get(f"/api/v1/map-assets/{asset_id}")
        yaml_name, pgm_name = safe_name(asset.get("yamlName"), (".yaml", ".yml")), safe_name(asset.get("pgmName"), (".pgm",))
        route_bytes = canonical(route)
        yaml_bytes, pgm = platform_get(f"/api/v1/map-assets/{asset_id}/yaml", True), platform_get(f"/api/v1/map-assets/{asset_id}/pgm", True)
        if hashlib.sha256(pgm).hexdigest() != asset.get("pgmSha256"):
            raise BridgeError("MAP_HASH_MISMATCH", "platform map hash mismatch")
        try:
            if Path(str(yaml.safe_load(yaml_bytes).get("image", ""))).name != pgm_name:
                raise ValueError("yaml image does not match pgm")
        except (yaml.YAMLError, AttributeError, ValueError) as exc:
            raise BridgeError("INVALID_MAP", str(exc)) from exc
        manifest = {"schemaVersion": "1.0", "deploymentId": deployment_id, "robotId": robot_id, "routeRevisionId": revision_id, "routeRevisionContentSha256": route_revision_hash, "routePayloadSha256": hashlib.sha256(route_bytes).hexdigest(), "routeContentSha256": hashlib.sha256(route_bytes).hexdigest(), "mapAssetId": asset_id, "mapImageSha256": asset["pgmSha256"], "yamlName": yaml_name, "pgmName": pgm_name}
        store.cache_deployment(deployment_id, robot_id, manifest, route_bytes, yaml_bytes, pgm)
        return {"deploymentId": deployment_id, "state": "READY_FOR_ROBOT", **manifest}

    @app.post("/bridge/v1/executions/{execution_id}/{action}", status_code=202)
    async def execution_control(execution_id: str, action: str, request: Request):
        command_type = action.upper()
        if command_type not in {"START", "PAUSE", "RESUME", "TAKEOVER", "CANCEL"}:
            raise HTTPException(status_code=404, detail="control not found")
        body = await request.json()
        robot_id, request_id = str(body.get("robotId") or ""), str(body.get("requestId") or "")
        if not robot_id or not request_id:
            raise BridgeError("INVALID_REQUEST", "robotId and requestId are required")
        deployment_id = str(body.get("deploymentId") or "")
        if command_type == "START":
            deployment = store.deployment(deployment_id)
            if not deployment or deployment["robot_id"] != robot_id or deployment["state"] != "READY_FOR_ROBOT":
                raise BridgeError("DEPLOYMENT_NOT_FOUND", "deployment is not ready for this robot", 404)
            if not str(body.get("executorRouteId") or ""):
                raise BridgeError("INVALID_REQUEST", "executorRouteId is required")
        else:
            execution = store.execution(execution_id)
            if not execution:
                raise BridgeError("EXECUTION_NOT_FOUND", "execution not found", 404)
            robot_id, deployment_id = execution["robot_id"], execution["deployment_id"]
        payload = {key: value for key, value in body.items() if key not in {"robotId", "requestId"}}
        if command_type == "START":
            payload["routeRevisionId"] = deployment["manifest"]["routeRevisionId"]
        payload.update({"executionId": execution_id, "deploymentId": deployment_id, "requestId": request_id, "type": command_type})
        item, conflict = store.queue_command(secrets.token_urlsafe(18), request_id, robot_id, str(body.get("taskId") or ""), execution_id, deployment_id, command_type, payload)
        if conflict:
            raise BridgeError("IDEMPOTENCY_CONFLICT", "requestId has different payload", 409)
        return {"accepted": True, "commandId": item["command_id"], "state": item["state"], "executionId": execution_id}

    @app.get("/bridge/v1/executions/{execution_id}")
    async def execution(execution_id):
        result = store.execution(execution_id)
        if not result:
            raise BridgeError("EXECUTION_NOT_FOUND", "execution not found", 404)
        return {"executionId": execution_id, "robotId": result["robot_id"], "deploymentId": result["deployment_id"], "state": result["state"], "lastEventSequence": result["last_event_sequence"], "lastError": result["last_error"]}

    @app.get("/bridge/v1/executions/{execution_id}/events")
    async def events(execution_id: str, afterSequence: int = 0, limit: int = 100):
        if not store.execution(execution_id):
            raise BridgeError("EXECUTION_NOT_FOUND", "execution not found", 404)
        return {"events": store.events(execution_id, afterSequence, limit)}

    @app.post("/robot-api/v1/heartbeat")
    async def heartbeat(request: Request):
        body = await request.json()
        robot_id = token_robot(request, body)
        if str(body.get("protocolVersion") or "") != "1.0" or not str(body.get("bootId") or ""):
            raise BridgeError("INVALID_REQUEST", "protocolVersion and bootId are required")
        store.upsert_robot(robot_id, body)
        command = store.lease_command(robot_id, secrets.token_urlsafe(18), str(body.get("state") or "idle"))
        output = None if not command else {"commandId": command["command_id"], "requestId": command["request_id"], "type": command["command_type"], "executionId": command["execution_id"], "deploymentId": command["deployment_id"], "leaseToken": command["lease_token"], **command["payload"]}
        robot = store.robot(robot_id) or {}
        return {"serverTime": now(), "nextHeartbeatSec": 1 if output else 3, "acceptedEventSequence": robot.get("last_event_sequence", 0), "command": output}

    @app.post("/robot-api/v1/commands/{command_id}/ack")
    async def ack(command_id: str, request: Request):
        body = await request.json()
        robot_id = token_robot(request, body)
        status = str(body.get("status") or "")
        if status not in {"RECEIVED", "REJECTED"} or not str(body.get("leaseToken") or ""):
            raise BridgeError("INVALID_REQUEST", "leaseToken and valid status are required")
        result = store.ack_command(command_id, robot_id, str(body["leaseToken"]), status, str(body.get("errorMessage") or ""))
        if not result:
            raise BridgeError("INVALID_LEASE", "command lease does not belong to robot", 409)
        return {"commandId": command_id, "state": result}

    @app.post("/robot-api/v1/events/batch")
    async def event_batch(request: Request):
        body = await request.json()
        robot_id = token_robot(request, body)
        events = body.get("events")
        if not isinstance(events, list) or len(events) > 100 or any(str(event.get("robot_id") or event.get("robotId") or robot_id) != robot_id for event in events if isinstance(event, dict)):
            raise BridgeError("INVALID_REQUEST", "events must be a robot-owned list of at most 100")
        if any(not isinstance(event, dict) for event in events):
            raise BridgeError("INVALID_REQUEST", "event must be an object")
        return {"acceptedThroughSequence": store.accept_events(robot_id, events)}

    @app.get("/robot-api/v1/deployments/{deployment_id}/{asset}")
    async def deployment_asset(deployment_id: str, asset: str, request: Request):
        robot_id = token_robot(request)
        path = store.deployment_file(deployment_id, robot_id, asset)
        if not path:
            raise BridgeError("DEPLOYMENT_NOT_FOUND", "deployment asset not found", 404)
        content = path.read_bytes()
        content_type = {"manifest": "application/json", "route": "application/json", "yaml": "application/yaml", "pgm": "image/x-portable-graymap"}.get(asset, "application/octet-stream")
        return Response(content, media_type=content_type, headers={"Content-Length": str(len(content)), "Content-Disposition": f'attachment; filename="{path.name}"'})

    return app


app = create_app()
