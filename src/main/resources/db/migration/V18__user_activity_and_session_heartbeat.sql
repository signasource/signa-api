-- "Active user" = any authenticated activity in the app (not only exercise attempts). One row per
-- user per throttle window is written by the auth filter; history is backfilled from attempts/sessions.
CREATE TABLE user_activity (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT user_activity_pkey PRIMARY KEY (id),
    CONSTRAINT user_activity_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_activity_occurred_at ON user_activity (occurred_at);
CREATE INDEX idx_user_activity_user_id ON user_activity (user_id);

INSERT INTO user_activity (id, user_id, occurred_at)
SELECT gen_random_uuid(), user_id, attempted_at FROM lesson_block_attempts;

INSERT INTO user_activity (id, user_id, occurred_at)
SELECT gen_random_uuid(), user_id, started_at FROM user_sessions;

-- Sessions are now upserted by a client-generated id (heartbeat), so a session is recorded while the
-- app is still open instead of only when it goes to the background.
ALTER TABLE user_sessions ADD COLUMN client_session_id uuid;
CREATE UNIQUE INDEX uq_user_sessions_user_client
    ON user_sessions (user_id, client_session_id) WHERE client_session_id IS NOT NULL;
