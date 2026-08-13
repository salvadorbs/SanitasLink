# Authentication and JWT

## Login

Logging in with an email and password:

1. a per-IP rate limit is applied;
2. the email is normalized (trim + lowercase) and the user is looked up;
3. the password is verified with BCrypt (cost 12);
4. failed attempts are counted and the account is temporarily locked after 5 failures;
5. on success a new access token and a rotated refresh token are issued.

Credential errors are **uniform** to prevent account enumeration.

The login rate limiter is an in-memory fixed-window limiter keyed by client address. It is
appropriate for a single-node deployment; a distributed limiter (or a shared store) must be used
when the backend is scaled horizontally.

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
  "sv": 0,
  "exp": 1699999999,
  "jti": "unique-token-id"
}
```

> The `roles` and `permissions` claims are **informational only**. On every request a filter
> re-resolves roles and permissions from the database, so administrative changes take effect
> immediately and stale claims can never grant access.
>
> The `sv` claim is the user's security version. It is incremented on every password change and
> reset, so access tokens issued before the change are rejected even before their natural expiry.

The decoder enforces the configured issuer (`sanitaslink.security.jwt.issuer`) in addition to the
signature. The default access token TTL is 15 minutes
(`sanitaslink.security.jwt.access-token-ttl`).

## Refresh tokens

The raw refresh token is a random 256-bit value; **only its SHA-256 hash is stored** and no raw
refresh token ever appears in JSON responses, logs or persistent client storage. The backend sets
it exclusively in an `HttpOnly` cookie:

- `sl_refresh`, scoped to `Path=/api/v1/auth`;
- `Secure` in production, disabled only in the `dev` profile;
- `SameSite=Strict`, so browsers never send it on cross-site requests;
- `Max-Age` equal to the refresh TTL (default 7 days).

The frontend reads nothing from the cookie: the browser attaches it automatically
(`withCredentials`), and the SPA keeps only the in-memory access token.

### Rotation and session families

Each login creates a new **session family** (`refresh_tokens.session_family_id`). Every rotation
within that session keeps the family id; the rotated token is atomically revoked
(`UPDATE ... WHERE revoked_at IS NULL` wins the race) and linked to its replacement via
`replaced_by_token_hash`.

The refresh flow therefore issues **exactly one** replacement refresh token per call.

### Replay detection

Presenting an already-rotated (revoked + replaced) token is treated as **replay**. The whole
session family is revoked in a dedicated transaction (it must survive the 401 response) and a
`TOKEN_REUSE` audit event is recorded. Independent sessions created by later logins on other
devices are **not** affected.

### Revocation scope

- **logout**: revokes the presented token and expires the cookie (`Max-Age=0`);
- **replay**: revokes the whole session family;
- **password change / password reset**: revokes *all* of the user's refresh tokens (security
  version bump also invalidates previously issued access tokens).

## CSRF and CORS for cookie endpoints

Spring CSRF is disabled because the access token travels in the `Authorization` header and the
cookie endpoints (login, refresh, logout, invitation acceptance) are protected by:

1. `SameSite=Strict` on the cookie;
2. an explicit `Origin` allowlist enforced by the CORS filter *and* by the controllers
   (`assertCookieOrigin`), rejecting any disallowed origin with `403` before token rotation;
3. credentialed CORS allowing only the configured origins and a narrow header allowlist
   (`Authorization`, `Content-Type`, `Accept`, `X-Correlation-Id`).

Requests without an `Origin` header (API clients, curl) are accepted: there is no cross-site
context without an Origin, and non-browser clients manage their own credentials. All auth
responses carry `Cache-Control: no-store`.

## First access and invitation acceptance

Invitation acceptance completes registration for an invited user: it sets the profile, the first
password and activates the office membership with the invited role. No initial password is ever
sent by email; the invitation token is the bearer credential.

## Password reset

Requesting a password reset always produces the same outcome (no account enumeration). When the
email exists, a one-time token is stored hashed and delivered through the notification port.
Confirming the reset sets the new password, invalidates refresh tokens and activates the account.
