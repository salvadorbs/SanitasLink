package com.sanitaslink.core.office;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.domain.MembershipStatus;
import com.sanitaslink.core.domain.OfficeMembership;
import com.sanitaslink.core.domain.Role;
import com.sanitaslink.core.domain.RoleScope;
import com.sanitaslink.core.domain.User;
import com.sanitaslink.core.domain.UserRole;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.office.dto.OfficeMemberResponse;
import com.sanitaslink.core.repository.OfficeMembershipRepository;
import com.sanitaslink.core.repository.RoleRepository;
import com.sanitaslink.core.repository.UserRepository;
import com.sanitaslink.core.repository.UserRoleRepository;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Office membership and role management. */
@Service
public class MembershipService {

  private static final String OWNER_ROLE_CODE = "MEDICO_TITOLARE";

  private final OfficeMembershipRepository membershipRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final OfficeGuard officeGuard;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public MembershipService(
      OfficeMembershipRepository membershipRepository,
      UserRoleRepository userRoleRepository,
      RoleRepository roleRepository,
      UserRepository userRepository,
      OfficeGuard officeGuard,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.membershipRepository = membershipRepository;
    this.userRoleRepository = userRoleRepository;
    this.roleRepository = roleRepository;
    this.userRepository = userRepository;
    this.officeGuard = officeGuard;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<OfficeMemberResponse> listMembers(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    return membershipRepository.findByOfficeIdAndStatus(officeId, MembershipStatus.ACTIVE).stream()
        .map(this::toMemberResponse)
        .toList();
  }

  @Transactional
  public void assignRole(UUID officeId, UUID userId, UUID roleId) {
    tenantContextManager.initialize();
    TenantContext actor = officeGuard.requireOfficeAccess(officeId);
    OfficeMembership membership = requireActiveMember(officeId, userId);
    Role role = requireOfficeRole(roleId);
    if (!userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
      userRoleRepository.save(new UserRole(userId, roleId, actor.userId()));
      auditService.recordAs(
          actor.userId(),
          AuditActionType.MEMBER_ROLE_CHANGED,
          "MEMBERSHIP",
          membership.getUserId().toString());
    }
  }

  @Transactional
  public void revokeRole(UUID officeId, UUID userId, UUID roleId) {
    tenantContextManager.initialize();
    TenantContext actor = officeGuard.requireOfficeAccess(officeId);
    OfficeMembership membership = requireActiveMember(officeId, userId);
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND, "Role not found"));

    List<String> officeRoles = roleRepository.findActiveRoleCodesForUser(userId, RoleScope.OFFICE);
    if (officeRoles.size() <= 1) {
      throw ApiException.conflict(
          ErrorCodes.OPERATION_CONFLICT, "A user must keep at least one office role");
    }
    if (OWNER_ROLE_CODE.equals(role.getCode())) {
      ensureNotLastOwner(officeId, userId, roleId);
    }
    userRoleRepository.deleteById(new UserRole.UserRoleId(userId, roleId));
    auditService.recordAs(
        actor.userId(),
        AuditActionType.MEMBER_ROLE_CHANGED,
        "MEMBERSHIP",
        membership.getUserId().toString());
  }

  @Transactional
  public void removeMember(UUID officeId, UUID userId) {
    tenantContextManager.initialize();
    TenantContext actor = officeGuard.requireOfficeAccess(officeId);
    OfficeMembership membership = requireActiveMember(officeId, userId);
    boolean isOwner =
        roleRepository.findActiveRoleCodesForUser(userId, RoleScope.OFFICE).stream()
            .anyMatch(OWNER_ROLE_CODE::equals);
    if (isOwner) {
      long owners =
          userRoleRepository.countActiveMembersWithRoleInOffice(
              officeId, MembershipStatus.ACTIVE, OWNER_ROLE_CODE);
      if (owners <= 1) {
        throw ApiException.conflict(
            ErrorCodes.LAST_PRACTICE_OWNER, "The last practice owner cannot be removed");
      }
    }
    userRoleRepository.deleteByUserId(userId);
    membership.setStatus(MembershipStatus.REVOKED);
    membership.setRevokedAt(java.time.Instant.now());
    membershipRepository.save(membership);
    auditService.recordAs(
        actor.userId(), AuditActionType.MEMBER_REMOVED, "MEMBERSHIP", userId.toString());
  }

  private OfficeMembership requireActiveMember(UUID officeId, UUID userId) {
    OfficeMembership membership =
        membershipRepository
            .findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
            .orElseThrow(
                () -> ApiException.notFound(ErrorCodes.MEMBERSHIP_NOT_FOUND, "Member not found"));
    if (!officeId.equals(membership.getOfficeId())) {
      throw ApiException.notFound(ErrorCodes.MEMBERSHIP_NOT_FOUND, "Member not found");
    }
    return membership;
  }

  private Role requireOfficeRole(UUID roleId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND, "Role not found"));
    if (!RoleScope.OFFICE.equals(role.getScope()) || !Boolean.TRUE.equals(role.getActive())) {
      throw ApiException.conflict(ErrorCodes.ROLE_NOT_ACTIVE, "Role is not active");
    }
    return role;
  }

  private void ensureNotLastOwner(UUID officeId, UUID userId, UUID roleId) {
    long owners =
        userRoleRepository.countActiveMembersWithRoleInOffice(
            officeId, MembershipStatus.ACTIVE, OWNER_ROLE_CODE);
    boolean targetHasRole = userRoleRepository.existsByUserIdAndRoleId(userId, roleId);
    if (targetHasRole && owners <= 1) {
      throw ApiException.conflict(
          ErrorCodes.LAST_PRACTICE_OWNER, "The last practice owner must keep the owner role");
    }
  }

  private OfficeMemberResponse toMemberResponse(OfficeMembership membership) {
    User user =
        userRepository
            .findById(membership.getUserId())
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
    List<String> roles = roleRepository.findActiveRoleCodesForUser(user.getId(), RoleScope.OFFICE);
    return new OfficeMemberResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        membership.getStatus(),
        roles);
  }
}
