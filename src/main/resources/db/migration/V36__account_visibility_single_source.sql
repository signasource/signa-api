-- Account visibility lived in two columns: the settings endpoint wrote user_settings.account_visibility
-- while ranking, search and public profiles read users.account_visibility, so switching to private
-- never took effect. users.account_visibility becomes the only source; the user's chosen value (the
-- one in user_settings) is carried over first. Erased accounts have no settings row and stay PRIVATE.
UPDATE users u
SET account_visibility = s.account_visibility
FROM user_settings s
WHERE s.user_id = u.id
  AND u.enabled = true;

ALTER TABLE user_settings DROP CONSTRAINT IF EXISTS user_settings_account_visibility_check;
ALTER TABLE user_settings DROP COLUMN account_visibility;
