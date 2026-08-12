package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An office-scoped invitation to join the office with a predefined role. */
@Entity
@Table(name = "office_invitations")
public class OfficeInvitation {

  @Id private UUID id;

  @Column(name = "office_id", nullable = false)
  private UUID officeId;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_by")
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public static OfficeInvitation create(
      UUID id,
      UUID officeId,
      String email,
      UUID roleId,
      String tokenHash,
      Instant expiresAt,
      UUID createdByUserId) {
    OfficeInvitation invitation = new OfficeInvitation();
    invitation.id = id;
    invitation.officeId = officeId;
    invitation.email = User.normalizeEmail(email);
    invitation.roleId = roleId;
    invitation.tokenHash = tokenHash;
    invitation.expiresAt = expiresAt;
    invitation.status = InvitationStatus.PENDING;
    invitation.createdByUserId = createdByUserId;
    return invitation;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOfficeId() {
    return officeId;
  }

  public String getEmail() {
    return email;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(Instant acceptedAt) {
    this.acceptedAt = acceptedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
