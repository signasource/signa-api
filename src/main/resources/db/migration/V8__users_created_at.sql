-- Account creation instant, for signup/new-user metrics on the admin usage dashboard.
-- Existing rows predate the column: backfill them to the migration time (the earliest we can prove
-- the account existed) so the column can be NOT NULL to match the entity.
ALTER TABLE users ADD COLUMN created_at timestamp(6) with time zone;

UPDATE users SET created_at = now() WHERE created_at IS NULL;

ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;
