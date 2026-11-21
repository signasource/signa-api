-- A course whose every purchase was refunded is no longer contracted. Courses contracted before
-- purchases existed have no purchase at all and are kept.
DELETE FROM organization_courses oc
WHERE EXISTS (
        SELECT 1 FROM course_purchases p
        WHERE p.organization_id = oc.organization_id
          AND p.course_id = oc.course_id
          AND p.status = 'REFUNDED')
  AND NOT EXISTS (
        SELECT 1 FROM course_purchases p
        WHERE p.organization_id = oc.organization_id
          AND p.course_id = oc.course_id
          AND p.status = 'PAID');

-- When an admin last nudged a participant from the panel (manual reminders have a cooldown).
ALTER TABLE organization_members ADD COLUMN last_reminder_at timestamp(6) with time zone;
