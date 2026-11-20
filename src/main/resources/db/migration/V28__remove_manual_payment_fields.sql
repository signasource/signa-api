ALTER TABLE course_purchases
    DROP COLUMN IF EXISTS payment_method,
    DROP COLUMN IF EXISTS manual_payment_date,
    DROP COLUMN IF EXISTS confirmed_by_name,
    DROP COLUMN IF EXISTS confirmed_at;
