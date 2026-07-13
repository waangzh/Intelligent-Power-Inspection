import json
import sqlite3
from pathlib import Path


class Store:
    def __init__(self, path: str):
        self.path = Path(path).expanduser()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.db() as db:
            db.executescript("""
              CREATE TABLE IF NOT EXISTS deployment_mappings (deployment_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, state TEXT NOT NULL, result_json TEXT NOT NULL, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS execution_mappings (execution_id TEXT PRIMARY KEY, robot_id TEXT NOT NULL, deployment_id TEXT NOT NULL, updated_at TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS event_cursors (execution_id TEXT PRIMARY KEY, sequence INTEGER NOT NULL);
            """)

    def db(self):
        db = sqlite3.connect(self.path, timeout=5)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA busy_timeout=5000")
        return db

    def deployment(self, deployment_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM deployment_mappings WHERE deployment_id=?", (deployment_id,)).fetchone()
        return {**dict(row), "result": json.loads(row["result_json"])} if row else None

    def save_deployment(self, deployment_id, robot_id, state, result, now):
        with self.db() as db:
            db.execute("INSERT INTO deployment_mappings VALUES (?, ?, ?, ?, ?) ON CONFLICT(deployment_id) DO UPDATE SET state=excluded.state,result_json=excluded.result_json,updated_at=excluded.updated_at", (deployment_id, robot_id, state, json.dumps(result), now))

    def save_execution(self, execution_id, robot_id, deployment_id, now):
        with self.db() as db:
            db.execute("INSERT INTO execution_mappings VALUES (?, ?, ?, ?) ON CONFLICT(execution_id) DO UPDATE SET robot_id=excluded.robot_id,deployment_id=excluded.deployment_id,updated_at=excluded.updated_at", (execution_id, robot_id, deployment_id, now))

    def execution(self, execution_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM execution_mappings WHERE execution_id=?", (execution_id,)).fetchone()
        return dict(row) if row else None

    def cursor(self, execution_id):
        with self.db() as db:
            row = db.execute("SELECT sequence FROM event_cursors WHERE execution_id=?", (execution_id,)).fetchone()
        return row["sequence"] if row else 0

    def save_cursor(self, execution_id, sequence):
        with self.db() as db:
            db.execute("INSERT INTO event_cursors VALUES (?, ?) ON CONFLICT(execution_id) DO UPDATE SET sequence=MAX(sequence, excluded.sequence)", (execution_id, sequence))
