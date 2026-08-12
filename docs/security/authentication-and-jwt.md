# Authentication and JWT

## Login

Logging in with an email and password:

1. a per-IP rate limit is applied;
2. the email is normalized (trim + lowercase) and the user is looked up;
3. the password is verified with BCrypt (cost 12);
4. failed attempts are counted and the account is temporarily locked after 5 failures;
5. on success a new access token and a rotated refresh token are issued.

Credential errors are **uniform** to prevent account enumeration.

## Access token

An access token is a JWT signed with HMAC-SHA256 using
`SANITASLINK_JWT_SECRET` (a base64-encoded key of at least 32 bytes). It carries:

```json
{
  "iss": "sanitaslink-backend",
  "sub": "<user-uuid>",
  "email": "user@example.com",
  "roles": ["MEDICO_TITOLARE"],
  "permissions": ["CORE_OFFICE_READ", "..."],
  "office_id": "<office-uuid>",
  "exp": 1699999999,
  "jti": "unique-token-id"
}
```

> The `roles` and `permissions` claims are **informational only**. On every request a filter
> re-resolves roles and permissions from the database, so administrative changes take effect
> immediately and stale claims can never grant access.

The default access token TTL is 15 minutes (`sanitaslink.security.jwt.access-token-ttl`).

## Refresh tokens

The refresh flow rotates the refresh token: the presented token is revoked, a new one is issued
and a new access token is returned. Raw refresh tokens are random 256-bit values; only their
SHA-256 hash is stored. Logging out revokes the refresh token. A password change or password
reset revokes all of the user's refresh tokens.

## First access and invitation acceptance

Invitation acceptance completes registration for an invited user: it sets the profile, the first
password and activates the office membership with the invited role. No initial password is ever
sent by email; the invitation token is the bearer credential.

## Password reset

Requesting a password reset always produces the same outcome (no account enumeration). When the
email exists, a one-time token is stored hashed and delivered through the notification port.
Confirming the reset sets the new password, invalidates refresh tokens and activates the account.
