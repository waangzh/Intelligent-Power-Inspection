CREATE INDEX idx_data_records_category_updated_at
    ON data_records(category, updated_at);

CREATE INDEX idx_data_records_category_created_at
    ON data_records(category, created_at);
