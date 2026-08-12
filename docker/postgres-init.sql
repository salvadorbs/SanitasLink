-- Local development database bootstrap. The app_user password below is a LOCAL-DEVELOPMENT
-- default; the runtime role must receive its credentials via the environment in any real
-- deployment (see backend/core-module/src/main/resources/application.yml).
CREATE ROLE app_user LOGIN PASSWORD 'app_user';

GRANT USAGE ON SCHEMA public TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE db_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES FOR ROLE db_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_user;
