package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.Permission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Permission}. */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

  List<Permission> findByActiveTrueOrderByModuleAscCodeAsc();

  /**
   * Effective permission codes for a user, aggregated as a set union over all of the user's active
   * roles (logical OR). Only active roles and active permissions are considered.
   */
  @Query(
      "SELECT DISTINCT p.code FROM Permission p "
          + "JOIN RolePermission rp ON rp.permissionId = p.id "
          + "JOIN Role r ON r.id = rp.roleId "
          + "JOIN UserRole ur ON ur.roleId = r.id "
          + "WHERE ur.userId = :userId AND r.active = TRUE AND p.active = TRUE")
  List<String> findActivePermissionCodesForUser(@Param("userId") UUID userId);
}
