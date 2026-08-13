-- ---------------------------------------------------------------------------
-- E2E seed (Playwright auth lifecycle tests)
--
-- Deterministic office + active user with a known password. Run AFTER the
-- backend has applied the Flyway migrations (roles/permissions are seeded by
-- V5), e.g.:
--
--   docker compose exec -T postgres psql -U db_owner -d sanitaslink_db \
--     -v ON_ERROR_STOP=1 -f docker/e2e-seed.sql
--
-- Password: E2E-Password-123!  (BCrypt cost 12, generated for tests only)
-- ---------------------------------------------------------------------------

INSERT INTO offices (id, name, tax_identifier, status, email)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Studio E2E',
    '00000000000',
    'ACTIVE',
    'e2e@studio.example'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, password_hash, first_name, last_name, status, email_verified_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'e2e@studio.example',
    '$2a$12$rm8lhRJyl47SKZSlRikI1O6U7si6KcJqMEuMKDEgbQO3xAv4noswu',
    'E2E',
    'User',
    'ACTIVE',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO office_memberships (office_id, user_id, status, accepted_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'ACTIVE',
    NOW()
)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id, assigned_by)
SELECT '22222222-2222-2222-2222-222222222222', id, NULL
FROM roles
WHERE code = 'MEDICO_TITOLARE'
ON CONFLICT (user_id, role_id) DO NOTHING;