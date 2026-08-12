package com.sanitaslink.core.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request to start the password reset flow. */
public record RequestPasswordResetRequest(
    @NotBlank @Email(message = "must be a valid email address") String email) {}
