-- Track when a gem or course order's payment was given back (refund or chargeback), so the usage
-- dashboard can report end-user refunds inside a time window (like B2B purchase_refunds.refunded_at).
-- Backfill existing REFUNDED orders with their created_at as a best-effort timestamp.

ALTER TABLE public.gem_orders
    ADD COLUMN refunded_at timestamp(6) with time zone;

ALTER TABLE public.course_orders
    ADD COLUMN refunded_at timestamp(6) with time zone;

UPDATE public.gem_orders SET refunded_at = created_at WHERE status = 'REFUNDED' AND refunded_at IS NULL;
UPDATE public.course_orders SET refunded_at = created_at WHERE status = 'REFUNDED' AND refunded_at IS NULL;
