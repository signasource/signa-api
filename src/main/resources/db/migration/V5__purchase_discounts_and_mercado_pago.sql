-- Volume discount of each purchase (existing purchases were sold at list price) and the
-- Mercado Pago Checkout Pro preference that pays it.
ALTER TABLE course_purchases ADD COLUMN list_price_per_seat numeric(12, 2);
UPDATE course_purchases SET list_price_per_seat = price_per_seat;
ALTER TABLE course_purchases ALTER COLUMN list_price_per_seat SET NOT NULL;

ALTER TABLE course_purchases ADD COLUMN discount_percent integer NOT NULL DEFAULT 0;
ALTER TABLE course_purchases ALTER COLUMN discount_percent DROP DEFAULT;

ALTER TABLE course_purchases ADD COLUMN mercado_pago_preference_id character varying(100);
