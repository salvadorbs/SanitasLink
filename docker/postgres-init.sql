-- Local development database bootstrap. This script runs as the deterministic bootstrap owner
-- (db_owner, the Compose PostgreSQL superuser). The runtime role app_user must never own tables,
-- schemas or migrations, so RLS always applies to it. The app_user password below is a
-- LOCAL-DEVELOPMENT default; the runtime role receives its credentials via the environment in any
-- real deployment (see backend/app-module/src/main/resources/application.yml).

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_user';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE db_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES FOR ROLE db_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_user;
