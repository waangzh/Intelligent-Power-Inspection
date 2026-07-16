import json
import os
import shutil
import sqlite3
import tempfile
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path


SCHEMA_VERSION = 2
TERMINAL_COMMAND_STATES = {"APPLIED", "REJECTED", "FAILED"}
TERMINAL_EXECUTION_STATES = {"COMPLETED", "FAILED", "CANCELLED"}
EVENT_STATES = {
    "route_started": "RUNNING", "route_paused": "PAUSED", "route_resumed": "RUNNING",
    "manual_takeover": "MANUAL_TAKEOVER", "route_finished": "COMPLETED",
    "route_failed": "FAILED", "route_canceled": "CANCELLED",
}
EXECUTION_TRANSITIONS = {
    "CREATED": {"DISPATCHING"},
    "DISPATCHING": {"RUNNING", "FAILED", "CANCELLED"},
    "RUNNING": {"PAUSED", "MANUAL_TAKEOVER", "COMPLETED", "FAILED", "CANCELLED"},
    "PAUSED": {"RUNNING", "CANCELLED", "FAILED"},
    "MANUAL_TAKEOVER": {"RUNNING", "CANCELLED"},
    "COMPLETED": set(), "FAILED": set(), "CANCELLED": set(),
}
COMMAND_RESULT_EVENTS = {
    "START": "route_started", "PAUSE": "route_paused", "RESUME": "route_resumed",
    "TAKEOVER": "manual_takeover", "CANCEL": "route_canceled",
}
START_EVENT_TYPES = {
    "command_accepted", "initial_pose_published", "route_started", "target_reached",
    "target_task_finished", "return_home_started", "route_finished", "route_failed",
}
DELIVERY_FIELDS = {"leaseToken", "leaseUntil", "serverTime", "attemptCount", "deliveryAttempt", "deliveredAt", "nextHeartbeatSec"}


class StoreConflict(ValueError):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


