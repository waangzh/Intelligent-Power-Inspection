-- Harden core domain constraints for MySQL and H2 (MySQL mode).
-- Validate legacy rows before any non-transactional ALTER TABLE statements run.
-- Keeping the guard table after a failed check makes the migration safe to retry
-- once the conflicting data has been corrected.
CREATE TABLE IF NOT EXISTS domain_constraint_preflight_invalid_existing_data (
  id INT NOT NULL,
  CONSTRAINT domain_constraint_preflight_failed PRIMARY KEY (id)
);

DELETE FROM domain_constraint_preflight_invalid_existing_data;
INSERT INTO domain_constraint_preflight_invalid_existing_data (id) VALUES (1);

INSERT INTO domain_constraint_preflight_invalid_existing_data (id)
SELECT 1
WHERE EXISTS (SELECT 1 FROM robots WHERE status NOT IN ('ONLINE', 'OFFLINE', 'BUSY'))
   OR EXISTS (SELECT 1 FROM inspection_tasks WHERE status NOT IN ('CREATED', 'DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER', 'COMPLETED', 'CANCELLED'))
   OR EXISTS (SELECT 1 FROM alarms WHERE severity NOT IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
   OR EXISTS (SELECT 1 FROM work_orders WHERE status NOT IN ('PENDING', 'PROCESSING', 'REVIEW', 'CLOSED', 'CANCELLED'))
   OR EXISTS (SELECT 1 FROM work_orders WHERE source NOT IN ('MANUAL', 'AUTO', 'AGENT'))
   OR EXISTS (SELECT 1 FROM work_orders WHERE priority NOT IN ('URGENT', 'HIGH', 'MEDIUM', 'LOW'))
   OR EXISTS (SELECT 1 FROM routes r LEFT JOIN sites s ON s.id = r.site_id WHERE s.id IS NULL)
   OR EXISTS (SELECT 1 FROM robots r LEFT JOIN sites s ON s.id = r.site_id WHERE r.site_id IS NOT NULL AND s.id IS NULL)
   OR EXISTS (SELECT 1 FROM robot_telemetry t LEFT JOIN robots r ON r.id = t.robot_id WHERE r.id IS NULL)
   OR EXISTS (SELECT 1 FROM inspection_tasks t LEFT JOIN sites s ON s.id = t.site_id WHERE t.site_id IS NOT NULL AND s.id IS NULL)
   OR EXISTS (SELECT 1 FROM inspection_tasks t LEFT JOIN routes r ON r.id = t.route_id WHERE r.id IS NULL)
   OR EXISTS (SELECT 1 FROM inspection_tasks t LEFT JOIN robots r ON r.id = t.robot_id WHERE r.id IS NULL)
   OR EXISTS (SELECT 1 FROM task_events e LEFT JOIN inspection_tasks t ON t.id = e.task_id WHERE t.id IS NULL)
   OR EXISTS (SELECT 1 FROM alarms a LEFT JOIN inspection_tasks t ON t.id = a.task_id WHERE a.task_id IS NOT NULL AND t.id IS NULL)
   OR EXISTS (SELECT 1 FROM work_orders w LEFT JOIN sites s ON s.id = w.site_id WHERE w.site_id IS NOT NULL AND s.id IS NULL)
   OR EXISTS (SELECT 1 FROM work_orders w LEFT JOIN inspection_tasks t ON t.id = w.task_id WHERE w.task_id IS NOT NULL AND t.id IS NULL)
   OR EXISTS (SELECT 1 FROM work_orders w LEFT JOIN alarms a ON a.id = w.alarm_id WHERE w.alarm_id IS NOT NULL AND a.id IS NULL)
   OR EXISTS (SELECT 1 FROM inspection_records r LEFT JOIN inspection_tasks t ON t.id = r.task_id WHERE r.task_id IS NOT NULL AND t.id IS NULL)
   OR EXISTS (SELECT 1 FROM inspection_records r LEFT JOIN sites s ON s.id = r.site_id WHERE r.site_id IS NOT NULL AND s.id IS NULL)
   OR EXISTS (
     SELECT robot_id
     FROM inspection_tasks
     WHERE status IN ('DISPATCHED', 'RUNNING', 'PAUSED', 'MANUAL_TAKEOVER')
     GROUP BY robot_id
     HAVING COUNT(*) > 1
   );

DROP TABLE domain_constraint_preflight_invalid_existing_data;


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
