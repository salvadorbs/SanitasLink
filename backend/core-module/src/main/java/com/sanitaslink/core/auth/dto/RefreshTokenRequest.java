package com.sanitaslink.core.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Refresh request with the current refresh token. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {}
