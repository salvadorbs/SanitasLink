package com.sanitaslink.patient;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request to update a patient's demographic data. All fields are optional. */
public record UpdatePatientRequest(
    @Size(max = 100, message = "first name too long") String firstName,
    @Size(max = 100, message = "last name too long") String lastName,
    LocalDate birthDate,
    @Size(max = 50, message = "tax identifier too long") String taxIdentifier,
    @Email(message = "must be a valid email address") @Size(max = 320) String email,
    @Size(max = 30, message = "phone too long") String phone,
    @Size(max = 300, message = "address too long") String address) {}
