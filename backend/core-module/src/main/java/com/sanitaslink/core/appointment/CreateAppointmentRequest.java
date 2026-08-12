package com.sanitaslink.core.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request to book an appointment. */
public record CreateAppointmentRequest(
    @NotBlank @Size(max = 200, message = "title too long") String title,
    UUID patientId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @Size(max = 1000, message = "notes too long") String notes) {}
