-- V5: Seed the initial role catalog, permission catalog and the role-permission matrix.
-- These records are the initial data only. Roles, permissions and assignments are
-- database-managed and fully mutable through future administrative APIs.

-- Roles
INSERT INTO roles (id, code, name, description, scope, system_role, active, version)
VALUES ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'Platform Administrator',
        'Global platform administrator with system-level access', 'PLATFORM', TRUE, TRUE, 0),
       ('00000000-0000-0000-0000-000000000011', 'MEDICO_TITOLARE', 'Practice Owner',
        'Owner of the practice, full office permissions', 'OFFICE', TRUE, TRUE, 0),
       ('00000000-0000-0000-0000-000000000012', 'MEDICO_COLLABORATORE', 'Collaborating Physician',
        'Physician collaborating within the practice', 'OFFICE', TRUE, TRUE, 0),
       ('00000000-0000-0000-0000-000000000013', 'SEGRETARIA_BASE', 'Base Secretary',
        'Secretary with administrative-only access', 'OFFICE', TRUE, TRUE, 0),
       ('00000000-0000-0000-0000-000000000014', 'SEGRETARIA_AVANZATA', 'Advanced Secretary',
        'Secretary with extended administrative access', 'OFFICE', TRUE, TRUE, 0);

-- Permissions
INSERT INTO permissions (id, code, module, name, description, active, version)
VALUES ('00000000-0000-0000-0000-000000000101', 'CORE_OFFICE_READ', 'CORE', 'View Office Info',
        'Access to office information and parameters', TRUE, 0),
       ('00000000-0000-0000-0000-000000000102', 'CORE_OFFICE_UPDATE', 'CORE', 'Edit Office Info',
        'Update office address, hours and legal data', TRUE, 0),
       ('00000000-0000-0000-0000-000000000103', 'CORE_STAFF_INVITE', 'CORE', 'Invite Staff',
        'Send invitations to register physicians or secretaries in the office', TRUE, 0),
       ('00000000-0000-0000-0000-000000000104', 'CORE_STAFF_MANAGE', 'CORE', 'Manage Staff',
        'Assign or revoke roles to collaborators and deactivate users', TRUE, 0),
       ('00000000-0000-0000-0000-000000000201', 'PATIENT_REGISTRY_READ', 'PATIENT', 'Read Registry',
        'Search and view patient registry and contacts', TRUE, 0),
       ('00000000-0000-0000-0000-000000000202', 'PATIENT_REGISTRY_CREATE', 'PATIENT', 'Register Patient',
        'Manually register a new patient in the office', TRUE, 0),
       ('00000000-0000-0000-0000-000000000203', 'PATIENT_REGISTRY_UPDATE', 'PATIENT', 'Update Registry',
        'Update demographic data, exemptions and contacts', TRUE, 0),
       ('00000000-0000-0000-0000-000000000204', 'PATIENT_PRIVACY_MANAGE', 'PATIENT', 'GDPR Management',
        'Record and print privacy policy and consent', TRUE, 0),
       ('00000000-0000-0000-0000-000000000205', 'PATIENT_CLINICAL_READ', 'PATIENT', 'Read Clinical Record',
        'View medical history, clinical diaries, problems and allergies', TRUE, 0),
       ('00000000-0000-0000-0000-000000000206', 'PATIENT_CLINICAL_WRITE', 'PATIENT', 'Write Clinical Record',
        'Record visit diaries, diagnoses, prescriptions and exams', TRUE, 0),
       ('00000000-0000-0000-0000-000000000301', 'APPOINTMENT_READ', 'APPOINTMENT', 'View Agenda',
        'Read the office appointments', TRUE, 0),
       ('00000000-0000-0000-0000-000000000302', 'APPOINTMENT_CREATE', 'APPOINTMENT', 'Book Appointment',
        'Create new appointments in the calendar', TRUE, 0),
       ('00000000-0000-0000-0000-000000000303', 'APPOINTMENT_UPDATE', 'APPOINTMENT', 'Edit Appointment',
        'Reschedule, change status or edit organisational notes', TRUE, 0),
       ('00000000-0000-0000-0000-000000000304', 'APPOINTMENT_CANCEL', 'APPOINTMENT', 'Cancel Appointment',
        'Cancel an appointment or mark it as missed', TRUE, 0),
       ('00000000-0000-0000-0000-000000000305', 'APPOINTMENT_SLOT_MANAGE', 'APPOINTMENT', 'Configure Slots',
        'Manage reception hours, holidays and agenda blocks', TRUE, 0),
       ('00000000-0000-0000-0000-000000000401', 'PRESCRIPTION_READ', 'PRESCRIPTION', 'View Prescriptions',
        'Read prescription request history and issued tickets', TRUE, 0),
       ('00000000-0000-0000-0000-000000000402', 'PRESCRIPTION_REQUEST_CREATE', 'PRESCRIPTION', 'Request Prescription',
        'Create prescription requests on behalf of patients', TRUE, 0),
       ('00000000-0000-0000-0000-000000000403', 'PRESCRIPTION_WRITE', 'PRESCRIPTION', 'Issue Prescription',
        'Sign and officially issue medical prescriptions', TRUE, 0),
       ('00000000-0000-0000-0000-000000000404', 'PRESCRIPTION_PRINT', 'PRESCRIPTION', 'Print / Send Prescription',
        'Print reminders and send prescriptions by email or SMS', TRUE, 0);

