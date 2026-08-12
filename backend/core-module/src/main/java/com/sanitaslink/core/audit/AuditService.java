package com.sanitaslink.core.audit;

import com.sanitaslink.core.repository.AuditEventRepository;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records immutable audit events. Written in the caller's transaction (MANDATORY) so audit and the
 * audited operation commit or roll back atomically.
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
    AuditEvent event =
        AuditEvent.builder()
            .officeId(context != null ? context.officeId() : null)
            .operatorId(context != null ? context.userId() : null)
            .actionType(actionType)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .patientId(patientId)
            .metadata(metadata)
            .build();
    auditEventRepository.save(event);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordAs(UUID operatorId, String actionType, String resourceType, String resourceId) {
    TenantContext context = TenantContextHolder.get();
    AuditEvent event =
        AuditEvent.builder()
            .officeId(context != null ? context.officeId() : null)
            .operatorId(operatorId)
            .actionType(actionType)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .build();
    auditEventRepository.save(event);
  }
}
