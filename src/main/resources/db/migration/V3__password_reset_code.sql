-- Password reset moves from a UUID link to a 6-digit code stored as a BCrypt hash, with a cap on
-- failed attempts per code.
ALTER TABLE tokens ADD COLUMN attempts integer NOT NULL DEFAULT 0;

-- Pending UUID reset tokens can no longer be redeemed.
DELETE FROM tokens WHERE type = 'PASSWORD_RESET';
