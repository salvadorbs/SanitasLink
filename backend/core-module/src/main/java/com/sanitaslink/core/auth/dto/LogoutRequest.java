package com.sanitaslink.core.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Logout request with the refresh token to revoke. */
public record LogoutRequest(@NotBlank String refreshToken) {}
