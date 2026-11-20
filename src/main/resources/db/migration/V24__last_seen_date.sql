-- Last date the user made any authenticated request (app open proxy).
-- Null for users who have not opened the app since this migration.
ALTER TABLE user_stats ADD COLUMN last_seen_date date;
