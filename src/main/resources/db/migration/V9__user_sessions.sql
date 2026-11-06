-- App-usage sessions reported by the client (foreground→background), for the "sesiones por usuario"
-- and "duración promedio de sesión" metrics on the admin usage dashboard.
CREATE TABLE user_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    duration_seconds integer NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT user_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT user_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_sessions_started_at ON user_sessions (started_at);
CREATE INDEX idx_user_sessions_user_id ON user_sessions (user_id);
