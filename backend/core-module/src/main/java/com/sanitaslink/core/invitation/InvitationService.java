package com.sanitaslink.core.invitation;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.config.TokenProperties;
import com.sanitaslink.core.domain.InvitationStatus;
import com.sanitaslink.core.domain.Office;
import com.sanitaslink.core.domain.OfficeInvitation;
import com.sanitaslink.core.domain.Role;
import com.sanitaslink.core.domain.RoleScope;
import com.sanitaslink.core.domain.User;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.notification.NotificationPort;
import com.sanitaslink.core.office.OfficeGuard;
import com.sanitaslink.core.office.dto.InvitationResponse;
import com.sanitaslink.core.office.dto.InviteMemberRequest;
import com.sanitaslink.core.repository.OfficeInvitationRepository;
import com.sanitaslink.core.repository.OfficeMembershipRepository;
import com.sanitaslink.core.repository.OfficeRepository;
import com.sanitaslink.core.repository.RoleRepository;
import com.sanitaslink.core.repository.UserRepository;
import com.sanitaslink.core.security.TokenGenerator;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Office invitation lifecycle (member-facing and admin paths). */
@Service
public class InvitationService {

  private final OfficeRepository officeRepository;
  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final OfficeMembershipRepository membershipRepository;
  private final OfficeInvitationRepository invitationRepository;
  private final TokenProperties tokenProperties;
  private final NotificationPort notificationPort;
  private final OfficeGuard officeGuard;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public InvitationService(
      OfficeRepository officeRepository,
      RoleRepository roleRepository,
      UserRepository userRepository,
      OfficeMembershipRepository membershipRepository,
      OfficeInvitationRepository invitationRepository,
      TokenProperties tokenProperties,
      NotificationPort notificationPort,
      OfficeGuard officeGuard,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.officeRepository = officeRepository;
    this.roleRepository = roleRepository;
    this.userRepository = userRepository;
    this.membershipRepository = membershipRepository;
    this.invitationRepository = invitationRepository;
    this.tokenProperties = tokenProperties;
    this.notificationPort = notificationPort;
    this.officeGuard = officeGuard;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional
  public InvitationResponse invite(UUID officeId, InviteMemberRequest request) {
    tenantContextManager.initialize();
    TenantContext actor = officeGuard.requireOfficeAccess(officeId);
    Office office =
        officeRepository
            .findById(officeId)
            .orElseThrow(
                () -> ApiException.notFound(ErrorCodes.OFFICE_NOT_FOUND, "Office not found"));

    Role role =
        roleRepository
            .findById(request.roleId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND, "Role not found"));
    if (!RoleScope.OFFICE.equals(role.getScope()) || !Boolean.TRUE.equals(role.getActive())) {
      throw ApiException.conflict(ErrorCodes.ROLE_NOT_ACTIVE, "Role is not active");
    }

    String email = User.normalizeEmail(request.email());
    if (invitationRepository
        .findFirstByOfficeIdAndEmailAndStatus(officeId, email, InvitationStatus.PENDING)
        .isPresent()) {
      throw ApiException.conflict(
          ErrorCodes.INVITATION_CONFLICT, "A pending invitation already exists for this email");
    }
    boolean alreadyMember =
        userRepository
            .findByEmail(email)
            .flatMap(user -> membershipRepository.findByUserId(user.getId()))
            .isPresent();
    if (alreadyMember) {
      throw ApiException.conflict(
          ErrorCodes.USER_ALREADY_MEMBER, "This email already belongs to an office");
    }

    Instant now = Instant.now();
    String rawToken = TokenGenerator.randomToken();
    OfficeInvitation invitation =
        OfficeInvitation.create(
            UUID.randomUUID(),
            officeId,
            email,
            role.getId(),
            TokenGenerator.sha256Hex(rawToken),
            now.plus(tokenProperties.getInvitationTtl()),
            actor.userId());
    invitationRepository.save(invitation);

    notificationPort.sendInvitation(email, office.getName(), rawToken, invitation.getExpiresAt());
    auditService.recordAs(
        actor.userId(),
        AuditActionType.MEMBER_INVITED,
        "INVITATION",
        invitation.getId().toString());
    return toResponse(invitation, role);
  }

  @Transactional(readOnly = true)
  public List<InvitationResponse> listInvitations(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    return invitationRepository.findByOfficeId(officeId).stream()
        .map(
            invitation ->
                roleRepository
                    .findById(invitation.getRoleId())
                    .map(role -> toResponse(invitation, role))
                    .orElseGet(() -> toResponse(invitation, null)))
        .toList();
  }

  @Transactional
  public void revokeInvitation(UUID officeId, UUID invitationId) {
    tenantContextManager.initialize();
    TenantContext actor = officeGuard.requireOfficeAccess(officeId);
    OfficeInvitation invitation =
        invitationRepository
            .findByIdAndOfficeId(invitationId, officeId)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        ErrorCodes.INVALID_INVITATION_TOKEN, "Invitation not found"));
    if (InvitationStatus.PENDING.equals(invitation.getStatus())) {
      invitation.setStatus(InvitationStatus.REVOKED);
      invitation.setRevokedAt(Instant.now());
      invitationRepository.save(invitation);
      auditService.recordAs(
          actor.userId(),
          AuditActionType.INVITATION_REVOKED,
          "INVITATION",
          invitation.getId().toString());
    }
  }

  private InvitationResponse toResponse(OfficeInvitation invitation, Role role) {
    return new InvitationResponse(
        invitation.getId(),
        invitation.getEmail(),
        invitation.getRoleId(),
        role != null ? role.getCode() : null,
        role != null ? role.getName() : null,
        invitation.getStatus(),
        invitation.getExpiresAt(),
        invitation.getCreatedAt());
  }
}
