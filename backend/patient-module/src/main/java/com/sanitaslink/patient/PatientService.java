package com.sanitaslink.patient;

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

  @Transactional
  public List<PatientResponse> list(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    auditService.record(AuditActionType.PATIENT_READ, "PATIENT", null, null, officeId, null);
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
        AuditActionType.PATIENT_CREATED,
        "PATIENT",
        patient.getId().toString(),
        patient.getId(),
        officeId,
        null);
    return toResponse(patient);
  }

  @Transactional
  public PatientResponse get(UUID officeId, UUID patientId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    auditService.record(
        AuditActionType.PATIENT_READ,
        "PATIENT",
        patient.getId().toString(),
        patient.getId(),
        officeId,
        null);
    return toResponse(patient);
  }

  @Transactional
  public PatientResponse update(UUID officeId, UUID patientId, UpdatePatientRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    if (request.firstName() != null) {
      patient.setFirstName(request.firstName().trim());
    }
    if (request.lastName() != null) {
      patient.setLastName(request.lastName().trim());
    }
    if (request.birthDate() != null) {
      patient.setBirthDate(request.birthDate());
    }
    if (request.taxIdentifier() != null) {
      patient.setTaxIdentifier(request.taxIdentifier().trim());
    }
    if (request.email() != null) {
      patient.setEmail(request.email().trim());
    }
    if (request.phone() != null) {
      patient.setPhone(request.phone().trim());
    }
    if (request.address() != null) {
      patient.setAddress(request.address().trim());
    }
    patientRepository.save(patient);
    auditService.record(
        AuditActionType.PATIENT_UPDATED,
        "PATIENT",
        patient.getId().toString(),
        patient.getId(),
        officeId,
        null);
    return toResponse(patient);
  }

  @Transactional
  public ClinicalNotesResponse getClinicalNotes(UUID officeId, UUID patientId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Patient patient = requirePatient(patientId);
    auditService.record(
        AuditActionType.PATIENT_CLINICAL_READ,
        "PATIENT",
        patient.getId().toString(),
        patient.getId(),
        officeId,
        null);
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
        patient.getId(),
        officeId,
        null);
    return new ClinicalNotesResponse(patient.getId(), patient.getClinicalNotes());
  }

  private Patient requirePatient(UUID patientId) {
    return patientRepository
        .findById(patientId)
        .orElseThrow(
            () -> ApiException.notFound(ErrorCodes.OPERATION_CONFLICT, "Patient not found"));
  }

  private void apply(Patient patient, CreatePatientRequest request) {
    patient.setFirstName(request.firstName().trim());
    patient.setLastName(request.lastName().trim());
    patient.setBirthDate(request.birthDate());
    patient.setTaxIdentifier(
        request.taxIdentifier() == null ? null : request.taxIdentifier().trim());
    patient.setEmail(request.email() == null ? null : request.email().trim());
    patient.setPhone(request.phone() == null ? null : request.phone().trim());
    patient.setAddress(request.address() == null ? null : request.address().trim());
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
