package com.sanitaslink.core.security;

import com.sanitaslink.core.domain.MembershipStatus;
import com.sanitaslink.core.domain.RoleScope;
import com.sanitaslink.core.domain.User;
import com.sanitaslink.core.domain.UserStatus;
import com.sanitaslink.core.repository.OfficeMembershipRepository;
import com.sanitaslink.core.repository.PermissionRepository;
import com.sanitaslink.core.repository.RoleRepository;
import com.sanitaslink.core.repository.UserRepository;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import com.sanitaslink.core.tenant.TenantContextManager;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the current authorization state for an authenticated user directly from the database, so
 * role and permission changes take effect immediately. Runs inside a transaction that first applies
 * a minimal self-read context, then re-applies the full context once the membership is known. This
 * makes the resolution correct even under PostgreSQL RLS.
 */
@Service
public class PermissionResolver {

  private final UserRepository userRepository;
  private final OfficeMembershipRepository membershipRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final TenantContextManager tenantContextManager;
  private final EntityManager entityManager;

  public PermissionResolver(
      UserRepository userRepository,
      OfficeMembershipRepository membershipRepository,
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      TenantContextManager tenantContextManager,
      EntityManager entityManager) {
    this.userRepository = userRepository;
    this.membershipRepository = membershipRepository;
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.tenantContextManager = tenantContextManager;
    this.entityManager = entityManager;
  }

  /** Resolution result for an authenticated user. */
  public record Resolution(
      UUID userId,
      String email,
      UUID officeId,
      boolean active,
      boolean admin,
      int securityVersion,
      List<String> roles,
      Set<String> permissions) {

    static Resolution inactive(UUID userId, String email) {
      return new Resolution(userId, email, null, false, false, 0, List.of(), Set.of());
    }
  }

  @Transactional(readOnly = true)
  public Resolution resolve(UUID userId) {
    // Flush any pending writes from the surrounding transaction BEFORE switching the RLS
    // context, so they are committed to the database under the caller's office context.
    entityManager.flush();
    // Phase 1: self-read context so RLS lets the user see their own membership and roles.
    tenantContextManager.initialize(null, userId, false);

    User user = userRepository.findById(userId).orElse(null);
    if (user == null) {
      return Resolution.inactive(userId, null);
    }
    if (!UserStatus.ACTIVE.equals(user.getStatus())) {
      return Resolution.inactive(userId, user.getEmail());
    }

    var activeMembership =
        membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
    UUID officeId = activeMembership.map(m -> m.getOfficeId()).orElse(null);

    List<String> platformRoles =
        roleRepository.findActiveRoleCodesForUser(userId, RoleScope.PLATFORM);
    boolean admin = platformRoles.stream().anyMatch("ADMIN"::equals);

    // Phase 2: full context (office + admin flag) once both are known.
    tenantContextManager.initialize(officeId, userId, admin);

    List<String> officeRoles = roleRepository.findActiveRoleCodesForUser(userId, RoleScope.OFFICE);
    List<String> permissionCodes = permissionRepository.findActivePermissionCodesForUser(userId);

    Set<String> roles = new LinkedHashSet<>();
    roles.addAll(platformRoles);
    roles.addAll(officeRoles);

    int securityVersion = user.getSecurityVersion() == null ? 0 : user.getSecurityVersion();
    return new Resolution(
        userId,
        user.getEmail(),
        officeId,
        true,
        admin,
        securityVersion,
        List.copyOf(roles),
        Set.copyOf(permissionCodes));
  }

  /** Rebuilds a {@link TenantContext} from a resolution and publishes it to the holder. */
  public TenantContext toContext(Resolution resolution) {
    TenantContext context =
        TenantContext.of(
            resolution.userId(),
            resolution.email(),
            resolution.officeId(),
            resolution.admin(),
            resolution.roles(),
            resolution.permissions());
    TenantContextHolder.set(context);
    return context;
  }
}
