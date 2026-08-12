package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Global role-to-permission relation. */
@Entity
@Table(name = "role_permissions")
@IdClass(RolePermission.RolePermissionId.class)
public class RolePermission {

  @Id
  @Column(name = "role_id")
  private UUID roleId;

  @Id
  @Column(name = "permission_id")
  private UUID permissionId;

  protected RolePermission() {}

  public RolePermission(UUID roleId, UUID permissionId) {
    this.roleId = roleId;
    this.permissionId = permissionId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getPermissionId() {
    return permissionId;
  }

  /** Composite primary key for {@link RolePermission}. */
  public static class RolePermissionId implements Serializable {

    private UUID roleId;
    private UUID permissionId;

    public RolePermissionId() {}

    public RolePermissionId(UUID roleId, UUID permissionId) {
      this.roleId = roleId;
      this.permissionId = permissionId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RolePermissionId that)) {
        return false;
      }
      return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(roleId, permissionId);
    }
  }
}
