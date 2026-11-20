-- Profile photo as a small data URL (data:image/...;base64,...). Only the back-office admins set it
-- today; the app validates format and size. Move to object storage if it is opened to every user.
ALTER TABLE users ADD COLUMN avatar text;
