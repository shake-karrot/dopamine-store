-- Purchase Slots table
CREATE TABLE purchase_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL,
    acquisition_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    expiration_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'COMPLETED')),
    reclaim_status VARCHAR(30) CHECK (reclaim_status IN ('AUTO_EXPIRED', 'MANUAL_RECLAIMED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(100) NOT NULL,

    CONSTRAINT expiration_30min CHECK (
        expiration_timestamp = acquisition_timestamp + INTERVAL '30 minutes'
    ),
    CONSTRAINT reclaim_only_when_expired CHECK (
        (status = 'EXPIRED' AND reclaim_status IS NOT NULL) OR
        (status != 'EXPIRED' AND reclaim_status IS NULL)
    )
);

-- Unique constraint: one active slot per user per product
CREATE UNIQUE INDEX idx_slot_user_product_active
    ON purchase_slots(user_id, product_id)
    WHERE status = 'ACTIVE';

-- Index for expiration job queries
CREATE INDEX idx_slot_expiration ON purchase_slots(expiration_timestamp)
    WHERE status = 'ACTIVE';

-- Index for slot counting by product and status
CREATE INDEX idx_slot_product_status ON purchase_slots(product_id, status);

-- Comments for documentation
COMMENT ON TABLE purchase_slots IS 'Temporary purchase rights granted on first-come-first-served basis';
COMMENT ON COLUMN purchase_slots.user_id IS 'FK to auth.users (external domain)';
COMMENT ON COLUMN purchase_slots.acquisition_timestamp IS 'When slot was acquired (epoch milliseconds precision)';
COMMENT ON COLUMN purchase_slots.expiration_timestamp IS 'When slot expires (acquisition + 30 minutes)';
COMMENT ON COLUMN purchase_slots.reclaim_status IS 'Reason for expiration (only for EXPIRED status)';
