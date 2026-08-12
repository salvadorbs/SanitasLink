package com.sanitaslink.core.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Login request. */
public record LoginRequest(
    @NotBlank @Email(message = "must be a valid email address") String email,
    @NotBlank String password) {}
