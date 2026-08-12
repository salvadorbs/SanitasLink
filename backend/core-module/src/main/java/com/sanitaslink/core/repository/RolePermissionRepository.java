package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link RolePermission}. */
public interface RolePermissionRepository
    extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {}
