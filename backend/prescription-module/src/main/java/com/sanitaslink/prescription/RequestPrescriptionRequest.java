package com.sanitaslink.prescription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request to create a prescription request on behalf of a patient. */
public record RequestPrescriptionRequest(
    @NotBlank(message = "medication is required") @Size(max = 300, message = "medication too long")
        String medication,
    @Size(max = 10000, message = "instructions too long") String instructions,
    @NotNull(message = "patientId is required") UUID patientId) {}
