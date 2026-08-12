package com.sanitaslink.core.office.dto;

import java.time.Instant;
import java.util.UUID;

/** Office view model. */
public record OfficeResponse(
    UUID id,
    String name,
    String legalName,
    String taxIdentifier,
    String email,
    String phone,
    String address,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
