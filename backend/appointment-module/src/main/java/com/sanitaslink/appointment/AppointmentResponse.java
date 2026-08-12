package com.sanitaslink.appointment;

import java.time.Instant;
import java.util.UUID;

/** Appointment view model. */
public record AppointmentResponse(
    UUID id,
    UUID patientId,
    String title,
    Instant startsAt,
    Instant endsAt,
    String status,
    String notes) {}
