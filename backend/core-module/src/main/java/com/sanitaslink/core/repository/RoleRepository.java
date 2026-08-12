package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Role}. */
public interface RoleRepository extends JpaRepository<Role, UUID> {

  Optional<Role> findByCode(String code);

  boolean existsByCode(String code);

  List<Role> findByActiveTrueOrderByCode();

  /** Role codes assigned to a user, filtered by scope. Only active roles are returned. */
  @Query(
      "SELECT DISTINCT r.code FROM Role r JOIN UserRole ur ON ur.roleId = r.id "
          + "WHERE ur.userId = :userId AND r.scope = :scope AND r.active = TRUE")
  List<String> findActiveRoleCodesForUser(
      @Param("userId") UUID userId, @Param("scope") String scope);
}
