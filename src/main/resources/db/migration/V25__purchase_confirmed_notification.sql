-- Add PURCHASE_CONFIRMED to the notification_templates code check constraint.
ALTER TABLE notification_templates
    DROP CONSTRAINT notification_templates_code_check;

ALTER TABLE notification_templates
    ADD CONSTRAINT notification_templates_code_check CHECK (
        code::text = ANY (ARRAY[
            'DAILY_REMINDER',
            'COURSE_COMPLETED',
            'STREAK_REMINDER',
            'NEW_COURSE_AVAILABLE',
            'GLOBAL_ANNOUNCEMENT',
            'FRIEND_REQUEST_RECEIVED',
            'FRIEND_REQUEST_ACCEPTED',
            'FRIEND_EVENT_LIKED',
            'SIGN_REPORT_RESOLVED',
            'PURCHASE_CONFIRMED'
        ]::text[])
    );
