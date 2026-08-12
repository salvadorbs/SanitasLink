package com.sanitaslink.core.office.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to invite a collaborator to the office with a predefined role. */
public record InviteMemberRequest(
    @NotBlank @Email(message = "must be a valid email address") String email,
    @NotNull UUID roleId) {}
