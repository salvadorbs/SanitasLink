package com.sanitaslink.core.auth.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Current authenticated user profile with effective roles and permissions. */
public record MeResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String status,
    UUID officeId,
    List<String> roles,
    Set<String> permissions) {}
