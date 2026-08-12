package com.sanitaslink.core.patient;

import java.time.LocalDate;
import java.util.UUID;

/** Patient registry view. Clinical notes are never exposed here. */
public record PatientResponse(
    UUID id,
    String firstName,
    String lastName,
    LocalDate birthDate,
    String email,
    String phone,
    String address) {}
