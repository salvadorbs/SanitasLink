package com.sanitaslink.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable audit event. Database triggers forbid UPDATE and DELETE. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id private UUID id;

  @Column(name = "office_id")
  private UUID officeId;

  @Column(name = "operator_id")
  private UUID operatorId;

  @Column(name = "action_type", nullable = false, length = 50)
  private String actionType;

  @Column(name = "resource_type", length = 50)
  private String resourceType;

  @Column(name = "resource_id", length = 100)
  private String resourceId;

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "correlation_id", length = 100)
  private String correlationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata")
  private String metadata;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  public static AuditEventBuilder builder() {
    return new AuditEventBuilder();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOfficeId() {
    return officeId;
  }

  public UUID getOperatorId() {
    return operatorId;
  }

  public String getActionType() {
    return actionType;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getMetadata() {
    return metadata;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  /** Fluent builder for {@link AuditEvent}. */
  public static final class AuditEventBuilder {

    private UUID officeId;
    private UUID operatorId;
    private String actionType;
    private String resourceType;
    private String resourceId;
    private UUID patientId;
    private String ipAddress;
    private String userAgent;
    private String correlationId;
    private String metadata;

    private AuditEventBuilder() {}

    public AuditEventBuilder officeId(UUID officeId) {
      this.officeId = officeId;
      return this;
    }

    public AuditEventBuilder operatorId(UUID operatorId) {
      this.operatorId = operatorId;
      return this;
    }

    public AuditEventBuilder actionType(String actionType) {
      this.actionType = actionType;
      return this;
    }

    public AuditEventBuilder resourceType(String resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public AuditEventBuilder resourceId(String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public AuditEventBuilder patientId(UUID patientId) {
      this.patientId = patientId;
      return this;
    }

    public AuditEventBuilder ipAddress(String ipAddress) {
      this.ipAddress = ipAddress;
      return this;
    }

    public AuditEventBuilder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public AuditEventBuilder correlationId(String correlationId) {
      this.correlationId = correlationId;
      return this;
    }

    public AuditEventBuilder metadata(String metadata) {
      this.metadata = metadata;
      return this;
    }

    public AuditEvent build() {
      AuditEvent event = new AuditEvent();
      event.id = UUID.randomUUID();
      event.officeId = officeId;
      event.operatorId = operatorId;
      event.actionType = actionType;
      event.resourceType = resourceType;
      event.resourceId = resourceId;
      event.patientId = patientId;
      event.ipAddress = ipAddress;
      event.userAgent = userAgent;
      event.correlationId = correlationId;
      event.metadata = metadata;
      event.occurredAt = Instant.now();
      return event;
    }
  }
}
