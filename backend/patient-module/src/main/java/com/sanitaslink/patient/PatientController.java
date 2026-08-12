package com.sanitaslink.patient;

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

/** Patient registry and clinical record endpoints. */
@RestController
@RequestMapping("/api/v1/offices/{officeId}/patients")
@Tag(name = "Patients", description = "Patient registry and clinical records")
public class PatientController {

  private final PatientService patientService;

  public PatientController(PatientService patientService) {
    this.patientService = patientService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PATIENT_REGISTRY_READ')")
  @Operation(summary = "List patients of the office")
  public ResponseEntity<List<PatientResponse>> list(@PathVariable UUID officeId) {
    return ResponseEntity.ok(patientService.list(officeId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PATIENT_REGISTRY_CREATE')")
  @Operation(summary = "Register a new patient")
  public ResponseEntity<PatientResponse> create(
      @PathVariable UUID officeId, @Valid @RequestBody CreatePatientRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(officeId, request));
  }

  @GetMapping("/{patientId}")
  @PreAuthorize("hasAuthority('PATIENT_REGISTRY_READ')")
  @Operation(summary = "View a patient's registry data")
  public ResponseEntity<PatientResponse> get(
      @PathVariable UUID officeId, @PathVariable UUID patientId) {
    return ResponseEntity.ok(patientService.get(officeId, patientId));
  }

  @PatchMapping("/{patientId}")
  @PreAuthorize("hasAuthority('PATIENT_REGISTRY_UPDATE')")
  @Operation(summary = "Update a patient's registry data")
  public ResponseEntity<PatientResponse> update(
      @PathVariable UUID officeId,
      @PathVariable UUID patientId,
      @Valid @RequestBody UpdatePatientRequest request) {
    return ResponseEntity.ok(patientService.update(officeId, patientId, request));
  }

  @GetMapping("/{patientId}/clinical")
  @PreAuthorize("hasAuthority('PATIENT_CLINICAL_READ')")
  @Operation(summary = "Read the patient's clinical record")
  public ResponseEntity<ClinicalNotesResponse> getClinical(
      @PathVariable UUID officeId, @PathVariable UUID patientId) {
    return ResponseEntity.ok(patientService.getClinicalNotes(officeId, patientId));
  }

  @PatchMapping("/{patientId}/clinical")
  @PreAuthorize("hasAuthority('PATIENT_CLINICAL_WRITE')")
  @Operation(summary = "Write the patient's clinical record")
  public ResponseEntity<ClinicalNotesResponse> updateClinical(
      @PathVariable UUID officeId,
      @PathVariable UUID patientId,
      @Valid @RequestBody ClinicalNotesResponse.Update update) {
    return ResponseEntity.ok(patientService.updateClinicalNotes(officeId, patientId, update));
  }
}
