-- Purchases table
CREATE TABLE purchases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_slot_id UUID NOT NULL UNIQUE REFERENCES purchase_slots(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    payment_id VARCHAR(100) NOT NULL UNIQUE,
    idempotency_key UUID NOT NULL UNIQUE,
    amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'KRW',
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CARD', 'BANK_TRANSFER', 'WALLET')),
    payment_status VARCHAR(20) NOT NULL CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    confirmation_timestamp TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(100) NOT NULL,

    CONSTRAINT confirmation_only_on_success CHECK (
        (payment_status = 'SUCCESS' AND confirmation_timestamp IS NOT NULL) OR
        (payment_status != 'SUCCESS' AND confirmation_timestamp IS NULL)
    ),
    CONSTRAINT failure_reason_only_on_failed CHECK (
        (payment_status = 'FAILED' AND failure_reason IS NOT NULL) OR
        (payment_status != 'FAILED' AND failure_reason IS NULL)
    )
);

-- Indexes for purchase queries
CREATE INDEX idx_purchase_user_status ON purchases(user_id, payment_status);
CREATE INDEX idx_purchase_created_at ON purchases(created_at DESC);

-- Comments for documentation
COMMENT ON TABLE purchases IS 'Completed payment transactions for purchase slots';
COMMENT ON COLUMN purchases.user_id IS 'FK to auth.users (external domain)';
COMMENT ON COLUMN purchases.product_id IS 'Denormalized for query efficiency';
COMMENT ON COLUMN purchases.idempotency_key IS 'For duplicate payment prevention';
COMMENT ON COLUMN purchases.payment_id IS 'External payment gateway reference';
