-- Side-view video of a sign whose animation is an .mp4 (front + lateral). Object key in the media
-- bucket, like signs.animation_url; null for .glb signs (the 3D avatar can be rotated instead).
ALTER TABLE signs ADD COLUMN IF NOT EXISTS lateral_animation_url varchar(255);
