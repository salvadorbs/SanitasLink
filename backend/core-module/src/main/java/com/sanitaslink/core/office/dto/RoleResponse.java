package com.sanitaslink.core.office.dto;

import java.util.UUID;

/** Role catalog view. */
public record RoleResponse(
    UUID id, String code, String name, String description, String scope, boolean active) {}
