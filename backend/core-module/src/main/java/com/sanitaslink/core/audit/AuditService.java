package com.sanitaslink.core.audit;

import com.sanitaslink.core.repository.AuditEventRepository;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records immutable audit events. Written in the caller's transaction (MANDATORY) so audit and the
 * audited operation commit or roll back atomically. Populates the mandatory request metadata (IP,
 * user agent, correlation id) from the current request when available.
 */
@Service
public class AuditService {

  private final AuditEventRepository auditEventRepository;

  public AuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String actionType, String resourceType, String resourceId, UUID patientId) {
    record(actionType, resourceType, resourceId, patientId, null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      String actionType, String resourceType, String resourceId, UUID patientId, String metadata) {
    TenantContext context = TenantContextHolder.get();
    RequestMetadata request = requestMetadata();
    AuditEvent event =
        AuditEvent.builder()
            .officeId(context != null ? context.officeId() : null)
            .operatorId(context != null ? context.userId() : null)
            .actionType(actionType)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .patientId(patientId)
            .ipAddress(request.ipAddress())
            .userAgent(request.userAgent())
            .correlationId(request.correlationId())
            .metadata(metadata)
            .build();
    auditEventRepository.save(event);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordAs(UUID operatorId, String actionType, String resourceType, String resourceId) {
    TenantContext context = TenantContextHolder.get();
    RequestMetadata request = requestMetadata();
    AuditEvent event =
        AuditEvent.builder()
            .officeId(context != null ? context.officeId() : null)
            .operatorId(operatorId)
            .actionType(actionType)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .ipAddress(request.ipAddress())
            .userAgent(request.userAgent())
            .correlationId(request.correlationId())
            .build();
    auditEventRepository.save(event);
  }

  private RequestMetadata requestMetadata() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servlet)) {
      return new RequestMetadata(null, null, null);
    }
    HttpServletRequest request = servlet.getRequest();
    String correlationId = request.getHeader("X-Correlation-Id");
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    return new RequestMetadata(
        request.getRemoteAddr(),
        truncate(request.getHeader("User-Agent"), 255),
        truncate(correlationId, 100));
  }

  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private record RequestMetadata(String ipAddress, String userAgent, String correlationId) {}
}
