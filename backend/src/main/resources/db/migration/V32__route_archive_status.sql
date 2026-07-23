-- Preserve route history by treating referenced route deletion as archival.
UPDATE routes
SET status = 'ACTIVE'
WHERE status IS NULL OR status <> 'ARCHIVED';
