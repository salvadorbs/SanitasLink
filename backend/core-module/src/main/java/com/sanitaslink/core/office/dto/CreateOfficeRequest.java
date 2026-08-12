package com.sanitaslink.core.office.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to provision a new office with its titular doctor. */
public record CreateOfficeRequest(
    @NotBlank @Size(max = 150, message = "name too long") String name,
    @Size(max = 200, message = "legal name too long") String legalName,
    @Size(max = 50, message = "tax identifier too long") String taxIdentifier,
    @Email(message = "must be a valid email address") @Size(max = 320) String email,
    @Size(max = 30, message = "phone too long") String phone,
    @Size(max = 300, message = "address too long") String address,
    @NotBlank @Email(message = "must be a valid email address") String ownerEmail,
    @NotBlank @Size(max = 100, message = "owner first name too long") String ownerFirstName,
    @NotBlank @Size(max = 100, message = "owner last name too long") String ownerLastName,
    @Size(max = 30, message = "owner phone too long") String ownerPhone) {}
