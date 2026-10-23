-- Audit log for content import pipeline executions (startup and admin-triggered).
-- First migration above the V1 baseline: runs on existing databases and fresh ones alike.
CREATE TABLE content_import_runs (
    id uuid NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    finished_at timestamp(6) with time zone NOT NULL,
    trigger varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    triggered_by varchar(255),
    courses_processed integer NOT NULL,
    created integer NOT NULL,
    updated integer NOT NULL,
    unchanged integer NOT NULL,
    details text,
    CONSTRAINT content_import_runs_pkey PRIMARY KEY (id)
);
