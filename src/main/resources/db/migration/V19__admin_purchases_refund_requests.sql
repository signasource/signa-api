-- Extend course_purchases with manual-payment details so the admin can record
-- the method, the reference and the date of a payment confirmed by hand, and
-- who confirmed it (kept even if the admin is later deleted).
ALTER TABLE public.course_purchases
    ADD COLUMN payment_method    character varying(20),
    ADD COLUMN manual_payment_date date,
    ADD COLUMN confirmed_by_name character varying(200),
    ADD COLUMN confirmed_at      timestamp(6) with time zone;

-- User / org initiated refund requests (not the same as purchase_refunds, which
-- track actual MP payment reversals). Linked polymorphically: exactly one of the
-- purchase_id columns is non-null depending on purchase_type.
CREATE TABLE public.refund_requests (
    id               uuid                         NOT NULL,
    purchase_type    character varying(10)        NOT NULL,  -- SEATS | GEMS | COURSE
    purchase_id      uuid                         NOT NULL,
    requested_by_id  uuid                         NOT NULL,
    reason           character varying(30)        NOT NULL,
    channel          character varying(50)        NOT NULL,
    message          character varying(2000)      NOT NULL,
    note             character varying(2000),
    status           character varying(20)        NOT NULL DEFAULT 'PENDING',
    resolved_by_name character varying(200),
    resolved_at      timestamp(6) with time zone,
    created_at       timestamp(6) with time zone  NOT NULL,
    CONSTRAINT refund_requests_pkey PRIMARY KEY (id),
    CONSTRAINT refund_requests_user_fk FOREIGN KEY (requested_by_id) REFERENCES public.users (id)
);

CREATE INDEX refund_requests_status_idx ON public.refund_requests (status, created_at DESC);
CREATE INDEX refund_requests_purchase_idx ON public.refund_requests (purchase_type, purchase_id);
