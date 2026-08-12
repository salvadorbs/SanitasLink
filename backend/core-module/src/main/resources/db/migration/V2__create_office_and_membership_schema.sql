-- V2: Office (tenant) and membership schema.
-- The office is the tenant. A user can belong to at most one office (mono-office invariant),
-- enforced by the PRIMARY KEY on office_memberships.user_id.

CREATE TABLE offices
(
    id             UUID PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    legal_name     VARCHAR(200),
    tax_identifier VARCHAR(50),
    email          VARCHAR(320),
    phone          VARCHAR(30),
    address        VARCHAR(300),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version        INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100)
);

CREATE TABLE office_memberships
(
    office_id  UUID NOT NULL REFERENCES offices (id),
    user_id    UUID NOT NULL REFERENCES users (id),
    status     VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    invited_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_office_memberships_office ON office_memberships (office_id);
CREATE INDEX idx_office_memberships_status ON office_memberships (status);

CREATE TABLE user_roles
(
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    assigned_by UUID,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);
