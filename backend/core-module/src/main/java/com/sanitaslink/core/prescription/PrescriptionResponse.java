package com.sanitaslink.core.prescription;

import java.time.Instant;
import java.util.UUID;

/** Prescription view model. */
public record PrescriptionResponse(
    UUID id,
    UUID patientId,
    String status,
    String medication,
    String instructions,
    Instant issuedAt,
    Instant printedAt) {}
