package com.sanitaslink.core.prescription;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.office.OfficeGuard;
import com.sanitaslink.core.patient.PatientRepository;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Prescription request/issue/print workflow, each operation audited. */
@Service
public class PrescriptionService {

  private static final String STATUS_REQUESTED = "REQUESTED";
  private static final String STATUS_ISSUED = "ISSUED";
  private static final String STATUS_PRINTED = "PRINTED";

  private final PrescriptionRepository prescriptionRepository;
  private final PatientRepository patientRepository;
  private final OfficeGuard officeGuard;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public PrescriptionService(
      PrescriptionRepository prescriptionRepository,
      PatientRepository patientRepository,
      OfficeGuard officeGuard,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.prescriptionRepository = prescriptionRepository;
    this.patientRepository = patientRepository;
    this.officeGuard = officeGuard;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<PrescriptionResponse> list(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    return prescriptionRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public PrescriptionResponse request(UUID officeId, RequestPrescriptionRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    requireOfficePatient(request.patientId());
    Prescription prescription = new Prescription();
    prescription.setId(UUID.randomUUID());
    prescription.setOfficeId(officeId);
    prescription.setPatientId(request.patientId());
    prescription.setStatus(STATUS_REQUESTED);
    prescription.setMedication(request.medication());
    prescription.setInstructions(request.instructions());
    prescriptionRepository.save(prescription);
    auditService.record(
        AuditActionType.PRESCRIPTION_REQUESTED,
        "PRESCRIPTION",
        prescription.getId().toString(),
        request.patientId());
    return toResponse(prescription);
  }

  @Transactional
  public PrescriptionResponse issue(UUID officeId, UUID prescriptionId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Prescription prescription = requirePrescription(prescriptionId);
    if (!STATUS_REQUESTED.equals(prescription.getStatus())) {
      throw new ApiException(
          ErrorCodes.OPERATION_CONFLICT,
          HttpStatus.CONFLICT,
          "Only requested prescriptions can be issued");
    }
    prescription.setStatus(STATUS_ISSUED);
    prescription.setIssuedAt(Instant.now());
    prescriptionRepository.save(prescription);
    auditService.record(
        AuditActionType.PRESCRIPTION_ISSUED,
        "PRESCRIPTION",
        prescription.getId().toString(),
        prescription.getPatientId());
    return toResponse(prescription);
  }

  @Transactional
  public PrescriptionResponse print(UUID officeId, UUID prescriptionId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Prescription prescription = requirePrescription(prescriptionId);
    if (!STATUS_ISSUED.equals(prescription.getStatus())
        && !STATUS_PRINTED.equals(prescription.getStatus())) {
      throw new ApiException(
          ErrorCodes.OPERATION_CONFLICT,
          HttpStatus.CONFLICT,
          "Only issued prescriptions can be printed");
    }
    prescription.setStatus(STATUS_PRINTED);
    prescription.setPrintedAt(Instant.now());
    prescriptionRepository.save(prescription);
    auditService.record(
        AuditActionType.PRESCRIPTION_PRINTED,
        "PRESCRIPTION",
        prescription.getId().toString(),
        prescription.getPatientId());
    return toResponse(prescription);
  }

  private Prescription requirePrescription(UUID prescriptionId) {
    return prescriptionRepository
        .findById(prescriptionId)
        .orElseThrow(
            () -> ApiException.notFound(ErrorCodes.OPERATION_CONFLICT, "Prescription not found"));
  }

  private void requireOfficePatient(UUID patientId) {
    if (patientId == null) {
      return;
    }
    // RLS scopes patients to the current office, so cross-office references are rejected.
    if (!patientRepository.existsById(patientId)) {
      throw ApiException.notFound(ErrorCodes.OPERATION_CONFLICT, "Patient not found");
    }
  }

  private PrescriptionResponse toResponse(Prescription prescription) {
    return new PrescriptionResponse(
        prescription.getId(),
        prescription.getPatientId(),
        prescription.getStatus(),
        prescription.getMedication(),
        prescription.getInstructions(),
        prescription.getIssuedAt(),
        prescription.getPrintedAt());
  }
}
