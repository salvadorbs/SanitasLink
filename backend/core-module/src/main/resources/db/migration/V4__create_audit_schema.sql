-- V4: Immutable audit trail.

CREATE TABLE audit_events
(
    id             UUID PRIMARY KEY,
    office_id      UUID,
    operator_id    UUID,
    action_type    VARCHAR(50)  NOT NULL,
    resource_type  VARCHAR(50),
    resource_id    VARCHAR(100),
    patient_id     UUID,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(255),
    correlation_id VARCHAR(100),
    metadata       JSONB,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_events_office ON audit_events (office_id);
CREATE INDEX idx_audit_events_occurred ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_operator ON audit_events (operator_id);

-- Audit events are immutable: no UPDATE or DELETE is ever allowed at the database level.
CREATE OR REPLACE FUNCTION prevent_audit_events_mutation()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'audit_events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_immutable
    BEFORE UPDATE OR DELETE
    ON audit_events
    FOR EACH ROW
EXECUTE FUNCTION prevent_audit_events_mutation();
