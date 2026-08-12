package com.sanitaslink.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accepts an office invitation: registers the invitee (first access) and activates the membership
 * with the invited role.
 */
public record AcceptInvitationRequest(
    @NotBlank String token,
    @NotBlank @Size(max = 100, message = "first name too long") String firstName,
    @NotBlank @Size(max = 100, message = "last name too long") String lastName,
    @NotBlank @Size(min = 8, max = 128, message = "password must be 8-128 characters")
        String password) {}
