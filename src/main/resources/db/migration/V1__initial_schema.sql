CREATE TABLE tenant_quotas (
    tenant_id VARCHAR(120) PRIMARY KEY,
    max_concurrent_jobs INTEGER NOT NULL DEFAULT 10 CHECK (max_concurrent_jobs > 0),
    weight INTEGER NOT NULL DEFAULT 1 CHECK (weight > 0)
);

CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(120) NOT NULL REFERENCES tenant_quotas(tenant_id),
    task_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    priority INTEGER NOT NULL CHECK (priority BETWEEN 0 AND 9),
    status VARCHAR(20) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts > 0),
    idempotency_key VARCHAR(180),
    worker_id VARCHAR(160),
    lease_expires_at TIMESTAMPTZ,
    lease_token UUID,
    last_error TEXT,
    output_json TEXT,
    CONSTRAINT uq_jobs_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_jobs_dispatch ON jobs(status, priority DESC, scheduled_at ASC, created_at ASC);
CREATE INDEX idx_jobs_due_scan ON jobs(status, scheduled_at ASC);
CREATE INDEX idx_jobs_tenant_status ON jobs(tenant_id, status);
CREATE INDEX idx_jobs_worker_lease ON jobs(worker_id, lease_expires_at) WHERE status = 'RUNNING';

CREATE TABLE workers (
    worker_id VARCHAR(160) PRIMARY KEY,
    hostname VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_concurrency INTEGER NOT NULL DEFAULT 1 CHECK (max_concurrency > 0),
    active_jobs INTEGER NOT NULL DEFAULT 0 CHECK (active_jobs >= 0),
    registered_at TIMESTAMPTZ NOT NULL,
    last_heartbeat TIMESTAMPTZ NOT NULL,
    completed_jobs BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_workers_heartbeat ON workers(status, last_heartbeat);
