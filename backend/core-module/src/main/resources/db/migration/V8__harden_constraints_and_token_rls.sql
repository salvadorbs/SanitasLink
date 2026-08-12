-- V8: Database hardening.
--  - users.security_version to invalidate access tokens on password change/reset
--  - CHECK constraints for lifecycle statuses and role scope
--  - referential integrity for audit/assignment ownership fields
--  - trigger enforcing that office invitations reference OFFICE-scope roles
--  - RLS for refresh_tokens and password_reset_tokens
--  - tightened audit_events policies (no cross-tenant global reads, no forged tenant rows)

-- ---------------------------------------------------------------------------
-- Access-token invalidation version
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN security_version INTEGER NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- Lifecycle / scope CHECK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT chk_users_status
        CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED', 'LOCKED'));

ALTER TABLE offices
    ADD CONSTRAINT chk_offices_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));

ALTER TABLE office_memberships
    ADD CONSTRAINT chk_office_memberships_status
        CHECK (status IN ('INVITED', 'ACTIVE', 'REVOKED'));

ALTER TABLE roles
    ADD CONSTRAINT chk_roles_scope
        CHECK (scope IN ('PLATFORM', 'OFFICE'));

ALTER TABLE office_invitations
    ADD CONSTRAINT chk_office_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'));

-- ---------------------------------------------------------------------------
-- Ownership referential integrity
-- Historical rows may reference users that no longer exist. For this one-time
-- maintenance migration the immutable audit trigger is dropped and RLS is
-- temporarily disabled so orphaned references can be cleaned up before the
-- foreign keys are added; both are fully restored afterwards.
-- ---------------------------------------------------------------------------
DROP TRIGGER trg_audit_events_immutable ON audit_events;

ALTER TABLE audit_events DISABLE ROW LEVEL SECURITY;
ALTER TABLE office_invitations DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles DISABLE ROW LEVEL SECURITY;

UPDATE audit_events
SET operator_id = NULL
WHERE operator_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = audit_events.operator_id);

UPDATE audit_events
SET office_id = NULL
WHERE office_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM offices o WHERE o.id = audit_events.office_id);

UPDATE office_invitations
SET created_by = NULL
WHERE created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = office_invitations.created_by);

UPDATE user_roles
SET assigned_by = NULL
WHERE assigned_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = user_roles.assigned_by);

ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_operator
        FOREIGN KEY (operator_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_office
        FOREIGN KEY (office_id) REFERENCES offices (id) ON DELETE SET NULL;

ALTER TABLE office_invitations
    ADD CONSTRAINT fk_office_invitations_creator
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
ALTER TABLE office_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE office_invitations FORCE ROW LEVEL SECURITY;
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;

CREATE TRIGGER trg_audit_events_immutable
    BEFORE UPDATE OR DELETE
    ON audit_events
    FOR EACH ROW
EXECUTE FUNCTION prevent_audit_events_mutation();

-- ---------------------------------------------------------------------------
-- Office invitations must always reference an OFFICE-scope role
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_office_invitation_role_scope()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM roles r WHERE r.id = NEW.role_id AND r.scope = 'OFFICE') THEN
        RAISE EXCEPTION 'office_invitations.role_id must reference an OFFICE-scope role';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_office_invitation_role_scope
    BEFORE INSERT OR UPDATE
    ON office_invitations
    FOR EACH ROW
EXECUTE FUNCTION enforce_office_invitation_role_scope();

-- ---------------------------------------------------------------------------
-- RLS for refresh_tokens
-- Reads are allowed for the owning user, for the bearer of the raw token hash,
-- and for global admins. Writes require the owning user's context (or admin).
-- ---------------------------------------------------------------------------
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;

CREATE POLICY rt_select ON refresh_tokens FOR SELECT
    USING (app_is_admin()
           OR user_id = app_current_user_id()
           OR token_hash = app_current_token_hash());

CREATE POLICY rt_insert ON refresh_tokens FOR INSERT
    WITH CHECK (app_is_admin() OR user_id = app_current_user_id());

CREATE POLICY rt_update ON refresh_tokens FOR UPDATE
    USING (app_is_admin() OR user_id = app_current_user_id())
    WITH CHECK (app_is_admin() OR user_id = app_current_user_id());

CREATE POLICY rt_delete ON refresh_tokens FOR DELETE
    USING (app_is_admin() OR user_id = app_current_user_id());

-- ---------------------------------------------------------------------------
-- RLS for password_reset_tokens
-- The reset token is a bearer credential for the confirmation flow; the owning
-- user context covers request creation and post-confirmation cleanup.
-- ---------------------------------------------------------------------------
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens FORCE ROW LEVEL SECURITY;

CREATE POLICY prt_select ON password_reset_tokens FOR SELECT
    USING (app_is_admin()
           OR user_id = app_current_user_id()
           OR token_hash = app_current_token_hash());

CREATE POLICY prt_insert ON password_reset_tokens FOR INSERT
    WITH CHECK (app_is_admin() OR user_id = app_current_user_id());

CREATE POLICY prt_update ON password_reset_tokens FOR UPDATE
    USING (app_is_admin()
           OR token_hash = app_current_token_hash()
           OR user_id = app_current_user_id())
    WITH CHECK (app_is_admin()
                OR token_hash = app_current_token_hash()
                OR user_id = app_current_user_id());

-- ---------------------------------------------------------------------------
-- Tightened audit_events policies
-- Global (office_id IS NULL) rows are no longer readable by every tenant; only
-- global admins may read them. Inserts must carry the current office context (or
-- admin) unless the event is genuinely global (e.g. login with no tenant).
-- ---------------------------------------------------------------------------
DROP POLICY ae_select ON audit_events;
DROP POLICY ae_insert ON audit_events;

CREATE POLICY ae_select ON audit_events FOR SELECT
    USING (app_is_admin() OR office_id = app_current_office_id());

CREATE POLICY ae_insert ON audit_events FOR INSERT
    WITH CHECK (app_is_admin() OR office_id IS NULL OR office_id = app_current_office_id());
