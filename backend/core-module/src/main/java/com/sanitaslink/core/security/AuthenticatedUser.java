package com.sanitaslink.core.security;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal. Holds identity data derived from the JWT; role/permission
 * authorities are refreshed from the database on every request.
 */
public record AuthenticatedUser(
    UUID id, String email, UUID officeId, List<String> roles, boolean admin) {}
