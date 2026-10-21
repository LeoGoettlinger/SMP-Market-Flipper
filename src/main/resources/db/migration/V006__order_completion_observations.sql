-- Order fills are a separate evidence stream, not completed AH sales.
CREATE TABLE IF NOT EXISTS order_completion_observations (
    order_id TEXT PRIMARY KEY,
    item_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity = 1024),
    unit_price TEXT NOT NULL,
    total_value TEXT NOT NULL,
    completed_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_completions_item_time
    ON order_completion_observations(item_id, completed_at DESC);
