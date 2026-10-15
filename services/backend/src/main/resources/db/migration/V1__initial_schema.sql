-- ResQNet coordination gateway, initial schema.
--
-- Types are written in standard SQL rather than the PostgreSQL shorthand
-- TIMESTAMPTZ so the identical migration runs against H2 in the test suite.
-- A schema that is only exercised against the production database is a schema
-- whose migrations are effectively untested.
--
-- Scope note: this schema stores what a responder needs to triage a report.
-- It deliberately does NOT store message bodies. Message text can contain a
-- survivor's location and their circumstances; keeping it off a central server
-- removes a category of liability and a category of breach.

CREATE TABLE responder_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(64)  NOT NULL UNIQUE,
    display_name        VARCHAR(128) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(32)  NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    last_login_at       TIMESTAMP WITH TIME ZONE
);

-- Roles are assigned here, by a human, and nowhere else. No client request can
-- create or elevate one.
CREATE TABLE incidents (
    id                      BIGSERIAL PRIMARY KEY,
    reference               VARCHAR(48)  NOT NULL UNIQUE,
    title                   VARCHAR(160) NOT NULL,
    category                VARCHAR(32)  NOT NULL,
    severity                INT          NOT NULL DEFAULT 0,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    advisory                BOOLEAN      NOT NULL DEFAULT TRUE,
    advisory_confidence     DOUBLE PRECISION,
    source_device_id        VARCHAR(96),
    gateway_device_id       VARCHAR(96),
    -- Precise coordinates only. A device that did not share a location stores
    -- NULL here rather than an approximate guess.
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    location_shared         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    acknowledged_at         TIMESTAMP WITH TIME ZONE,
    resolved_at             TIMESTAMP WITH TIME ZONE,
    assigned_to             BIGINT REFERENCES responder_accounts (id),
    CONSTRAINT incidents_severity_range CHECK (severity BETWEEN 0 AND 3)
);

CREATE INDEX idx_incidents_status ON incidents (status);
CREATE INDEX idx_incidents_created ON incidents (created_at DESC);
CREATE INDEX idx_incidents_severity ON incidents (severity DESC, created_at DESC);

CREATE TABLE incident_messages (
    id                  BIGSERIAL PRIMARY KEY,
    incident_id         BIGINT       NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    message_id          VARCHAR(160) NOT NULL,
    origin_device_id    VARCHAR(96)  NOT NULL,
    destination_device_id VARCHAR(96),
    state               VARCHAR(24)  NOT NULL,
    priority            VARCHAR(16)  NOT NULL,
    created_at          BIGINT       NOT NULL,
    expires_at          BIGINT       NOT NULL,
    attempts            INT          NOT NULL DEFAULT 0,
    detail              VARCHAR(255),
    simulated           BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (incident_id, message_id)
);

CREATE INDEX idx_messages_incident ON incident_messages (incident_id);

-- Append-only. A responder can add rows; nothing in the application updates or
-- deletes them.
CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    actor           VARCHAR(96)  NOT NULL,
    actor_role      VARCHAR(32)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(48),
    target_id       VARCHAR(64),
    detail          VARCHAR(512),
    request_id      VARCHAR(64)
);

CREATE INDEX idx_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_target ON audit_events (target_type, target_id);

CREATE TABLE gateway_devices (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           VARCHAR(96) NOT NULL UNIQUE,
    label               VARCHAR(128),
    last_seen_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_reported_at    TIMESTAMP WITH TIME ZONE,
    messages_reported   BIGINT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE
);

-- One row per simulation run, so a dashboard can show exactly which run it is
-- displaying and a viewer can never mistake generated data for field telemetry.
CREATE TABLE simulation_runs (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              VARCHAR(64) NOT NULL UNIQUE,
    topology            VARCHAR(32) NOT NULL,
    node_count          INT         NOT NULL,
    seed                BIGINT      NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    originated          BIGINT      NOT NULL DEFAULT 0,
    acknowledged        BIGINT      NOT NULL DEFAULT 0,
    delivery_rate       DOUBLE PRECISION NOT NULL DEFAULT 0,
    median_latency_ms   BIGINT,
    p95_latency_ms      BIGINT,
    amplification      DOUBLE PRECISION,
    partitions          INT         NOT NULL DEFAULT 0,
    report              TEXT
);

CREATE TABLE simulation_events (
    id              BIGSERIAL PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL,
    at_epoch_millis BIGINT      NOT NULL,
    round_number    INT         NOT NULL,
    node_label      VARCHAR(48) NOT NULL,
    kind            VARCHAR(48) NOT NULL,
    message_id      VARCHAR(160),
    hop_count       INT,
    reason          VARCHAR(48),
    detail          VARCHAR(512)
);

CREATE INDEX idx_simulation_events_run ON simulation_events (run_id, id);
