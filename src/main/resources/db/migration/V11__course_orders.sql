-- Individual, one-time purchase of permanent access to a paid course through Mercado Pago, modeled
-- on gem_orders. The course price lives in the YAML and is mirrored onto courses.price; each
-- purchase attempt is a course_order paid through a Mercado Pago order. Paying grants a permanent
-- user_course_enrollment (organization_id null, access_expires_at null).

ALTER TABLE public.courses
    ADD COLUMN price numeric(12, 2);

CREATE TABLE public.course_orders (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    course_id uuid NOT NULL,
    amount numeric(12, 2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    mercado_pago_order_id character varying(100),
    created_at timestamp(6) with time zone NOT NULL,
    paid_at timestamp(6) with time zone,
    CONSTRAINT course_orders_pkey PRIMARY KEY (id),
    CONSTRAINT course_orders_user_fk FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT course_orders_course_fk FOREIGN KEY (course_id) REFERENCES public.courses (id)
);

CREATE UNIQUE INDEX course_orders_mp_order_uq ON public.course_orders (mercado_pago_order_id);
CREATE INDEX course_orders_user_idx ON public.course_orders (user_id, created_at DESC);
