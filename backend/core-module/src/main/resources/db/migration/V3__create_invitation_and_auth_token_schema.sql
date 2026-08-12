-- V3: Invitation and authentication token tables.
-- Raw tokens are never persisted; only their SHA-256 hashes are stored.

CREATE TABLE office_invitations
(
    id          UUID PRIMARY KEY,
    office_id   UUID NOT NULL REFERENCES offices (id) ON DELETE CASCADE,
    email       VARCHAR(320) NOT NULL,
    role_id     UUID NOT NULL REFERENCES roles (id),
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    accepted_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_office_invitations_pending
    ON office_invitations (office_id, LOWER(email)) WHERE status = 'PENDING';
CREATE INDEX idx_office_invitations_office ON office_invitations (office_id);
CREATE INDEX idx_office_invitations_token ON office_invitations (token_hash);

CREATE TABLE refresh_tokens
(
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash            VARCHAR(64) NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);

CREATE TABLE password_reset_tokens
(
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE UNIQUE INDEX uq_password_reset_tokens_hash ON password_reset_tokens (token_hash);
