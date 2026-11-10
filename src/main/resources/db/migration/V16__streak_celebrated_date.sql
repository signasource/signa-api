-- Streak day the user was already shown the "streak day added" celebration for. A day is pending
-- while last_streak_date is today and this column is not. Nullable: no backfill needed, because
-- only today's streak day is ever pending.
ALTER TABLE user_stats ADD COLUMN streak_celebrated_date date;
