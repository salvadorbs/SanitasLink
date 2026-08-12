package com.sanitaslink.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirms a password reset with the one-time token and a new password. */
public record ConfirmPasswordResetRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 128, message = "new password must be 8-128 characters")
        String newPassword) {}
