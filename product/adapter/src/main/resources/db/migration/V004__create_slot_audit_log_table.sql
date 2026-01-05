-- Audit log table for slot state transitions
CREATE TABLE slot_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id UUID NOT NULL REFERENCES purchase_slots(id),
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(100) NOT NULL,
    metadata JSONB
);

-- Indexes for audit log queries
CREATE INDEX idx_audit_slot ON slot_audit_log(slot_id, timestamp DESC);
CREATE INDEX idx_audit_timestamp ON slot_audit_log(timestamp DESC);

-- Comments for documentation
COMMENT ON TABLE slot_audit_log IS 'Audit trail for all slot state transitions';
COMMENT ON COLUMN slot_audit_log.event_type IS 'SLOT_REQUESTED, SLOT_ACQUIRED, SLOT_EXPIRED, etc.';
COMMENT ON COLUMN slot_audit_log.metadata IS 'Additional context (e.g., expiration reason, admin action)';
