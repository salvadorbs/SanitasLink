package com.sanitaslink.core.office.dto;

import java.util.List;
import java.util.UUID;

/** Office member view with assigned office roles. */
public record OfficeMemberResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String membershipStatus,
    List<String> roles) {}
