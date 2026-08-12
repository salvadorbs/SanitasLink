package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's membership of an office. The primary key on {@code user_id} enforces the mono-office
 * invariant: a user can belong to at most one office.
 */
@Entity
@Table(name = "office_memberships")
public class OfficeMembership extends AbstractBaseEntity {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "office_id", nullable = false)
  private UUID officeId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "invited_at")
  private Instant invitedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public static OfficeMembership invited(UUID userId, UUID officeId) {
    OfficeMembership membership = new OfficeMembership();
    membership.userId = userId;
    membership.officeId = officeId;
    membership.status = MembershipStatus.INVITED;
    membership.invitedAt = Instant.now();
    return membership;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getOfficeId() {
    return officeId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getInvitedAt() {
    return invitedAt;
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
}
