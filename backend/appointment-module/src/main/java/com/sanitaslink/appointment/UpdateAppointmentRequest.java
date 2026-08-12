package com.sanitaslink.appointment;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request to update (reschedule/edit) an appointment. All fields are optional. */
public record UpdateAppointmentRequest(
    @Size(max = 200, message = "title too long") String title,
    UUID patientId,
    Instant startsAt,
    Instant endsAt,
    @Size(max = 1000, message = "notes too long") String notes) {}
