package com.sanitaslink.core.office.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to assign a role to an office member. */
public record AssignRoleRequest(@NotNull UUID roleId) {}
