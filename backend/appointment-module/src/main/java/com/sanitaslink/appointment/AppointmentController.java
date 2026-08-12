package com.sanitaslink.appointment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Appointment endpoints. */
@RestController
@RequestMapping("/api/v1/offices/{officeId}/appointments")
@Tag(name = "Appointments", description = "Office agenda and appointments")
public class AppointmentController {

  private final AppointmentService appointmentService;

  public AppointmentController(AppointmentService appointmentService) {
    this.appointmentService = appointmentService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
  @Operation(summary = "List office appointments")
  public ResponseEntity<List<AppointmentResponse>> list(@PathVariable UUID officeId) {
    return ResponseEntity.ok(appointmentService.list(officeId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('APPOINTMENT_CREATE')")
  @Operation(summary = "Book an appointment")
  public ResponseEntity<AppointmentResponse> create(
      @PathVariable UUID officeId, @Valid @RequestBody CreateAppointmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(appointmentService.create(officeId, request));
  }

  @PatchMapping("/{appointmentId}")
  @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE')")
  @Operation(summary = "Update / reschedule an appointment")
  public ResponseEntity<AppointmentResponse> update(
      @PathVariable UUID officeId,
      @PathVariable UUID appointmentId,
      @Valid @RequestBody UpdateAppointmentRequest request) {
    return ResponseEntity.ok(appointmentService.update(officeId, appointmentId, request));
  }

  @PatchMapping("/{appointmentId}/status")
  @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE')")
  @Operation(summary = "Transition an appointment to a new status")
  public ResponseEntity<AppointmentResponse> transition(
      @PathVariable UUID officeId,
      @PathVariable UUID appointmentId,
      @Valid @RequestBody ChangeAppointmentStatusRequest request) {
    return ResponseEntity.ok(
        appointmentService.transition(officeId, appointmentId, request.status()));
  }

  @DeleteMapping("/{appointmentId}")
  @PreAuthorize("hasAuthority('APPOINTMENT_CANCEL')")
  @Operation(summary = "Cancel an appointment")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID officeId, @PathVariable UUID appointmentId) {
    appointmentService.cancel(officeId, appointmentId);
    return ResponseEntity.noContent().build();
  }
}
