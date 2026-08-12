package com.sanitaslink.core.office.admin;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.config.TokenProperties;
import com.sanitaslink.core.domain.MembershipStatus;
import com.sanitaslink.core.domain.Office;
import com.sanitaslink.core.domain.OfficeInvitation;
import com.sanitaslink.core.domain.OfficeMembership;
import com.sanitaslink.core.domain.Permission;
import com.sanitaslink.core.domain.Role;
import com.sanitaslink.core.domain.User;
import com.sanitaslink.core.domain.UserRole;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.notification.NotificationPort;
import com.sanitaslink.core.office.dto.CreateOfficeRequest;
import com.sanitaslink.core.office.dto.OfficeMemberResponse;
import com.sanitaslink.core.office.dto.OfficeResponse;
import com.sanitaslink.core.office.dto.PermissionResponse;
import com.sanitaslink.core.office.dto.RoleResponse;
import com.sanitaslink.core.repository.OfficeInvitationRepository;
import com.sanitaslink.core.repository.OfficeMembershipRepository;
import com.sanitaslink.core.repository.OfficeRepository;
import com.sanitaslink.core.repository.PermissionRepository;
import com.sanitaslink.core.repository.RoleRepository;
import com.sanitaslink.core.repository.UserRepository;
import com.sanitaslink.core.repository.UserRoleRepository;
import com.sanitaslink.core.security.TokenGenerator;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global platform administration of offices and the role/permission catalog. */
@Service
public class AdminOfficeService {

  private static final String OWNER_ROLE_CODE = "MEDICO_TITOLARE";

  private final OfficeRepository officeRepository;
  private final UserRepository userRepository;
  private final OfficeMembershipRepository membershipRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final OfficeInvitationRepository invitationRepository;
  private final TokenProperties tokenProperties;
  private final NotificationPort notificationPort;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public AdminOfficeService(
      OfficeRepository officeRepository,
      UserRepository userRepository,
      OfficeMembershipRepository membershipRepository,
      UserRoleRepository userRoleRepository,
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      OfficeInvitationRepository invitationRepository,
      TokenProperties tokenProperties,
      NotificationPort notificationPort,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.officeRepository = officeRepository;
    this.userRepository = userRepository;
    this.membershipRepository = membershipRepository;
    this.userRoleRepository = userRoleRepository;
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.invitationRepository = invitationRepository;
    this.tokenProperties = tokenProperties;
    this.notificationPort = notificationPort;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional
  public OfficeResponse createOffice(CreateOfficeRequest request) {
    tenantContextManager.initialize();
    TenantContext admin = TenantContextHolder.require();

    UUID officeId = UUID.randomUUID();
    Office office =
        Office.create(
            officeId,
            request.name(),
            request.legalName(),
            request.taxIdentifier(),
            request.email(),
            request.phone(),
            request.address());
    officeRepository.save(office);

    String ownerEmail = User.normalizeEmail(request.ownerEmail());
    User owner = userRepository.findByEmail(ownerEmail).orElse(null);
    if (owner == null) {
      owner =
          User.invited(
              UUID.randomUUID(),
              ownerEmail,
              request.ownerFirstName(),
              request.ownerLastName(),
              request.ownerPhone());
      userRepository.save(owner);
    } else {
      if (membershipRepository.findByUserId(owner.getId()).isPresent()) {
        throw ApiException.conflict(
            ErrorCodes.USER_ALREADY_MEMBER, "The titular email already belongs to an office");
      }
      owner.setFirstName(request.ownerFirstName());
      owner.setLastName(request.ownerLastName());
      owner.setPhone(request.ownerPhone());
    }

    OfficeMembership membership = OfficeMembership.invited(owner.getId(), officeId);
    membershipRepository.save(membership);

    Role ownerRole =
        roleRepository
            .findByCode(OWNER_ROLE_CODE)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.ROLE_NOT_FOUND, HttpStatus.NOT_FOUND, "Owner role not found"));
    userRoleRepository.save(new UserRole(owner.getId(), ownerRole.getId(), admin.userId()));

    Instant now = Instant.now();
    String rawToken = TokenGenerator.randomToken();
    OfficeInvitation invitation =
        OfficeInvitation.create(
            UUID.randomUUID(),
            officeId,
            ownerEmail,
            ownerRole.getId(),
            TokenGenerator.sha256Hex(rawToken),
            now.plus(tokenProperties.getInvitationTtl()),
            admin.userId());
    invitationRepository.save(invitation);

    notificationPort.sendInvitation(
        ownerEmail, office.getName(), rawToken, invitation.getExpiresAt());

    auditService.recordAs(
        admin.userId(), AuditActionType.OFFICE_CREATED, "OFFICE", officeId.toString());
    auditService.recordAs(
        admin.userId(),
        AuditActionType.MEMBER_INVITED,
        "INVITATION",
        invitation.getId().toString());

    return toResponse(office);
  }

  @Transactional(readOnly = true)
  public List<OfficeResponse> listOffices() {
    tenantContextManager.initialize();
    return officeRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public OfficeResponse getOffice(UUID officeId) {
    tenantContextManager.initialize();
    Office office =
        officeRepository
            .findById(officeId)
            .orElseThrow(
                () -> ApiException.notFound(ErrorCodes.OFFICE_NOT_FOUND, "Office not found"));
    return toResponse(office);
  }

  @Transactional(readOnly = true)
  public List<OfficeMemberResponse> listMembers(UUID officeId) {
    tenantContextManager.initialize();
    return membershipRepository.findByOfficeIdAndStatus(officeId, MembershipStatus.ACTIVE).stream()
        .map(
            membership -> {
              User user =
                  userRepository
                      .findById(membership.getUserId())
                      .orElseThrow(
                          () -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
              List<String> roles =
                  roleRepository.findActiveRoleCodesForUser(user.getId(), "OFFICE");
              return new OfficeMemberResponse(
                  user.getId(),
                  user.getEmail(),
                  user.getFirstName(),
                  user.getLastName(),
                  membership.getStatus(),
                  roles);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RoleResponse> listRoles() {
    tenantContextManager.initialize();
    return roleRepository.findAll().stream().map(this::toRoleResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<PermissionResponse> listPermissions() {
    tenantContextManager.initialize();
    return permissionRepository.findByActiveTrueOrderByModuleAscCodeAsc().stream()
        .map(this::toPermissionResponse)
        .toList();
  }

  private OfficeResponse toResponse(Office office) {
    return new OfficeResponse(
        office.getId(),
        office.getName(),
        office.getLegalName(),
        office.getTaxIdentifier(),
        office.getEmail(),
        office.getPhone(),
        office.getAddress(),
        office.getStatus(),
        office.getCreatedAt(),
        office.getUpdatedAt());
  }

  private RoleResponse toRoleResponse(Role role) {
    return new RoleResponse(
        role.getId(),
        role.getCode(),
        role.getName(),
        role.getDescription(),
        role.getScope(),
        Boolean.TRUE.equals(role.getActive()));
  }

  private PermissionResponse toPermissionResponse(Permission permission) {
    return new PermissionResponse(
        permission.getId(),
        permission.getCode(),
        permission.getModule(),
        permission.getName(),
        permission.getDescription(),
        Boolean.TRUE.equals(permission.getActive()));
  }
}
