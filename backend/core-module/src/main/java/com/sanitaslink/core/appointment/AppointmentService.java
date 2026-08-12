package com.sanitaslink.core.appointment;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.office.OfficeGuard;
import com.sanitaslink.core.patient.PatientRepository;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appointment booking and management, each operation audited. */
@Service
public class AppointmentService {

  private static final String STATUS_SCHEDULED = "SCHEDULED";
  private static final String STATUS_CANCELLED = "CANCELLED";

  private final AppointmentRepository appointmentRepository;
  private final PatientRepository patientRepository;
  private final OfficeGuard officeGuard;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public AppointmentService(
      AppointmentRepository appointmentRepository,
      PatientRepository patientRepository,
      OfficeGuard officeGuard,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.appointmentRepository = appointmentRepository;
    this.patientRepository = patientRepository;
    this.officeGuard = officeGuard;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<AppointmentResponse> list(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    return appointmentRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public AppointmentResponse create(UUID officeId, CreateAppointmentRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "endsAt must be after startsAt");
    }
    requireOfficePatient(request.patientId());
    Appointment appointment = new Appointment();
    appointment.setId(UUID.randomUUID());
    appointment.setOfficeId(officeId);
    appointment.setTitle(request.title());
    appointment.setPatientId(request.patientId());
    appointment.setStartsAt(request.startsAt());
    appointment.setEndsAt(request.endsAt());
    appointment.setStatus(STATUS_SCHEDULED);
    appointment.setNotes(request.notes());
    appointmentRepository.save(appointment);
    auditService.record(
        AuditActionType.APPOINTMENT_CREATED,
        "APPOINTMENT",
        appointment.getId().toString(),
        request.patientId());
    return toResponse(appointment);
  }

  @Transactional
  public void cancel(UUID officeId, UUID appointmentId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Appointment appointment = requireAppointment(appointmentId);
    appointment.setStatus(STATUS_CANCELLED);
    appointmentRepository.save(appointment);
    auditService.record(
        AuditActionType.APPOINTMENT_CANCELLED,
        "APPOINTMENT",
        appointment.getId().toString(),
        appointment.getPatientId());
  }

  private Appointment requireAppointment(UUID appointmentId) {
    return appointmentRepository
        .findById(appointmentId)
        .orElseThrow(
            () -> ApiException.notFound(ErrorCodes.OPERATION_CONFLICT, "Appointment not found"));
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

  private AppointmentResponse toResponse(Appointment appointment) {
    return new AppointmentResponse(
        appointment.getId(),
        appointment.getPatientId(),
        appointment.getTitle(),
        appointment.getStartsAt(),
        appointment.getEndsAt(),
        appointment.getStatus(),
        appointment.getNotes());
  }
}
