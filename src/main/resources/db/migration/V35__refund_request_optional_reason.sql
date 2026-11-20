-- The buyer may ask for a refund without choosing a reason or writing a comment.
ALTER TABLE public.refund_requests
    ALTER COLUMN reason DROP NOT NULL,
    ALTER COLUMN message DROP NOT NULL;
