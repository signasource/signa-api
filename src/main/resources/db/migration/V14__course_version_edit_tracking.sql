ALTER TABLE course_versions ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE course_versions ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE course_versions ADD COLUMN based_on_version VARCHAR(50);
