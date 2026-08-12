-- V10: Appointments (tenant-scoped).
-- Office-scoped RLS plus a composite foreign key so an appointment can only reference a patient
-- that belongs to the same office.

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
    CONSTRAINT fk_appointments_patient_office
        FOREIGN KEY (office_id, patient_id) REFERENCES patients (office_id, id),
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