class Store:
    def __init__(self, path: str):
        self.path = Path(path).expanduser()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.deployments_root = self.path.parent / "storage" / "deployments"
        self.deployments_root.mkdir(parents=True, exist_ok=True)
        with self.db() as db:
            legacy_tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            db.executescript("""
              CREATE TABLE IF NOT EXISTS schema_meta (schema_version INTEGER NOT NULL);
              CREATE TABLE IF NOT EXISTS deployment_mappings (deployment_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, state TEXT NOT NULL, result_json TEXT NOT NULL, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS robots (robot_id TEXT PRIMARY KEY, boot_id TEXT NOT NULL DEFAULT '', state TEXT NOT NULL DEFAULT 'offline', status_json TEXT NOT NULL DEFAULT '{}', last_seen TEXT NOT NULL, last_event_sequence INTEGER NOT NULL DEFAULT 0, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS executions (execution_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, deployment_id TEXT NOT NULL, state TEXT NOT NULL, last_event_sequence INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '', updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS commands (command_id TEXT PRIMARY KEY, request_id TEXT NOT NULL UNIQUE, robot_id TEXT NOT NULL, task_id TEXT NOT NULL DEFAULT '', execution_id TEXT NOT NULL, deployment_id TEXT NOT NULL DEFAULT '', command_type TEXT NOT NULL, payload_json TEXT NOT NULL, state TEXT NOT NULL, lease_token TEXT NOT NULL DEFAULT '', lease_until TEXT NOT NULL DEFAULT '', attempt_count INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, acked_at TEXT NOT NULL DEFAULT '');
              CREATE TABLE IF NOT EXISTS events (robot_id TEXT NOT NULL, sequence INTEGER NOT NULL, event_json TEXT NOT NULL, occurred_at TEXT NOT NULL, PRIMARY KEY(robot_id, sequence));
            """)
            version = db.execute("SELECT schema_version FROM schema_meta LIMIT 1").fetchone()
            if not version or int(version[0]) < SCHEMA_VERSION:
                self._migrate_legacy(db, legacy_tables)
                db.execute("DELETE FROM schema_meta")
                db.execute("INSERT INTO schema_meta VALUES (?)", (SCHEMA_VERSION,))
        self._cleanup_orphans()

    def _migrate_legacy(self, db, legacy_tables):
        if "execution_mappings" in legacy_tables:
            for row in db.execute("SELECT execution_id, robot_id, deployment_id, updated_at FROM execution_mappings"):
                db.execute("INSERT OR IGNORE INTO executions(execution_id,robot_id,deployment_id,state,updated_at) VALUES (?,?,?,?,?)", (row[0], row[1], row[2], "CREATED", row[3]))
        if "event_cursors" in legacy_tables:
            for row in db.execute("SELECT execution_id, sequence FROM event_cursors"):
                db.execute("UPDATE executions SET last_event_sequence=MAX(last_event_sequence,?) WHERE execution_id=?", (int(row[1]), row[0]))

    def _cleanup_orphans(self):
        with self.db() as db:
            valid = {row[0] for row in db.execute("SELECT deployment_id FROM deployment_mappings")}
        for path in self.deployments_root.iterdir():
            if path.is_dir() and (path.name.startswith(".staging-") or path.name not in valid):
                shutil.rmtree(path, ignore_errors=True)

    @contextmanager
    def db(self):
        db = sqlite3.connect(self.path, timeout=5)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA busy_timeout=5000")
        try:
            yield db
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    @staticmethod
    def _payload(row):
        result = dict(row)
        result["payload"] = json.loads(result.pop("payload_json"))
        return result

    def upsert_robot(self, robot_id, body):
        stamp = now()
        with self.db() as db:
            db.execute("INSERT INTO robots(robot_id,boot_id,state,status_json,last_seen,updated_at) VALUES (?,?,?,?,?,?) ON CONFLICT(robot_id) DO UPDATE SET boot_id=excluded.boot_id,state=excluded.state,status_json=excluded.status_json,last_seen=excluded.last_seen,updated_at=excluded.updated_at", (robot_id, str(body.get("bootId") or ""), str(body.get("state") or "unknown"), json.dumps(body, ensure_ascii=False), stamp, stamp))

    def robot(self, robot_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM robots WHERE robot_id=?", (robot_id,)).fetchone()
        return dict(row) if row else None

    @staticmethod
    def _deployment_identity(robot_id, manifest):
        keys = ("routeRevisionId", "routeRevisionContentSha256", "routePayloadSha256", "mapAssetId", "mapImageSha256", "yamlName", "pgmName")
        return (robot_id,) + tuple(str(manifest.get(key) or "") for key in keys)

    def cache_deployment(self, deployment_id, robot_id, manifest, route, yaml_bytes, pgm):
        if Path(deployment_id).name != deployment_id or not deployment_id or ".." in Path(deployment_id).parts:
            raise ValueError("invalid deployment id")
        existing = self.deployment(deployment_id)
        target = self.deployments_root / deployment_id
        if existing:
            complete = all((target / name).is_file() for name in ("manifest.json", "route.json", existing["manifest"].get("yamlName", ""), existing["manifest"].get("pgmName", "")))
            if self._deployment_identity(existing["robot_id"], existing["manifest"]) == self._deployment_identity(robot_id, manifest) and complete:
                return existing
            raise StoreConflict("DEPLOYMENT_CONFLICT", "deploymentId already has different identity or incomplete cache")
        staging = Path(tempfile.mkdtemp(prefix=".staging-", dir=self.deployments_root))
        installed = False
        try:
            (staging / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True), encoding="utf-8")
            (staging / "route.json").write_bytes(route)
            (staging / manifest["yamlName"]).write_bytes(yaml_bytes)
            (staging / manifest["pgmName"]).write_bytes(pgm)
            os.replace(staging, target)
            installed = True
            with self.db() as db:
                db.execute("INSERT INTO deployment_mappings VALUES (?, ?, ?, ?, ?)", (deployment_id, robot_id, "READY_FOR_ROBOT", json.dumps(manifest, ensure_ascii=False), now()))
            return self.deployment(deployment_id)
        except Exception:
            if installed:
                shutil.rmtree(target, ignore_errors=True)
            raise
        finally:
            shutil.rmtree(staging, ignore_errors=True)

    def deployment(self, deployment_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM deployment_mappings WHERE deployment_id=?", (deployment_id,)).fetchone()
        return {**dict(row), "manifest": json.loads(row["result_json"])} if row else None

    def deployment_file(self, deployment_id, robot_id, name):
        if Path(deployment_id).name != deployment_id:
            return None
        deployment = self.deployment(deployment_id)
        if not deployment or deployment["robot_id"] != robot_id or deployment["state"] != "READY_FOR_ROBOT":
            return None
        names = {"manifest": "manifest.json", "route": "route.json", "yaml": deployment["manifest"].get("yamlName"), "pgm": deployment["manifest"].get("pgmName")}
        filename = names.get(name)
        if not filename or Path(filename).name != filename:
            return None
        path = self.deployments_root / deployment_id / filename
        return path if path.is_file() else None

    def queue_command(self, command_id, request_id, robot_id, task_id, execution_id, deployment_id, command_type, payload):
        payload = {key: value for key, value in payload.items() if key not in DELIVERY_FIELDS}
        encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            existing = db.execute("SELECT * FROM commands WHERE request_id=?", (request_id,)).fetchone()
            if existing:
                item = self._payload(existing)
                if item["robot_id"] != robot_id or item["command_type"] != command_type or json.dumps(item["payload"], ensure_ascii=False, sort_keys=True, separators=(",", ":")) != encoded:
                    return None, "IDEMPOTENCY_CONFLICT"
                return item, ""
            stamp = now()
            if command_type == "START":
                execution = db.execute("SELECT * FROM executions WHERE execution_id=?", (execution_id,)).fetchone()
                if execution and (execution["robot_id"] != robot_id or execution["deployment_id"] != deployment_id):
                    return None, "EXECUTION_CONFLICT"
                previous_start = db.execute("SELECT state FROM commands WHERE execution_id=? AND command_type='START' ORDER BY created_at DESC LIMIT 1", (execution_id,)).fetchone()
                if previous_start:
                    if execution["state"] != "FAILED" or previous_start["state"] not in {"FAILED", "REJECTED"}:
                        return None, "EXECUTION_CONFLICT"
                    # START_FAILED may be retried with a new requestId. Keep the global
                    # event cursor intact so late events can never be reassigned.
                    db.execute("UPDATE executions SET state='CREATED',last_error='',updated_at=? WHERE execution_id=?", (stamp, execution_id))
                db.execute("INSERT OR IGNORE INTO executions(execution_id,robot_id,deployment_id,state,updated_at) VALUES (?,?,?,?,?)", (execution_id, robot_id, deployment_id, "CREATED", stamp))
            else:
                execution = db.execute("SELECT * FROM executions WHERE execution_id=?", (execution_id,)).fetchone()
                if not execution or execution["robot_id"] != robot_id:
                    return None, "EXECUTION_CONFLICT"
                deployment_id = execution["deployment_id"]
            db.execute("INSERT INTO commands(command_id,request_id,robot_id,task_id,execution_id,deployment_id,command_type,payload_json,state,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", (command_id, request_id, robot_id, task_id, execution_id, deployment_id, command_type, encoded, "QUEUED", stamp, stamp))
            return self._payload(db.execute("SELECT * FROM commands WHERE command_id=?", (command_id,)).fetchone()), ""

    def command(self, command_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM commands WHERE command_id=?", (command_id,)).fetchone()
        return self._payload(row) if row else None

    def execution(self, execution_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM executions WHERE execution_id=?", (execution_id,)).fetchone()
        return dict(row) if row else None

    @staticmethod
    def _compatible(command_type, execution_state, robot_state):
        active_robot = robot_state in {"starting", "running", "paused", "manual_takeover", "returning_home", "waiting_loop"}
        if command_type == "START":
            return execution_state in {"CREATED", "DISPATCHING"} and not active_robot
        if command_type == "CANCEL":
            return execution_state in {"CREATED", "DISPATCHING", "RUNNING", "PAUSED", "MANUAL_TAKEOVER"}
        if command_type == "TAKEOVER":
            return execution_state == "RUNNING" and active_robot
        if command_type == "PAUSE":
            return execution_state == "RUNNING" and active_robot
        if command_type == "RESUME":
            return execution_state in {"PAUSED", "MANUAL_TAKEOVER"} and active_robot
        return False

    def lease_command(self, robot_id, lease_token, robot_state="idle", lease_seconds=15):
        stamp = now()
        deadline = (datetime.now(timezone.utc) + timedelta(seconds=lease_seconds)).isoformat()
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            rows = db.execute("SELECT c.*,e.state AS execution_state FROM commands c JOIN executions e ON e.execution_id=c.execution_id WHERE c.robot_id=? AND (c.state='QUEUED' OR (c.state='LEASED' AND c.lease_until<?)) ORDER BY CASE c.command_type WHEN 'CANCEL' THEN 0 WHEN 'TAKEOVER' THEN 1 WHEN 'PAUSE' THEN 2 WHEN 'RESUME' THEN 3 ELSE 4 END,c.created_at", (robot_id, stamp)).fetchall()
            row = next((item for item in rows if self._compatible(item["command_type"], item["execution_state"], robot_state)), None)
            if not row:
                return None
            db.execute("UPDATE commands SET state='LEASED',lease_token=?,lease_until=?,attempt_count=attempt_count+1,updated_at=? WHERE command_id=?", (lease_token, deadline, stamp, row["command_id"]))
            if row["command_type"] == "START" and row["execution_state"] == "CREATED":
                db.execute("UPDATE executions SET state='DISPATCHING',updated_at=? WHERE execution_id=?", (stamp, row["execution_id"]))
            elif row["command_type"] == "CANCEL" and row["execution_state"] == "CREATED":
                db.execute("UPDATE executions SET state='DISPATCHING',updated_at=? WHERE execution_id=?", (stamp, row["execution_id"]))
            row = db.execute("SELECT * FROM commands WHERE command_id=?", (row["command_id"],)).fetchone()
        return self._payload(row)

    def ack_command(self, command_id, robot_id, lease_token, status, error=""):
        with self.db() as db:
            row = db.execute("SELECT * FROM commands WHERE command_id=?", (command_id,)).fetchone()
            if not row or row["robot_id"] != robot_id:
                return None
            state = "ACKED" if status == "RECEIVED" else "REJECTED"
            if row["state"] == state:
                return state if row["lease_token"] == lease_token else None
            if row["state"] != "LEASED" or row["lease_token"] != lease_token:
                return None
            stamp = now()
            db.execute("UPDATE commands SET state=?,updated_at=?,acked_at=?,lease_until='' WHERE command_id=?", (state, stamp, stamp if state == "ACKED" else "", command_id))
            if state == "REJECTED" and row["command_type"] == "START":
                db.execute("UPDATE executions SET state='FAILED',last_error=?,updated_at=? WHERE execution_id=? AND state='DISPATCHING'", (error, stamp, row["execution_id"]))
            return state

    def expire_leases_for_smoke(self):
        with self.db() as db:
            db.execute("UPDATE commands SET lease_until='1970-01-01T00:00:00+00:00' WHERE state='LEASED'")

    @staticmethod
    def _validate_event(row, event, robot_id):
        execution_id = str(event.get("execution_id") or "")
        deployment_id = str(event.get("deployment_id") or "")
        command_id = str(event.get("command_id") or "")
        if not execution_id or not command_id or row["execution_robot_id"] != robot_id or row["execution_deployment_id"] != deployment_id:
            raise StoreConflict("EVENT_OWNERSHIP_CONFLICT", "event execution does not belong to robot/deployment")
        if (row["command_robot_id"] != robot_id or row["command_execution_id"] != execution_id
                or row["command_deployment_id"] != deployment_id or row["command_request_id"] != str(event.get("request_id") or "")):
            raise StoreConflict("EVENT_OWNERSHIP_CONFLICT", "event command does not belong to execution")
        event_type = str(event.get("event") or "")
        expected = COMMAND_RESULT_EVENTS.get(row["command_type"])
        if event_type in set(COMMAND_RESULT_EVENTS.values()) and event_type != expected:
            raise StoreConflict("EVENT_COMMAND_MISMATCH", "event does not match command type")
        if event_type in START_EVENT_TYPES and row["command_type"] != "START":
            raise StoreConflict("EVENT_COMMAND_MISMATCH", "route event must belong to START")

    def accept_events(self, robot_id, events):
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            robot = db.execute("SELECT last_event_sequence FROM robots WHERE robot_id=?", (robot_id,)).fetchone()
            if not robot:
                raise StoreConflict("ROBOT_NOT_FOUND", "heartbeat is required before events")
            accepted = int(robot[0])
            for event in events:
                sequence = int(event.get("sequence", 0))
                if sequence <= 0:
                    raise StoreConflict("INVALID_EVENT", "event sequence must be positive")
                command_id = str(event.get("command_id") or "")
                row = db.execute("SELECT e.robot_id AS execution_robot_id,e.deployment_id AS execution_deployment_id,c.robot_id AS command_robot_id,c.execution_id AS command_execution_id,c.deployment_id AS command_deployment_id,c.request_id AS command_request_id,c.command_type FROM executions e LEFT JOIN commands c ON c.command_id=? WHERE e.execution_id=?", (command_id, str(event.get("execution_id") or ""))).fetchone()
                if not row:
                    raise StoreConflict("EVENT_OWNERSHIP_CONFLICT", "event execution was not found")
                self._validate_event(row, event, robot_id)
                db.execute("INSERT OR IGNORE INTO events(robot_id,sequence,event_json,occurred_at) VALUES (?,?,?,?)", (robot_id, sequence, json.dumps(event, ensure_ascii=False), str(event.get("occurred_at") or now())))
            new_accepted = accepted
            while db.execute("SELECT 1 FROM events WHERE robot_id=? AND sequence=?", (robot_id, new_accepted + 1)).fetchone():
                new_accepted += 1
            rows = db.execute("SELECT sequence,event_json FROM events WHERE robot_id=? AND sequence>? AND sequence<=? ORDER BY sequence", (robot_id, accepted, new_accepted)).fetchall()
            for stored in rows:
                event = json.loads(stored["event_json"])
                self._apply_event(db, event, int(stored["sequence"]))
            db.execute("UPDATE robots SET last_event_sequence=?,updated_at=? WHERE robot_id=?", (new_accepted, now(), robot_id))
        return new_accepted

    def _apply_event(self, db, event, sequence):
        execution_id = str(event.get("execution_id") or "")
        command_id = str(event.get("command_id") or "")
        event_type = str(event.get("event") or "")
        command_type = ""
        if command_id:
            command = db.execute("SELECT state,command_type FROM commands WHERE command_id=?", (command_id,)).fetchone()
            command_type = str(command["command_type"]) if command else ""
            if command and command["state"] not in TERMINAL_COMMAND_STATES:
                state = None
                if event_type == COMMAND_RESULT_EVENTS.get(command["command_type"]):
                    state = "APPLIED"
                elif event_type == "command_rejected":
                    state = "REJECTED"
                elif event_type == "command_failed":
                    state = "FAILED"
                if state:
                    db.execute("UPDATE commands SET state=?,updated_at=? WHERE command_id=?", (state, now(), command_id))
        execution = db.execute("SELECT state FROM executions WHERE execution_id=?", (execution_id,)).fetchone()
        if not execution:
            return
        current = execution["state"]
        target = "FAILED" if command_type == "START" and event_type in {"command_rejected", "command_failed"} else EVENT_STATES.get(event_type)
        if target and current not in TERMINAL_EXECUTION_STATES and target in EXECUTION_TRANSITIONS.get(current, set()):
            current = target
        error = str(event.get("error_message") or event.get("error") or event.get("reason") or "")
        db.execute("UPDATE executions SET state=?,last_event_sequence=MAX(last_event_sequence,?),last_error=CASE WHEN ?='' THEN last_error ELSE ? END,updated_at=? WHERE execution_id=?", (current, sequence, error, error, now(), execution_id))

    def events(self, execution_id, after_sequence, limit):
        with self.db() as db:
            rows = db.execute("SELECT event_json FROM events WHERE json_extract(event_json,'$.execution_id')=? AND sequence>? ORDER BY sequence LIMIT ?", (execution_id, max(0, int(after_sequence)), max(1, min(int(limit), 100)))).fetchall()
        return [json.loads(row[0]) for row in rows]
