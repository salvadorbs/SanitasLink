package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Assignment of a role to a user. Multiple assignments per user are allowed and aggregate. */
@Entity
@Table(name = "user_roles")
@IdClass(UserRole.UserRoleId.class)
public class UserRole {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "role_id")
  private UUID roleId;

  @Column(name = "assigned_by")
  private UUID assignedBy;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  protected UserRole() {}

  public UserRole(UUID userId, UUID roleId, UUID assignedBy) {
    this.userId = userId;
    this.roleId = roleId;
    this.assignedBy = assignedBy;
    this.assignedAt = Instant.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getAssignedBy() {
    return assignedBy;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  /** Composite primary key for {@link UserRole}. */
  public static class UserRoleId implements Serializable {

    private UUID userId;
    private UUID roleId;

    public UserRoleId() {}

    public UserRoleId(UUID userId, UUID roleId) {
      this.userId = userId;
      this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof UserRoleId that)) {
        return false;
      }
      return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, roleId);
    }
  }
}
