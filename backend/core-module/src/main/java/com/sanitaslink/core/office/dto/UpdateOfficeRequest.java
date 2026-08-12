package com.sanitaslink.core.office.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Request to update office information. All fields are optional. */
public record UpdateOfficeRequest(
    @Size(max = 150, message = "name too long") String name,
    @Size(max = 200, message = "legal name too long") String legalName,
    @Size(max = 50, message = "tax identifier too long") String taxIdentifier,
    @Email(message = "must be a valid email address") @Size(max = 320) String email,
    @Size(max = 30, message = "phone too long") String phone,
    @Size(max = 300, message = "address too long") String address) {}
