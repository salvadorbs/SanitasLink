package com.sanitaslink.prescription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prescription endpoints. */
@RestController
@RequestMapping("/api/v1/offices/{officeId}/prescriptions")
@Tag(name = "Prescriptions", description = "Prescription requests and issued tickets")
public class PrescriptionController {

  private final PrescriptionService prescriptionService;

  public PrescriptionController(PrescriptionService prescriptionService) {
    this.prescriptionService = prescriptionService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PRESCRIPTION_READ')")
  @Operation(summary = "List prescriptions of the office")
  public ResponseEntity<List<PrescriptionResponse>> list(@PathVariable UUID officeId) {
    return ResponseEntity.ok(prescriptionService.list(officeId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PRESCRIPTION_REQUEST_CREATE')")
  @Operation(summary = "Create a prescription request")
  public ResponseEntity<PrescriptionResponse> request(
      @PathVariable UUID officeId, @Valid @RequestBody RequestPrescriptionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(prescriptionService.request(officeId, request));
  }

  @PatchMapping("/{prescriptionId}/issue")
  @PreAuthorize("hasAuthority('PRESCRIPTION_WRITE')")
  @Operation(summary = "Issue (sign) a prescription request")
  public ResponseEntity<PrescriptionResponse> issue(
      @PathVariable UUID officeId, @PathVariable UUID prescriptionId) {
    return ResponseEntity.ok(prescriptionService.issue(officeId, prescriptionId));
  }

  @PatchMapping("/{prescriptionId}/print")
  @PreAuthorize("hasAuthority('PRESCRIPTION_PRINT')")
  @Operation(summary = "Print / send a prescription")
  public ResponseEntity<PrescriptionResponse> print(
      @PathVariable UUID officeId, @PathVariable UUID prescriptionId) {
    return ResponseEntity.ok(prescriptionService.print(officeId, prescriptionId));
  }
}
