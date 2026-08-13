package com.sanitaslink.core.auth.dto;

/** Token response returned on login and refresh. The refresh token is never exposed here. */
public record LoginResponse(String accessToken, long expiresInSeconds, String tokenType) {}
