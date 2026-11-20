-- Gem packs are now sold with Mercado Pago instead of Google Play. Packs carry their own price
-- and each purchase attempt is a gem_order paid through a Mercado Pago order.
-- gem_purchases (Google Play tokens) is left untouched: it only holds history, nothing writes to it.

ALTER TABLE public.gem_packs
    ADD COLUMN price numeric(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN currency character varying(3) NOT NULL DEFAULT 'ARS';

CREATE TABLE public.gem_orders (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    gem_pack_id uuid NOT NULL,
    gems integer NOT NULL,
    amount numeric(12, 2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    mercado_pago_order_id character varying(100),
    created_at timestamp(6) with time zone NOT NULL,
    paid_at timestamp(6) with time zone,
    CONSTRAINT gem_orders_pkey PRIMARY KEY (id),
    CONSTRAINT gem_orders_user_fk FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT gem_orders_pack_fk FOREIGN KEY (gem_pack_id) REFERENCES public.gem_packs (id)
);

CREATE UNIQUE INDEX gem_orders_mp_order_uq ON public.gem_orders (mercado_pago_order_id);
CREATE INDEX gem_orders_user_idx ON public.gem_orders (user_id, created_at DESC);