-- MEDICO_TITOLARE: all office permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000011', id
FROM permissions
WHERE id BETWEEN '00000000-0000-0000-0000-000000000101' AND '00000000-0000-0000-0000-000000000404';

-- MEDICO_COLLABORATORE: everything except CORE_OFFICE_UPDATE, CORE_STAFF_INVITE, CORE_STAFF_MANAGE
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000012', id
FROM permissions
WHERE id NOT IN ('00000000-0000-0000-0000-000000000102',
                 '00000000-0000-0000-0000-000000000103',
                 '00000000-0000-0000-0000-000000000104');

-- SEGRETARIA_BASE: office read, registry, privacy, appointments, prescription read + request create
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000013', id
FROM permissions
WHERE id IN ('00000000-0000-0000-0000-000000000101',
             '00000000-0000-0000-0000-000000000201',
             '00000000-0000-0000-0000-000000000202',
             '00000000-0000-0000-0000-000000000203',
             '00000000-0000-0000-0000-000000000204',
             '00000000-0000-0000-0000-000000000301',
             '00000000-0000-0000-0000-000000000302',
             '00000000-0000-0000-0000-000000000303',
             '00000000-0000-0000-0000-000000000304',
             '00000000-0000-0000-0000-000000000305',
             '00000000-0000-0000-0000-000000000401',
             '00000000-0000-0000-0000-000000000402');

-- SEGRETARIA_AVANZATA: SEGRETARIA_BASE plus PRESCRIPTION_PRINT
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000014', id
FROM permissions
WHERE id IN ('00000000-0000-0000-0000-000000000101',
             '00000000-0000-0000-0000-000000000201',
             '00000000-0000-0000-0000-000000000202',
             '00000000-0000-0000-0000-000000000203',
             '00000000-0000-0000-0000-000000000204',
             '00000000-0000-0000-0000-000000000301',
             '00000000-0000-0000-0000-000000000302',
             '00000000-0000-0000-0000-000000000303',
             '00000000-0000-0000-0000-000000000304',
             '00000000-0000-0000-0000-000000000305',
             '00000000-0000-0000-0000-000000000401',
             '00000000-0000-0000-0000-000000000402',
             '00000000-0000-0000-0000-000000000404');

-- ADMIN: no granular permissions assigned; platform access is granted via the ADMIN role itself.
