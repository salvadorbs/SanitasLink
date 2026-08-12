package com.sanitaslink.core.auth.dto;

/** Token pair returned on login and refresh. */
public record LoginResponse(
    String accessToken, String refreshToken, long expiresInSeconds, String tokenType) {}
