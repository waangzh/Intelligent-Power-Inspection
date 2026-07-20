import hashlib
import hmac
import json
import os
import secrets
import shutil
import tempfile
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import yaml
import httpx
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, Response

from .store import Store, StoreConflict, now


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
    upload_enabled = os.environ.get("BRIDGE_MAP_UPLOAD_ENABLED", "false").lower() in {"1", "true", "yes", "on"}
    upload_connect_timeout = float(os.environ.get("BRIDGE_MAP_UPLOAD_CONNECT_TIMEOUT_SEC", "10"))
    upload_read_timeout = float(os.environ.get("BRIDGE_MAP_UPLOAD_READ_TIMEOUT_SEC", "60"))
    upload_yaml_max = int(os.environ.get("BRIDGE_MAP_UPLOAD_YAML_MAX_BYTES", str(1024 * 1024)))
    upload_pgm_max = int(os.environ.get("BRIDGE_MAP_UPLOAD_PGM_MAX_BYTES", str(100 * 1024 * 1024)))
    upload_request_max = int(os.environ.get("BRIDGE_MAP_UPLOAD_REQUEST_MAX_BYTES", str(110 * 1024 * 1024)))
    upload_temp_root = Path(os.environ.get("BRIDGE_MAP_UPLOAD_TEMP_DIR", "~/.local/share/ylhb/map-upload-tmp")).expanduser()
    inspection_upload_enabled = os.environ.get("BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED", "false").lower() in {"1", "true", "yes", "on"}
    inspection_upload_connect_timeout = float(os.environ.get("BRIDGE_INSPECTION_IMAGE_UPLOAD_CONNECT_TIMEOUT_SEC", "10"))
    inspection_upload_read_timeout = float(os.environ.get("BRIDGE_INSPECTION_IMAGE_UPLOAD_READ_TIMEOUT_SEC", "60"))
    inspection_upload_max = int(os.environ.get("BRIDGE_INSPECTION_IMAGE_UPLOAD_MAX_BYTES", str(20 * 1024 * 1024)))
    inspection_upload_temp_root = Path(os.environ.get(
        "BRIDGE_INSPECTION_IMAGE_UPLOAD_TEMP_DIR", "~/.local/share/ylhb/inspection-image-upload-tmp"
    )).expanduser()
    store = Store(os.environ.get("ROBOT_BRIDGE_STORAGE_PATH", "~/.local/share/ylhb/robot-bridge.db"))
    app.state.store = store

    def valid_robot_authorization(value):
        token = value[7:] if value.startswith("Bearer ") else ""
        return bool(token) and any(
            hmac.compare_digest(token, str(expected))
            for expected in robot_tokens.values()
        )

    @app.middleware("http")
    async def admin_auth(request: Request, call_next):
        if request.url.path.startswith("/bridge/v1") and (not bridge_token or not hmac.compare_digest(request.headers.get("authorization", ""), f"Bearer {bridge_token}")):
            return JSONResponse(status_code=401, content={"code": "AUTH_FAILED", "message": "Bearer token required", "requestId": ""})
        if request.url.path.startswith("/robot-api/") and not valid_robot_authorization(request.headers.get("authorization", "")):
            return JSONResponse(status_code=401, content={"code": "AUTH_FAILED", "message": "Bearer token required", "requestId": ""})
        return await call_next(request)

    @app.exception_handler(BridgeError)
    async def bridge_error(_request, exc):
        return JSONResponse(status_code=exc.status, content={"code": exc.code, "message": exc.message, "requestId": ""})

    @app.exception_handler(StoreConflict)
    async def store_conflict(_request, exc):
        return JSONResponse(status_code=409, content={"code": exc.code, "message": str(exc), "requestId": ""})

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

    async def save_upload(upload, path, limit):
        digest = hashlib.sha256()
        size = 0
        with path.open("wb") as target:
            while chunk := await upload.read(1024 * 1024):
                size += len(chunk)
                if size > limit:
                    raise BridgeError("PAYLOAD_TOO_LARGE", "uploaded file exceeds configured size limit", 413)
                target.write(chunk)
                digest.update(chunk)
        return size, digest.hexdigest()

    def valid_sha256(value):
        return len(value) == 64 and all(character in "0123456789abcdefABCDEF" for character in value)

    def platform_error(status, payload):
        codes = {
            400: "INVALID_REQUEST", 401: "PLATFORM_AUTH_FAILED",
            403: "FORBIDDEN", 404: "ROBOT_NOT_FOUND",
            409: "CONTENT_CONFLICT", 413: "PAYLOAD_TOO_LARGE",
            429: "RATE_LIMITED",
        }
        return JSONResponse(status_code=status, content={
            "code": codes.get(status, "PLATFORM_UNREACHABLE" if status >= 500 else "PLATFORM_ERROR"),
            "message": str(payload.get("message") or f"platform HTTP {status}") if isinstance(payload, dict) else f"platform HTTP {status}",
            "requestId": "",
        })

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
        manifest = {"schemaVersion": "1.0", "deploymentId": deployment_id, "robotId": robot_id, "routeRevisionId": revision_id, "routeRevisionContentSha256": route_revision_hash, "routePayloadSha256": hashlib.sha256(route_bytes).hexdigest(), "routeContentSha256": hashlib.sha256(route_bytes).hexdigest(), "mapAssetId": asset_id, "mapImageSha256": asset["pgmSha256"], "yamlName": yaml_name, "pgmName": pgm_name}
        existing = store.deployment(deployment_id)
        if existing:
            complete = all(store.deployment_file(deployment_id, robot_id, name) for name in ("manifest", "route", "yaml", "pgm"))
            if existing["robot_id"] == robot_id and existing["manifest"] == manifest and complete:
                return {"deploymentId": deployment_id, "state": "READY_FOR_ROBOT", **manifest}
            raise BridgeError("DEPLOYMENT_CONFLICT", "deploymentId already has different identity or incomplete cache", 409)
        yaml_bytes, pgm = platform_get(f"/api/v1/map-assets/{asset_id}/yaml", True), platform_get(f"/api/v1/map-assets/{asset_id}/pgm", True)
        if hashlib.sha256(pgm).hexdigest() != asset.get("pgmSha256"):
            raise BridgeError("MAP_HASH_MISMATCH", "platform map hash mismatch")
        try:
            if Path(str(yaml.safe_load(yaml_bytes).get("image", ""))).name != pgm_name:
                raise ValueError("yaml image does not match pgm")
        except (yaml.YAMLError, AttributeError, ValueError) as exc:
            raise BridgeError("INVALID_MAP", str(exc)) from exc
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
            message = "executionId belongs to another robot or deployment" if conflict == "EXECUTION_CONFLICT" else "requestId has different payload"
            raise BridgeError(conflict, message, 409)
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

    @app.get("/bridge/v1/robots/{robot_id}")
    async def robot_status(robot_id: str):
        robot = store.robot(robot_id)
        if not robot and robot_id not in robot_tokens:
            raise BridgeError("ROBOT_NOT_FOUND", "robot not found", 404)
        robot = robot or {}
        try:
            last_seen = datetime.fromisoformat(str(robot.get("last_seen") or ""))
            online = (datetime.now(timezone.utc) - last_seen).total_seconds() <= 12
        except ValueError:
            online = False
        status = json.loads(robot.get("status_json") or "{}")
        return {
            "robotId": robot_id, "configured": robot_id in robot_tokens, "online": online,
            "bootId": robot.get("boot_id", ""), "state": robot.get("state", "offline"),
            "lastSeen": robot.get("last_seen", ""),
            "acceptedEventSequence": int(robot.get("last_event_sequence", 0)),
            "protocolVersion": status.get("protocolVersion", ""),
            "activeExecutionId": status.get("activeExecutionId"),
            "activeDeploymentId": status.get("activeDeploymentId"),
            "softwareVersion": status.get("softwareVersion"), "health": status.get("health", {}),
        }

    @app.post("/robot-api/v1/heartbeat")
    async def heartbeat(request: Request):
        try:
            body = await request.json()
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise BridgeError("INVALID_REQUEST", "invalid heartbeat payload") from exc
        if not isinstance(body, dict):
            raise BridgeError("INVALID_REQUEST", "invalid heartbeat payload")
        robot_id = token_robot(request, body)
        required_text = ("robotId", "bootId", "softwareVersion", "state")
        valid_states = {"idle", "starting", "running", "paused", "manual_takeover", "returning_home", "waiting_loop", "succeeded", "failed", "canceled"}
        sequence = body.get("latestLocalEventSequence")
        if (
            str(body.get("protocolVersion") or "") != "1.0"
            or str(body.get("robotId") or "") != robot_id
            or any(not str(body.get(field) or "") for field in required_text)
            or str(body.get("state") or "") not in valid_states
            or isinstance(sequence, bool)
            or not isinstance(sequence, int)
            or sequence < 0
            or not isinstance(body.get("health"), dict)
        ):
            raise BridgeError("INVALID_REQUEST", "invalid heartbeat payload")
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

    @app.post("/robot-api/v1/map-assets")
    async def upload_map_asset(
        request: Request,
        yaml_file: UploadFile = File(alias="yaml"),
        pgm_file: UploadFile = File(alias="pgm"),
        captured_at: str = Form(default="", alias="capturedAt"),
        content_identity: str = Form(alias="contentIdentitySha256"),
        yaml_sha256: str = Form(default="", alias="yamlSha256"),
        pgm_sha256: str = Form(default="", alias="pgmSha256"),
    ):
        if not upload_enabled:
            raise BridgeError("UPLOAD_DISABLED", "map upload is disabled", 503)
        robot_id = token_robot(request)
        idempotency_key = request.headers.get("idempotency-key", "").strip()
        if not idempotency_key or len(idempotency_key) > 160:
            raise BridgeError("INVALID_REQUEST", "valid Idempotency-Key is required")
        yaml_name = safe_name(yaml_file.filename, (".yaml", ".yml"))
        pgm_name = safe_name(pgm_file.filename, (".pgm",))
        if not valid_sha256(content_identity) or not valid_sha256(yaml_sha256) or not valid_sha256(pgm_sha256):
            raise BridgeError("INVALID_REQUEST", "content and audit hashes must be SHA-256")
        upload_temp_root.mkdir(parents=True, exist_ok=True)
        temporary = Path(tempfile.mkdtemp(prefix="map-upload-", dir=upload_temp_root))
        yaml_path, pgm_path = temporary / yaml_name, temporary / pgm_name
        try:
            yaml_size, actual_yaml_sha = await save_upload(yaml_file, yaml_path, upload_yaml_max)
            pgm_size, actual_pgm_sha = await save_upload(pgm_file, pgm_path, upload_pgm_max)
            if yaml_size + pgm_size > upload_request_max:
                raise BridgeError("PAYLOAD_TOO_LARGE", "map upload exceeds configured request size", 413)
            if not hmac.compare_digest(yaml_sha256.lower(), actual_yaml_sha):
                raise BridgeError("CONTENT_MISMATCH", "YAML hash mismatch", 409)
            if not hmac.compare_digest(pgm_sha256.lower(), actual_pgm_sha):
                raise BridgeError("CONTENT_MISMATCH", "PGM hash mismatch", 409)
            if not platform_url or not platform_token:
                raise BridgeError("PLATFORM_UNREACHABLE", "platform configuration is missing", 503)
            headers = {
                "Authorization": f"Bearer {platform_token}",
                "X-Bridge-Robot-Id": robot_id,
                "Idempotency-Key": idempotency_key,
            }
            timeout_config = httpx.Timeout(upload_read_timeout, connect=upload_connect_timeout)
            try:
                with yaml_path.open("rb") as yaml_source, pgm_path.open("rb") as pgm_source:
                    async with httpx.AsyncClient(timeout=timeout_config, trust_env=False) as client:
                        response = await client.post(
                            platform_url + "/api/v1/internal/robot-map-assets",
                            headers=headers,
                            data={
                                "capturedAt": captured_at,
                                "contentIdentitySha256": content_identity,
                            },
                            files={
                                "yaml": (yaml_name, yaml_source, "application/x-yaml"),
                                "pgm": (pgm_name, pgm_source, "image/x-portable-graymap"),
                            },
                        )
            except httpx.HTTPError as exc:
                raise BridgeError("PLATFORM_UNREACHABLE", f"platform upload failed: {type(exc).__name__}", 503) from exc
            try:
                payload = response.json()
            except ValueError as exc:
                raise BridgeError("PLATFORM_UNREACHABLE", "platform returned invalid JSON", 503) from exc
            if response.status_code >= 400:
                return platform_error(response.status_code, payload)
            asset = payload.get("data", payload)
            return JSONResponse(status_code=response.status_code, content={
                "mapAssetId": asset.get("id"),
                "status": asset.get("status"),
                "contentIdentitySha256": asset.get("contentIdentitySha256"),
                "yamlSha256": asset.get("yamlSha256"),
                "pgmSha256": asset.get("pgmSha256"),
            })
        finally:
            await yaml_file.close()
            await pgm_file.close()
            shutil.rmtree(temporary, ignore_errors=True)

    @app.post("/robot-api/v1/inspection-images")
    async def upload_inspection_image(
        request: Request,
        image: UploadFile = File(),
        execution_id: str = Form(alias="executionId"),
        task_id: str = Form(alias="taskId"),
        checkpoint_id: str = Form(alias="checkpointId"),
        captured_at: str = Form(alias="capturedAt"),
        image_sha256: str = Form(alias="imageSha256"),
        width: str = Form(default=""),
        height: str = Form(default=""),
    ):
        if not inspection_upload_enabled:
            raise BridgeError("UPLOAD_DISABLED", "inspection image upload is disabled", 503)
        robot_id = token_robot(request)
        idempotency_key = request.headers.get("idempotency-key", "").strip()
        if not idempotency_key or len(idempotency_key) > 160:
            raise BridgeError("INVALID_REQUEST", "valid Idempotency-Key is required")
        if not all(value.strip() for value in (execution_id, task_id, checkpoint_id, captured_at)):
            raise BridgeError("INVALID_REQUEST", "executionId, taskId, checkpointId and capturedAt are required")
        if not valid_sha256(image_sha256):
            raise BridgeError("INVALID_REQUEST", "imageSha256 must be SHA-256")
        image_name = safe_name(image.filename, (".jpg", ".jpeg", ".png", ".webp", ".bmp"))
        inspection_upload_temp_root.mkdir(parents=True, exist_ok=True)
        temporary = Path(tempfile.mkdtemp(prefix="inspection-image-", dir=inspection_upload_temp_root))
        image_path = temporary / image_name
        try:
            _, actual_sha = await save_upload(image, image_path, inspection_upload_max)
            if not hmac.compare_digest(image_sha256.lower(), actual_sha):
                raise BridgeError("CONTENT_MISMATCH", "inspection image hash mismatch", 409)
            if not platform_url or not platform_token:
                raise BridgeError("PLATFORM_UNREACHABLE", "platform configuration is missing", 503)
            data = {
                "executionId": execution_id.strip(),
                "taskId": task_id.strip(),
                "checkpointId": checkpoint_id.strip(),
                "capturedAt": captured_at.strip(),
                "imageSha256": actual_sha,
            }
            if width.strip():
                data["width"] = width.strip()
            if height.strip():
                data["height"] = height.strip()
            try:
                with image_path.open("rb") as image_source:
                    async with httpx.AsyncClient(
                        timeout=httpx.Timeout(
                            inspection_upload_read_timeout,
                            connect=inspection_upload_connect_timeout,
                        ),
                        trust_env=False,
                    ) as client:
                        response = await client.post(
                            platform_url + "/api/v1/internal/robot-inspection-images",
                            headers={
                                "Authorization": f"Bearer {platform_token}",
                                "X-Bridge-Robot-Id": robot_id,
                                "Idempotency-Key": idempotency_key,
                            },
                            data=data,
                            files={"image": (image_name, image_source, image.content_type or "application/octet-stream")},
                        )
            except httpx.HTTPError as exc:
                raise BridgeError("PLATFORM_UNREACHABLE", f"platform upload failed: {type(exc).__name__}", 503) from exc
            try:
                payload = response.json()
            except ValueError as exc:
                raise BridgeError("PLATFORM_UNREACHABLE", "platform returned invalid JSON", 503) from exc
            if response.status_code >= 400:
                return platform_error(response.status_code, payload)
            saved = payload.get("data", payload)
            return JSONResponse(status_code=response.status_code, content={
                "imageId": saved.get("id"),
                "status": saved.get("status"),
                "source": saved.get("source"),
                "executionId": saved.get("executionId"),
                "taskId": saved.get("taskId"),
                "checkpointId": saved.get("checkpointId"),
            })
        finally:
            await image.close()
            shutil.rmtree(temporary, ignore_errors=True)

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
