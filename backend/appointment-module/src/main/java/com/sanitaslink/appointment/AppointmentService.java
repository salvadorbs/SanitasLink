package com.sanitaslink.appointment;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.office.OfficeGuard;
import com.sanitaslink.core.tenant.TenantContextManager;
import com.sanitaslink.patient.PatientRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appointment booking and lifecycle management, each operation audited. */
@Service
public class AppointmentService {

  public static final String STATUS_SCHEDULED = "SCHEDULED";
  public static final String STATUS_CONFIRMED = "CONFIRMED";
  public static final String STATUS_CANCELLED = "CANCELLED";
  public static final String STATUS_MISSED = "MISSED";
  public static final String STATUS_COMPLETED = "COMPLETED";

  private static final Map<String, Set<String>> TRANSITIONS =
      Map.of(
          STATUS_SCHEDULED,
              Set.of(STATUS_CONFIRMED, STATUS_CANCELLED, STATUS_MISSED, STATUS_COMPLETED),
          STATUS_CONFIRMED, Set.of(STATUS_CANCELLED, STATUS_MISSED, STATUS_COMPLETED),
          STATUS_MISSED, Set.of(STATUS_COMPLETED),
          STATUS_CANCELLED, Set.of(),
          STATUS_COMPLETED, Set.of());

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

  @Transactional
  public List<AppointmentResponse> list(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    auditService.record(
        AuditActionType.APPOINTMENT_READ, "APPOINTMENT", null, null, officeId, null);
    return appointmentRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public AppointmentResponse create(UUID officeId, CreateAppointmentRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    validatePeriod(request.startsAt(), request.endsAt());
    requireOfficePatient(request.patientId());
    Appointment appointment = new Appointment();
    appointment.setId(UUID.randomUUID());
    appointment.setOfficeId(officeId);
    appointment.setTitle(request.title().trim());
    appointment.setPatientId(request.patientId());
    appointment.setStartsAt(request.startsAt());
    appointment.setEndsAt(request.endsAt());
    appointment.setStatus(STATUS_SCHEDULED);
    appointment.setNotes(request.notes() == null ? null : request.notes().trim());
    appointmentRepository.save(appointment);
    auditService.record(
        AuditActionType.APPOINTMENT_CREATED,
        "APPOINTMENT",
        appointment.getId().toString(),
        request.patientId(),
        officeId,
        null);
    return toResponse(appointment);
  }

  @Transactional
  public AppointmentResponse update(
      UUID officeId, UUID appointmentId, UpdateAppointmentRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Appointment appointment = requireAppointment(appointmentId);
    if (!canReschedule(appointment.getStatus())) {
      throw transitionConflict(appointment.getStatus());
    }
    Instant startsAt = request.startsAt() != null ? request.startsAt() : appointment.getStartsAt();
    Instant endsAt = request.endsAt() != null ? request.endsAt() : appointment.getEndsAt();
    UUID patientId = request.patientId() != null ? request.patientId() : appointment.getPatientId();
    validatePeriod(startsAt, endsAt);
    requireOfficePatient(patientId);
    if (request.title() != null) {
      appointment.setTitle(request.title().trim());
    }
    if (request.startsAt() != null) {
      appointment.setStartsAt(request.startsAt());
    }
    if (request.endsAt() != null) {
      appointment.setEndsAt(request.endsAt());
    }
    appointment.setPatientId(patientId);
    if (request.notes() != null) {
      appointment.setNotes(request.notes().trim());
    }
    appointmentRepository.save(appointment);
    auditService.record(
        AuditActionType.APPOINTMENT_UPDATED,
        "APPOINTMENT",
        appointment.getId().toString(),
        patientId,
        officeId,
        null);
    return toResponse(appointment);
  }

  @Transactional
  public AppointmentResponse transition(UUID officeId, UUID appointmentId, String targetStatus) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Appointment appointment = requireAppointment(appointmentId);
    String from = appointment.getStatus();
    Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
    if (!allowed.contains(targetStatus)) {
      throw transitionConflict(from);
    }
    if (appointmentRepository.transitionStatus(appointmentId, from, targetStatus) == 0) {
      throw transitionConflict(from);
    }
    appointment.setStatus(targetStatus);
    auditService.record(
        AuditActionType.APPOINTMENT_UPDATED,
        "APPOINTMENT",
        appointment.getId().toString(),
        appointment.getPatientId(),
        officeId,
        "{\"from\":\"" + from + "\",\"to\":\"" + targetStatus + "\"}");
    return toResponse(appointment);
  }

  @Transactional
  public void cancel(UUID officeId, UUID appointmentId) {
    transition(officeId, appointmentId, STATUS_CANCELLED);
  }

  private boolean canReschedule(String status) {
    return STATUS_SCHEDULED.equals(status) || STATUS_CONFIRMED.equals(status);
  }

  private void validatePeriod(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "endsAt must be after startsAt");
    }
  }

  private ApiException transitionConflict(String from) {
    return new ApiException(
        ErrorCodes.OPERATION_CONFLICT,
        HttpStatus.CONFLICT,
        "Cannot transition appointment from status " + from + " to the requested status");
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
