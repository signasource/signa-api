CREATE TABLE course_purchases (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    course_id uuid NOT NULL,
    created_by_id uuid,
    seats integer NOT NULL,
    duration_days integer NOT NULL,
    sign_count integer NOT NULL,
    price_per_seat numeric(12, 2) NOT NULL,
    total_amount numeric(14, 2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(20) NOT NULL,
    payment_reference character varying(100),
    paid_at timestamp(6) with time zone,
    invitation_deadline timestamp(6) with time zone,
    cancelled_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT course_purchases_pkey PRIMARY KEY (id),
    CONSTRAINT course_purchases_status_check CHECK (
        status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED')
    ),
    CONSTRAINT fk_course_purchases_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id),
    CONSTRAINT fk_course_purchases_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_course_purchases_created_by FOREIGN KEY (created_by_id) REFERENCES users (id)
);

CREATE INDEX idx_course_purchases_organization ON course_purchases (organization_id);
CREATE INDEX idx_course_purchases_status ON course_purchases (status);

CREATE TABLE purchase_seats (
    id uuid NOT NULL,
    purchase_id uuid NOT NULL,
    email character varying(150) NOT NULL,
    status character varying(20) NOT NULL,
    invitation_token character varying(64),
    invited_at timestamp(6) with time zone,
    last_reminder_at timestamp(6) with time zone,
    auto_reminder_sent_at timestamp(6) with time zone,
    activated_at timestamp(6) with time zone,
    access_expires_at timestamp(6) with time zone,
    revoked_at timestamp(6) with time zone,
    user_id uuid,
    CONSTRAINT purchase_seats_pkey PRIMARY KEY (id),
    CONSTRAINT uk_purchase_seats_purchase_email UNIQUE (purchase_id, email),
    CONSTRAINT uk_purchase_seats_invitation_token UNIQUE (invitation_token),
    CONSTRAINT purchase_seats_status_check CHECK (
        status IN (
            'PENDING_PAYMENT',
            'INVITED',
            'INVITATION_EXPIRED',
            'ACTIVE',
            'ACCESS_EXPIRED',
            'REVOKED',
            'CANCELLED'
        )
    ),
    CONSTRAINT fk_purchase_seats_purchase FOREIGN KEY (purchase_id)
        REFERENCES course_purchases (id),
    CONSTRAINT fk_purchase_seats_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_purchase_seats_email ON purchase_seats (email);
CREATE INDEX idx_purchase_seats_user ON purchase_seats (user_id);
CREATE INDEX idx_purchase_seats_status ON purchase_seats (status);
