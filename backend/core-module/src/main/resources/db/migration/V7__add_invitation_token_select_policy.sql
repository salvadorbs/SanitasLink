-- V7: Allow invitation lookup by token hash.
-- The invitation token is the bearer credential for the acceptance flow: the
-- unauthenticated client must be able to read the invitation row when it proves
-- possession of the token. The application sets app.current_token_hash (SET LOCAL
-- semantics) before the lookup; possession of the token is the only way to set it.

CREATE OR REPLACE FUNCTION app_current_token_hash() RETURNS TEXT
    LANGUAGE sql
    STABLE
AS $$
SELECT NULLIF(current_setting('app.current_token_hash', true), '')
$$;

CREATE POLICY oi_token_select ON office_invitations FOR SELECT
    USING (token_hash = app_current_token_hash());
