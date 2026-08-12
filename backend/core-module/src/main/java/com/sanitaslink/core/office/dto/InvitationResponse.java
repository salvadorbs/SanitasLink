package com.sanitaslink.core.office.dto;

import java.time.Instant;
import java.util.UUID;

/** Office invitation view model. */
public record InvitationResponse(
    UUID id,
    String email,
    UUID roleId,
    String roleCode,
    String roleName,
    String status,
    Instant expiresAt,
    Instant createdAt) {}
