-- V9: Clinical domain foundation.
-- Patients, appointments and prescriptions are tenant-scoped (office_id), protected by
-- FORCE ROW LEVEL SECURITY and carry the standard audit columns. Sensitive fields (tax
-- identifier, clinical notes, medication data) are encrypted at rest by the application.

-- ---------------------------------------------------------------------------
-- patients
-- ---------------------------------------------------------------------------
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

ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE patients FORCE ROW LEVEL SECURITY;

CREATE POLICY patients_all ON patients FOR ALL
    USING (app_is_admin() OR office_id = app_current_office_id())
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());

-- ---------------------------------------------------------------------------
-- appointments
-- ---------------------------------------------------------------------------
CREATE TABLE appointments
(
    id         UUID PRIMARY KEY,
    office_id  UUID NOT NULL REFERENCES offices (id),
    patient_id UUID REFERENCES patients (id),
    title      VARCHAR(200) NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes      VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT chk_appointments_status
        CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'CANCELLED', 'MISSED', 'COMPLETED')),
    CONSTRAINT chk_appointments_period CHECK (ends_at > starts_at)
);

CREATE INDEX idx_appointments_office ON appointments (office_id);
CREATE INDEX idx_appointments_patient ON appointments (patient_id);

ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments FORCE ROW LEVEL SECURITY;

CREATE POLICY appointments_all ON appointments FOR ALL
    USING (app_is_admin() OR office_id = app_current_office_id())
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());

-- ---------------------------------------------------------------------------
-- prescriptions
-- ---------------------------------------------------------------------------
CREATE TABLE prescriptions
(
    id          UUID PRIMARY KEY,
    office_id   UUID NOT NULL REFERENCES offices (id),
    patient_id  UUID REFERENCES patients (id),
    status      VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    medication  VARCHAR(600) NOT NULL,
    instructions VARCHAR(20000),
    issued_at   TIMESTAMPTZ,
    printed_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
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
