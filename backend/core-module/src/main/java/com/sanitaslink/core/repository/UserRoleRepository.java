package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link UserRole}. */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.UserRoleId> {

  List<UserRole> findByUserId(UUID userId);

  boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

  long deleteByUserId(UUID userId);

  /** Role ids assigned to a user, filtered by role scope. */
  @Query(
      "SELECT ur.roleId FROM UserRole ur JOIN Role r ON r.id = ur.roleId "
          + "WHERE ur.userId = :userId AND r.scope = :scope")
  List<UUID> findRoleIdsByUserIdAndScope(
      @Param("userId") UUID userId, @Param("scope") String scope);

  /**
   * Counts active office members currently assigned the given role code in the office. Used to
   * protect the invariant that the last practice owner cannot be removed.
   */
  @Query(
      "SELECT COUNT(ur) FROM UserRole ur "
          + "JOIN OfficeMembership om ON om.userId = ur.userId "
          + "JOIN Role r ON r.id = ur.roleId "
          + "WHERE om.officeId = :officeId AND om.status = :membershipStatus "
          + "AND r.code = :roleCode AND r.active = TRUE")
  long countActiveMembersWithRoleInOffice(
      @Param("officeId") UUID officeId,
      @Param("membershipStatus") String membershipStatus,
      @Param("roleCode") String roleCode);
}
