CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    secret_hash TEXT NOT NULL,
    guardian_proof TEXT NOT NULL,
    pin_attempt_day TEXT,
    pin_failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS commands (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    action TEXT NOT NULL,
    minutes INTEGER,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    consumed_at INTEGER,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_commands_device_pending
    ON commands(device_id, consumed_at, created_at DESC);
