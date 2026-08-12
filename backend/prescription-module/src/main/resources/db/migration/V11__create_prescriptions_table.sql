-- V11: Prescriptions (tenant-scoped).
-- A prescription is always issued on behalf of a patient of the same office: patient_id is
-- NOT NULL and enforced by a composite office-scoped foreign key.

CREATE TABLE prescriptions
(
    id           UUID PRIMARY KEY,
    office_id    UUID NOT NULL REFERENCES offices (id),
    patient_id   UUID NOT NULL REFERENCES patients (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    medication   VARCHAR(600) NOT NULL,
    instructions VARCHAR(20000),
    issued_at    TIMESTAMPTZ,
    printed_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT fk_prescriptions_patient_office
        FOREIGN KEY (office_id, patient_id) REFERENCES patients (office_id, id),
    CONSTRAINT chk_prescriptions_status
        CHECK (status IN ('REQUESTED', 'ISSUED', 'PRINTED', 'REVOKED'))
);

CREATE INDEX idx_prescriptions_office ON prescriptions (office_id);
CREATE INDEX idx_prescriptions_patient ON prescriptions (patient_id);

ALTER TABLE prescriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE prescriptions FORCE ROW LEVEL SECURITY;

CREATE POLICY prescriptions_all ON prescriptions FOR ALL
    USING (app_is_admin() OR office_id = app_current_office_id())
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());
