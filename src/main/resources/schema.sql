CREATE TABLE IF NOT EXISTS workspaces (
 id VARCHAR(36) PRIMARY KEY, token_hash CHAR(64) UNIQUE NOT NULL, csrf CHAR(48) NOT NULL, expires BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS resources (
 id VARCHAR(36) PRIMARY KEY, owner VARCHAR(36) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 name VARCHAR(100) NOT NULL, capacity INT NOT NULL CHECK(capacity BETWEEN 1 AND 20), version BIGINT NOT NULL DEFAULT 1,
 created BIGINT NOT NULL, updated BIGINT NOT NULL, demo_tag VARCHAR(32), UNIQUE(owner,demo_tag)
);
CREATE INDEX IF NOT EXISTS resources_owner ON resources(owner);
CREATE TABLE IF NOT EXISTS reservations (
 id VARCHAR(36) PRIMARY KEY, owner VARCHAR(36) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 resource_id VARCHAR(36) NOT NULL REFERENCES resources(id) ON DELETE CASCADE, title VARCHAR(100) NOT NULL,
 units INT NOT NULL CHECK(units BETWEEN 1 AND 20), start_ms BIGINT NOT NULL, end_ms BIGINT NOT NULL CHECK(end_ms>start_ms),
 status VARCHAR(16) NOT NULL CHECK(status IN ('HELD','CONFIRMED','CANCELLED')), hold_until BIGINT,
 version BIGINT NOT NULL DEFAULT 1, created BIGINT NOT NULL, updated BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS reservation_overlap ON reservations(resource_id,start_ms,end_ms);
CREATE INDEX IF NOT EXISTS reservations_owner ON reservations(owner,created);
CREATE TABLE IF NOT EXISTS idempotency (
 owner VARCHAR(36) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, request_key VARCHAR(80) NOT NULL,
 digest CHAR(64) NOT NULL, reservation_id VARCHAR(36) REFERENCES reservations(id) ON DELETE SET NULL, PRIMARY KEY(owner,request_key)
);
