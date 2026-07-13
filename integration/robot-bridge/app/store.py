import json
import os
import shutil
import sqlite3
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path


EVENT_STATES = {
    "route_started": "RUNNING", "route_paused": "PAUSED", "route_resumed": "RUNNING",
    "manual_takeover": "MANUAL_TAKEOVER", "route_finished": "COMPLETED",
    "route_failed": "FAILED", "route_canceled": "CANCELLED",
}


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


class Store:
    def __init__(self, path: str):
        self.path = Path(path).expanduser()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.deployments_root = self.path.parent / "storage" / "deployments"
        self.deployments_root.mkdir(parents=True, exist_ok=True)
        with self.db() as db:
            db.executescript("""
              CREATE TABLE IF NOT EXISTS deployment_mappings (deployment_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, state TEXT NOT NULL, result_json TEXT NOT NULL, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS execution_mappings (execution_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, deployment_id TEXT NOT NULL, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS event_cursors (execution_id TEXT PRIMARY KEY, sequence INTEGER NOT NULL);
              CREATE TABLE IF NOT EXISTS robots (robot_id TEXT PRIMARY KEY, boot_id TEXT NOT NULL DEFAULT '', state TEXT NOT NULL DEFAULT 'offline', status_json TEXT NOT NULL DEFAULT '{}', last_seen TEXT NOT NULL, last_event_sequence INTEGER NOT NULL DEFAULT 0, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS executions (execution_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, deployment_id TEXT NOT NULL, state TEXT NOT NULL, last_event_sequence INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '', updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS commands (command_id TEXT PRIMARY KEY, request_id TEXT NOT NULL UNIQUE, robot_id TEXT NOT NULL, task_id TEXT NOT NULL DEFAULT '', execution_id TEXT NOT NULL, deployment_id TEXT NOT NULL DEFAULT '', command_type TEXT NOT NULL, payload_json TEXT NOT NULL, state TEXT NOT NULL, lease_token TEXT NOT NULL DEFAULT '', lease_until TEXT NOT NULL DEFAULT '', attempt_count INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, acked_at TEXT NOT NULL DEFAULT '');
              CREATE TABLE IF NOT EXISTS events (robot_id TEXT NOT NULL, sequence INTEGER NOT NULL, event_json TEXT NOT NULL, occurred_at TEXT NOT NULL, PRIMARY KEY(robot_id, sequence));
            """)
            for row in db.execute("SELECT execution_id, robot_id, deployment_id, updated_at FROM execution_mappings"):
                db.execute("INSERT OR IGNORE INTO executions(execution_id,robot_id,deployment_id,state,updated_at) VALUES (?,?,?,?,?)", (row[0], row[1], row[2], "CREATED", row[3]))

    def db(self):
        db = sqlite3.connect(self.path, timeout=5)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA busy_timeout=5000")
        return db

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

    def cache_deployment(self, deployment_id, robot_id, manifest, route, yaml_bytes, pgm):
        if Path(deployment_id).name != deployment_id or not deployment_id or ".." in Path(deployment_id).parts:
            raise ValueError("invalid deployment id")
        target = self.deployments_root / deployment_id
        staging = Path(tempfile.mkdtemp(prefix=f"{deployment_id}-", dir=self.deployments_root))
        backup = None
        installed = False
        try:
            (staging / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True), encoding="utf-8")
            (staging / "route.json").write_bytes(route)
            (staging / manifest["yamlName"]).write_bytes(yaml_bytes)
            (staging / manifest["pgmName"]).write_bytes(pgm)
            if target.exists():
                backup = self.deployments_root / f".{deployment_id}.previous"
                shutil.rmtree(backup, ignore_errors=True)
                os.replace(target, backup)
            os.replace(staging, target)
            installed = True
            self.save_deployment(deployment_id, robot_id, "READY_FOR_ROBOT", manifest, now())
        finally:
            shutil.rmtree(staging, ignore_errors=True)
            if backup:
                if installed:
                    shutil.rmtree(backup, ignore_errors=True)
                elif not target.exists():
                    os.replace(backup, target)

    def save_deployment(self, deployment_id, robot_id, state, result, stamp):
        with self.db() as db:
            db.execute("INSERT INTO deployment_mappings VALUES (?, ?, ?, ?, ?) ON CONFLICT(deployment_id) DO UPDATE SET robot_id=excluded.robot_id,state=excluded.state,result_json=excluded.result_json,updated_at=excluded.updated_at", (deployment_id, robot_id, state, json.dumps(result, ensure_ascii=False), stamp))

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
        encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        with self.db() as db:
            existing = db.execute("SELECT * FROM commands WHERE request_id=?", (request_id,)).fetchone()
            if existing:
                item = self._payload(existing)
                if item["robot_id"] != robot_id or item["command_type"] != command_type or json.dumps(item["payload"], ensure_ascii=False, sort_keys=True, separators=(",", ":")) != encoded:
                    return None, True
                return item, False
            stamp = now()
            db.execute("INSERT INTO commands(command_id,request_id,robot_id,task_id,execution_id,deployment_id,command_type,payload_json,state,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", (command_id, request_id, robot_id, task_id, execution_id, deployment_id, command_type, encoded, "QUEUED", stamp, stamp))
            if command_type == "START":
                db.execute("INSERT INTO executions(execution_id,robot_id,deployment_id,state,updated_at) VALUES (?,?,?,?,?) ON CONFLICT(execution_id) DO NOTHING", (execution_id, robot_id, deployment_id, "CREATED", stamp))
            return self._payload(db.execute("SELECT * FROM commands WHERE command_id=?", (command_id,)).fetchone()), False

    def execution(self, execution_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM executions WHERE execution_id=?", (execution_id,)).fetchone()
        return dict(row) if row else None

    def lease_command(self, robot_id, lease_token, robot_state="idle", lease_seconds=15):
        stamp = now()
        deadline = (datetime.now(timezone.utc) + timedelta(seconds=lease_seconds)).isoformat()
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM commands WHERE robot_id=? AND (state='QUEUED' OR (state='LEASED' AND lease_until<?)) ORDER BY created_at LIMIT 1", (robot_id, stamp)).fetchone()
            if not row:
                return None
            if row["command_type"] == "START" and robot_state in {"starting", "running", "paused", "manual_takeover", "returning_home", "waiting_loop"}:
                return None
            db.execute("UPDATE commands SET state='LEASED',lease_token=?,lease_until=?,attempt_count=attempt_count+1,updated_at=? WHERE command_id=?", (lease_token, deadline, stamp, row["command_id"]))
            row = db.execute("SELECT * FROM commands WHERE command_id=?", (row["command_id"],)).fetchone()
        return self._payload(row)

    def ack_command(self, command_id, robot_id, lease_token, status, error=""):
        with self.db() as db:
            row = db.execute("SELECT * FROM commands WHERE command_id=?", (command_id,)).fetchone()
            if not row or row["robot_id"] != robot_id or row["lease_token"] != lease_token:
                return None
            state = "ACKED" if status == "RECEIVED" else "REJECTED"
            db.execute("UPDATE commands SET state=?,updated_at=?,acked_at=?,lease_until='',lease_token='',payload_json=payload_json WHERE command_id=?", (state, now(), now() if state == "ACKED" else "", command_id))
            if state == "REJECTED":
                db.execute("UPDATE executions SET last_error=?,updated_at=? WHERE execution_id=?", (error, now(), row["execution_id"]))
            return state

    def expire_leases_for_smoke(self):
        with self.db() as db:
            db.execute("UPDATE commands SET lease_until='1970-01-01T00:00:00+00:00' WHERE state='LEASED'")

    def accept_events(self, robot_id, events):
        ordered = sorted(events, key=lambda item: int(item.get("sequence", 0)))
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT last_event_sequence FROM robots WHERE robot_id=?", (robot_id,)).fetchone()
            accepted = int(row[0]) if row else 0
            for event in ordered:
                sequence = int(event.get("sequence", 0))
                if sequence <= 0:
                    continue
                db.execute("INSERT OR IGNORE INTO events(robot_id,sequence,event_json,occurred_at) VALUES (?,?,?,?)", (robot_id, sequence, json.dumps(event, ensure_ascii=False), str(event.get("occurred_at") or now())))
            while db.execute("SELECT 1 FROM events WHERE robot_id=? AND sequence=?", (robot_id, accepted + 1)).fetchone():
                accepted += 1
            db.execute("UPDATE robots SET last_event_sequence=?,updated_at=? WHERE robot_id=?", (accepted, now(), robot_id))
            for event in ordered:
                state = EVENT_STATES.get(str(event.get("event") or ""))
                execution_id = str(event.get("execution_id") or "")
                if state and execution_id:
                    db.execute("UPDATE executions SET state=?,last_event_sequence=MAX(last_event_sequence,?),last_error=?,updated_at=? WHERE execution_id=?", (state, int(event.get("sequence", 0)), str(event.get("error") or event.get("reason") or ""), now(), execution_id))
        return accepted

    def events(self, execution_id, after_sequence, limit):
        with self.db() as db:
            rows = db.execute("SELECT event_json FROM events WHERE json_extract(event_json,'$.execution_id')=? AND sequence>? ORDER BY sequence LIMIT ?", (execution_id, max(0, int(after_sequence)), max(1, min(int(limit), 100)))).fetchall()
        return [json.loads(row[0]) for row in rows]
