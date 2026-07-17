-- Harden core domain constraints for MySQL and H2 (MySQL mode).

-- Status / enum CHECKs.
ALTER TABLE robots ADD CONSTRAINT chk_robots_status
  CHECK (status IN ('ONLINE', 'OFFLINE', 'BUSY'));

ALTER TABLE inspection_tasks ADD CONSTRAINT chk_inspection_tasks_status
  CHECK (status IN ('CREATED', 'DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER', 'COMPLETED', 'CANCELLED'));

ALTER TABLE alarms ADD CONSTRAINT chk_alarms_severity
  CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));

ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_status
  CHECK (status IN ('PENDING', 'PROCESSING', 'REVIEW', 'CLOSED', 'CANCELLED'));

ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_source
  CHECK (source IN ('MANUAL', 'AUTO', 'AGENT'));

ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_priority
  CHECK (priority IN ('URGENT', 'HIGH', 'MEDIUM', 'LOW'));

-- Cross-table foreign keys.
ALTER TABLE routes ADD CONSTRAINT fk_routes_site
  FOREIGN KEY (site_id) REFERENCES sites(id);

ALTER TABLE robots ADD CONSTRAINT fk_robots_site
  FOREIGN KEY (site_id) REFERENCES sites(id);

ALTER TABLE robot_telemetry ADD CONSTRAINT fk_robot_telemetry_robot
  FOREIGN KEY (robot_id) REFERENCES robots(id) ON DELETE CASCADE;

ALTER TABLE inspection_tasks ADD CONSTRAINT fk_inspection_tasks_site
  FOREIGN KEY (site_id) REFERENCES sites(id);

ALTER TABLE inspection_tasks ADD CONSTRAINT fk_inspection_tasks_route
  FOREIGN KEY (route_id) REFERENCES routes(id);

ALTER TABLE inspection_tasks ADD CONSTRAINT fk_inspection_tasks_robot
  FOREIGN KEY (robot_id) REFERENCES robots(id);

ALTER TABLE task_events ADD CONSTRAINT fk_task_events_task
  FOREIGN KEY (task_id) REFERENCES inspection_tasks(id) ON DELETE CASCADE;

ALTER TABLE alarms ADD CONSTRAINT fk_alarms_task
  FOREIGN KEY (task_id) REFERENCES inspection_tasks(id) ON DELETE SET NULL;

ALTER TABLE work_orders ADD CONSTRAINT fk_work_orders_site
  FOREIGN KEY (site_id) REFERENCES sites(id);

ALTER TABLE work_orders ADD CONSTRAINT fk_work_orders_task
  FOREIGN KEY (task_id) REFERENCES inspection_tasks(id) ON DELETE SET NULL;

ALTER TABLE work_orders ADD CONSTRAINT fk_work_orders_alarm
  FOREIGN KEY (alarm_id) REFERENCES alarms(id);

ALTER TABLE inspection_records ADD CONSTRAINT fk_inspection_records_task
  FOREIGN KEY (task_id) REFERENCES inspection_tasks(id) ON DELETE SET NULL;

ALTER TABLE inspection_records ADD CONSTRAINT fk_inspection_records_site
  FOREIGN KEY (site_id) REFERENCES sites(id);

-- One active task per robot: non-null only while task is active; UNIQUE allows many NULLs.
ALTER TABLE inspection_tasks ADD COLUMN active_robot_key VARCHAR(64);

UPDATE inspection_tasks
SET active_robot_key = robot_id
WHERE status IN ('DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER');

CREATE UNIQUE INDEX uq_inspection_tasks_active_robot ON inspection_tasks(active_robot_key);
