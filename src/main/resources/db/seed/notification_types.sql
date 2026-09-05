-- Notification Templates Seed
INSERT INTO notification_templates (code, default_title, default_body, scope, schedulable, enabled)
VALUES
    ('DAILY_REMINDER', 'Time to practice', 'Keep your learning streak alive! Open SignaSource to continue.', 'INDIVIDUAL', true, true),
    ('COURSE_COMPLETED', 'Congratulations!', 'You have completed the course. Great job!', 'INDIVIDUAL', false, true),
    ('STREAK_REMINDER', 'Streak Warning', 'Your learning streak is ending soon. Practice now to keep it going!', 'INDIVIDUAL', true, true),
    ('NEW_COURSE_AVAILABLE', 'New course available', 'Check out our latest course on sign language.', 'GLOBAL', false, true),
    ('GLOBAL_ANNOUNCEMENT', 'Important announcement', 'We have an important update for you.', 'GLOBAL', false, true),
    ('FRIEND_REQUEST_RECEIVED', 'Nueva solicitud de amistad', '{{friend}} te envió una solicitud de amistad.', 'INDIVIDUAL', false, true),
    ('FRIEND_REQUEST_ACCEPTED', 'Solicitud aceptada', '{{friend}} aceptó tu solicitud de amistad.', 'INDIVIDUAL', false, true),
    ('FRIEND_EVENT_LIKED', 'Le gustó tu actividad', 'A {{friend}} le gustó tu actividad.', 'INDIVIDUAL', false, true)
ON CONFLICT (code) DO UPDATE SET
    default_title = EXCLUDED.default_title,
    default_body = EXCLUDED.default_body,
    scope = EXCLUDED.scope,
    schedulable = EXCLUDED.schedulable,
    enabled = EXCLUDED.enabled;
