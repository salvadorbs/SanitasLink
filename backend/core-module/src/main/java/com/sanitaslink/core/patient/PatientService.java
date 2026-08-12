package com.sanitaslink.core.patient;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.office.OfficeGuard;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Patient registry and clinical record operations, each audited. */
@Service
public class PatientService {

  private final PatientRepository patientRepository;
  private final OfficeGuard officeGuard;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public PatientService(
      PatientRepository patientRepository,
      OfficeGuard officeGuard,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.patientRepository = patientRepository;
    this.officeGuard = officeGuard;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<PatientResponse> list(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    return patientRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public PatientResponse create(UUID officeId, CreatePatientRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = new Patient();
    patient.setId(UUID.randomUUID());
    patient.setOfficeId(officeId);
    apply(patient, request);
    patientRepository.save(patient);
    auditService.record(
        AuditActionType.PATIENT_CREATED, "PATIENT", patient.getId().toString(), patient.getId());
    return toResponse(patient);
  }

  @Transactional(readOnly = true)
  public PatientResponse get(UUID officeId, UUID patientId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    auditService.record(
        AuditActionType.PATIENT_READ, "PATIENT", patient.getId().toString(), patient.getId());
    return toResponse(patient);
  }

  @Transactional
  public PatientResponse update(UUID officeId, UUID patientId, UpdatePatientRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    if (request.firstName() != null) {
      patient.setFirstName(request.firstName());
    }
    if (request.lastName() != null) {
      patient.setLastName(request.lastName());
    }
    if (request.birthDate() != null) {
      patient.setBirthDate(request.birthDate());
    }
    if (request.taxIdentifier() != null) {
      patient.setTaxIdentifier(request.taxIdentifier());
    }
    if (request.email() != null) {
      patient.setEmail(request.email());
    }
    if (request.phone() != null) {
      patient.setPhone(request.phone());
    }
    if (request.address() != null) {
      patient.setAddress(request.address());
    }
    patientRepository.save(patient);
    auditService.record(
        AuditActionType.PATIENT_UPDATED, "PATIENT", patient.getId().toString(), patient.getId());
    return toResponse(patient);
  }

  @Transactional(readOnly = true)
  public ClinicalNotesResponse getClinicalNotes(UUID officeId, UUID patientId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    auditService.record(
        AuditActionType.PATIENT_CLINICAL_READ,
        "PATIENT",
        patient.getId().toString(),
        patient.getId());
    return new ClinicalNotesResponse(patient.getId(), patient.getClinicalNotes());
  }

  @Transactional
  public ClinicalNotesResponse updateClinicalNotes(
      UUID officeId, UUID patientId, ClinicalNotesResponse.Update update) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    patient.setClinicalNotes(update.clinicalNotes());
    patientRepository.save(patient);
    auditService.record(
        AuditActionType.PATIENT_CLINICAL_WRITE,
        "PATIENT",
        patient.getId().toString(),
        patient.getId());
    return new ClinicalNotesResponse(patient.getId(), patient.getClinicalNotes());
  }

  private Patient requirePatient(UUID patientId) {
    return patientRepository
        .findById(patientId)
        .orElseThrow(
            () -> ApiException.notFound(ErrorCodes.OPERATION_CONFLICT, "Patient not found"));
  }

  private void apply(Patient patient, CreatePatientRequest request) {
    patient.setFirstName(request.firstName());
    patient.setLastName(request.lastName());
    patient.setBirthDate(request.birthDate());
    patient.setTaxIdentifier(request.taxIdentifier());
    patient.setEmail(request.email());
    patient.setPhone(request.phone());
    patient.setAddress(request.address());
  }

  private PatientResponse toResponse(Patient patient) {
    return new PatientResponse(
        patient.getId(),
        patient.getFirstName(),
        patient.getLastName(),
        patient.getBirthDate(),
        patient.getEmail(),
        patient.getPhone(),
        patient.getAddress());
  }
}
