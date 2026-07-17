ALTER TABLE work_orders ADD COLUMN site_id VARCHAR(64);
CREATE INDEX idx_work_orders_site_status ON work_orders(site_id, status);
