package com.sanitaslink.appointment;

import jakarta.validation.constraints.NotBlank;

/** Request to transition an appointment to a target status. */
public record ChangeAppointmentStatusRequest(
    @NotBlank(message = "status is required") String status) {}
