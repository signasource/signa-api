-- When the sign's animation file was last uploaded from the admin panel. Clients append it to the
-- media URL as a cache-buster (?v=<epoch>), because a re-upload keeps the same object key and the
-- app caches .glb files on disk by URL. Null = never uploaded from the panel (imported default).
ALTER TABLE signs ADD COLUMN IF NOT EXISTS animation_updated_at timestamp with time zone;
