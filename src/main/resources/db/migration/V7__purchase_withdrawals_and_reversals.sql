-- Withdrawal period and refunds that undo a paid purchase (withdrawal by the organization, a
-- refund made in Mercado Pago, or a chargeback).
ALTER TABLE course_purchases ADD COLUMN refundable_until timestamp(6) with time zone;
ALTER TABLE course_purchases ADD COLUMN refunded_at timestamp(6) with time zone;

-- Purchases already paid with Mercado Pago get the same 10-day window from their payment date.
UPDATE course_purchases
SET refundable_until = paid_at + interval '10 days'
WHERE status = 'PAID' AND paid_at IS NOT NULL AND payment_reference LIKE 'mercadopago:%';

ALTER TABLE course_purchases DROP CONSTRAINT course_purchases_status_check;
ALTER TABLE course_purchases ADD CONSTRAINT course_purchases_status_check CHECK (
    status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED')
);
CREATE INDEX idx_course_purchases_payment_reference ON course_purchases (payment_reference);

ALTER TABLE purchase_refunds ADD COLUMN note character varying(500);
ALTER TABLE purchase_refunds ADD COLUMN requested_by_id uuid;
ALTER TABLE purchase_refunds ADD CONSTRAINT fk_purchase_refunds_requested_by
    FOREIGN KEY (requested_by_id) REFERENCES users (id);

ALTER TABLE purchase_refunds DROP CONSTRAINT purchase_refunds_reason_check;
ALTER TABLE purchase_refunds ADD CONSTRAINT purchase_refunds_reason_check CHECK (
    reason IN (
        'PURCHASE_CANCELLED',
        'ALREADY_PAID',
        'AMOUNT_MISMATCH',
        'BUYER_REQUESTED',
        'EXTERNAL_REFUND',
        'CHARGEBACK'
    )
);
