-- Move mutable course metadata to course_versions so that editing a draft does not
-- affect the published version. Course.name/description/etc. still serve as the
-- "current published" values read by the mobile app.

ALTER TABLE course_versions ADD COLUMN name VARCHAR(150);
ALTER TABLE course_versions ADD COLUMN description TEXT;
ALTER TABLE course_versions ADD COLUMN is_free BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE course_versions ADD COLUMN price NUMERIC(12, 2);
ALTER TABLE course_versions ADD COLUMN has_camera_recognition BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE course_versions ADD COLUMN media_format VARCHAR(10);

UPDATE course_versions cv
SET name                   = c.name,
    description            = c.description,
    is_free                = c.is_free,
    price                  = c.price,
    has_camera_recognition = c.has_camera_recognition,
    media_format           = c.media_format
FROM courses c
WHERE cv.course_id = c.id;

ALTER TABLE course_versions ALTER COLUMN name SET NOT NULL;
