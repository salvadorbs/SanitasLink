package com.sanitaslink.core.office.dto;

import java.util.UUID;

/** Permission catalog view. */
public record PermissionResponse(
    UUID id, String code, String module, String name, String description, boolean active) {}
