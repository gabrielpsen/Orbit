CREATE TABLE scheduler_fence (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    epoch BIGINT NOT NULL CHECK (epoch >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO scheduler_fence (singleton_id, epoch)
VALUES (1, 0)
ON CONFLICT (singleton_id) DO NOTHING;
