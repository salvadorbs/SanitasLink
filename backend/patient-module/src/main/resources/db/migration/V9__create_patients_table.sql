-- V9: Patient registry (tenant-scoped clinical foundation).
-- Patients are protected by FORCE ROW LEVEL SECURITY and carry the standard audit columns.
-- Sensitive fields (tax identifier, clinical notes) are encrypted at rest by the application.
-- The composite unique (office_id, id) backs office-scoped foreign keys from appointments
-- and prescriptions, preventing cross-office patient references.

CREATE TABLE patients
(
    id             UUID PRIMARY KEY,
    office_id      UUID NOT NULL REFERENCES offices (id),
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    birth_date     DATE,
    tax_identifier VARCHAR(255),
    email          VARCHAR(320),
    phone          VARCHAR(30),
    address        VARCHAR(300),
    clinical_notes VARCHAR(20000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100)
);

CREATE INDEX idx_patients_office ON patients (office_id);
ALTER TABLE patients
    ADD CONSTRAINT uq_patients_office_id_id UNIQUE (office_id, id);

ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE patients FORCE ROW LEVEL SECURITY;

CREATE POLICY patients_all ON patients FOR ALL
    USING (app_is_admin() OR office_id = app_current_office_id())
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());
