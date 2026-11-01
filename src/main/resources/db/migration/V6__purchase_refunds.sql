-- Approved Mercado Pago payments that could not be applied (purchase cancelled, already paid,
-- or another amount) and were refunded in full. One row per payment keeps refunds idempotent.
CREATE TABLE purchase_refunds (
    id uuid NOT NULL,
    purchase_id uuid NOT NULL,
    payment_id character varying(30) NOT NULL,
    amount numeric(14, 2) NOT NULL,
    currency character varying(3) NOT NULL,
    reason character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    mercado_pago_refund_id character varying(50),
    created_at timestamp(6) with time zone NOT NULL,
    refunded_at timestamp(6) with time zone,
    CONSTRAINT purchase_refunds_pkey PRIMARY KEY (id),
    CONSTRAINT uk_purchase_refunds_payment UNIQUE (payment_id),
    CONSTRAINT purchase_refunds_reason_check CHECK (
        reason IN ('PURCHASE_CANCELLED', 'ALREADY_PAID', 'AMOUNT_MISMATCH')
    ),
    CONSTRAINT purchase_refunds_status_check CHECK (status IN ('PENDING', 'REFUNDED')),
    CONSTRAINT fk_purchase_refunds_purchase FOREIGN KEY (purchase_id)
        REFERENCES course_purchases (id)
);

CREATE INDEX idx_purchase_refunds_purchase ON purchase_refunds (purchase_id);
