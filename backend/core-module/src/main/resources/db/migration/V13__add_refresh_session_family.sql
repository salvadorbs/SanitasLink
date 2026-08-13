-- ---------------------------------------------------------------------------
-- Session families for refresh tokens
--
-- Each login creates a new family; rotations keep the same family id. A
-- replay of an already-rotated token revokes every token of its family
-- (the sessions cloned from the same login), while independent logins on
-- other devices stay untouched.
--
-- gen_random_uuid() is built into PostgreSQL 13+, so the default backfills
-- legacy rows as single-token families without any DML (DDL is not subject
-- to row-level security, unlike the UPDATE a backfill would require).
-- ---------------------------------------------------------------------------
ALTER TABLE refresh_tokens
    ADD COLUMN session_family_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE refresh_tokens
    ALTER COLUMN session_family_id DROP DEFAULT;

CREATE INDEX idx_refresh_tokens_session_family ON refresh_tokens (session_family_id);