-- V6: Row-Level Security policies for tenant-scoped tables.
-- Context GUCs (set with SET LOCAL semantics via set_config(..., false) inside each
-- transaction) are:
--   app.current_office_id  - the UUID of the current office (empty string when absent)
--   app.current_user_id    - the UUID of the current user (empty string when absent)
--   app.is_admin           - 'true' when the current user holds the global ADMIN role
-- The application JDBC user (app_user) is NOT the table owner, so RLS always applies.
-- FORCE ROW LEVEL SECURITY additionally applies the policies to the owner (db_owner),
-- which is only ever used by Flyway for migrations.

CREATE OR REPLACE FUNCTION app_current_office_id() RETURNS UUID
    LANGUAGE sql
    STABLE
AS $$
SELECT NULLIF(current_setting('app.current_office_id', true), '')::uuid
$$;

CREATE OR REPLACE FUNCTION app_current_user_id() RETURNS UUID
    LANGUAGE sql
    STABLE
AS $$
SELECT NULLIF(current_setting('app.current_user_id', true), '')::uuid
$$;

CREATE OR REPLACE FUNCTION app_is_admin() RETURNS BOOLEAN
    LANGUAGE sql
    STABLE
AS $$
SELECT COALESCE(current_setting('app.is_admin', true), '') = 'true'
$$;

-- ---------------------------------------------------------------------------
-- offices
-- ---------------------------------------------------------------------------
ALTER TABLE offices ENABLE ROW LEVEL SECURITY;
ALTER TABLE offices FORCE ROW LEVEL SECURITY;

CREATE POLICY offices_select ON offices FOR SELECT
    USING (app_is_admin() OR id = app_current_office_id());

CREATE POLICY offices_insert ON offices FOR INSERT
    WITH CHECK (app_is_admin());

CREATE POLICY offices_update ON offices FOR UPDATE
    USING (app_is_admin() OR id = app_current_office_id())
    WITH CHECK (app_is_admin() OR id = app_current_office_id());

-- ---------------------------------------------------------------------------
-- office_memberships
-- A user can always read their own membership rows (bootstrap context), but can
-- only modify memberships of their own office.
-- ---------------------------------------------------------------------------
ALTER TABLE office_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE office_memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY om_select ON office_memberships FOR SELECT
    USING (app_is_admin() OR office_id = app_current_office_id() OR user_id = app_current_user_id());

CREATE POLICY om_insert ON office_memberships FOR INSERT
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());

CREATE POLICY om_update ON office_memberships FOR UPDATE
    USING (app_is_admin() OR office_id = app_current_office_id())
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());

CREATE POLICY om_delete ON office_memberships FOR DELETE
    USING (app_is_admin() OR office_id = app_current_office_id());

-- ---------------------------------------------------------------------------
-- user_roles
-- A user can read their own role assignments, but can only create/modify role
-- assignments within an office they belong to (or as a global admin).
-- ---------------------------------------------------------------------------
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;

CREATE POLICY ur_select ON user_roles FOR SELECT
    USING (app_is_admin()
           OR user_id = app_current_user_id()
           OR EXISTS (SELECT 1
                      FROM office_memberships om
                      WHERE om.user_id = user_roles.user_id
                        AND om.office_id = app_current_office_id()));

CREATE POLICY ur_insert ON user_roles FOR INSERT
    WITH CHECK (app_is_admin()
                OR EXISTS (SELECT 1
                           FROM office_memberships om
                           WHERE om.user_id = user_roles.user_id
                             AND om.office_id = app_current_office_id()));

CREATE POLICY ur_update ON user_roles FOR UPDATE
    USING (app_is_admin()
           OR EXISTS (SELECT 1
                      FROM office_memberships om
                      WHERE om.user_id = user_roles.user_id
                        AND om.office_id = app_current_office_id()))
    WITH CHECK (app_is_admin()
                OR EXISTS (SELECT 1
                           FROM office_memberships om
                           WHERE om.user_id = user_roles.user_id
                             AND om.office_id = app_current_office_id()));

CREATE POLICY ur_delete ON user_roles FOR DELETE
    USING (app_is_admin()
           OR EXISTS (SELECT 1
                      FROM office_memberships om
                      WHERE om.user_id = user_roles.user_id
                        AND om.office_id = app_current_office_id()));

-- ---------------------------------------------------------------------------
-- office_invitations
-- ---------------------------------------------------------------------------
ALTER TABLE office_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE office_invitations FORCE ROW LEVEL SECURITY;

CREATE POLICY oi_select ON office_invitations FOR SELECT
    USING (app_is_admin() OR office_id = app_current_office_id());

CREATE POLICY oi_insert ON office_invitations FOR INSERT
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());

CREATE POLICY oi_update ON office_invitations FOR UPDATE
    USING (app_is_admin() OR office_id = app_current_office_id())
    WITH CHECK (app_is_admin() OR office_id = app_current_office_id());

CREATE POLICY oi_delete ON office_invitations FOR DELETE
    USING (app_is_admin() OR office_id = app_current_office_id());

-- ---------------------------------------------------------------------------
-- audit_events
-- Reads are restricted to the owning office (or global admins); appends are
-- always allowed because the audit trail is written by the application. Updates
-- and deletes are blocked by the immutable trigger (and by the absence of any
-- UPDATE/DELETE policy).
-- ---------------------------------------------------------------------------
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;

CREATE POLICY ae_select ON audit_events FOR SELECT
    USING (app_is_admin() OR office_id IS NULL OR office_id = app_current_office_id());

CREATE POLICY ae_insert ON audit_events FOR INSERT
    WITH CHECK (TRUE);
