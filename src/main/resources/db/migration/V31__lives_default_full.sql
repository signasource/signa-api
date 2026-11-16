-- Lives used to be created as NULL (read as "0 lives" by clients). A full bar is the real
-- starting state: backfill existing rows and make the column default match the entity.
UPDATE user_stats SET current_lives = 5 WHERE current_lives IS NULL AND lives_mode = 'LIMITED';
ALTER TABLE user_stats ALTER COLUMN current_lives SET DEFAULT 5;
