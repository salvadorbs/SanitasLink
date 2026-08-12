package com.sanitaslink.core.auth;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.auth.dto.AcceptInvitationRequest;
import com.sanitaslink.core.auth.dto.ChangePasswordRequest;
import com.sanitaslink.core.auth.dto.ConfirmPasswordResetRequest;
import com.sanitaslink.core.auth.dto.LoginRequest;
import com.sanitaslink.core.auth.dto.LoginResponse;
import com.sanitaslink.core.auth.dto.LogoutRequest;
import com.sanitaslink.core.auth.dto.MeResponse;
import com.sanitaslink.core.auth.dto.RefreshTokenRequest;
import com.sanitaslink.core.auth.dto.RequestPasswordResetRequest;
import com.sanitaslink.core.config.JwtProperties;
import com.sanitaslink.core.config.TokenProperties;
import com.sanitaslink.core.domain.InvitationStatus;
import com.sanitaslink.core.domain.MembershipStatus;
import com.sanitaslink.core.domain.OfficeInvitation;
import com.sanitaslink.core.domain.OfficeMembership;
import com.sanitaslink.core.domain.PasswordResetToken;
import com.sanitaslink.core.domain.RefreshToken;
import com.sanitaslink.core.domain.User;
import com.sanitaslink.core.domain.UserRole;
import com.sanitaslink.core.domain.UserStatus;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.notification.NotificationPort;
import com.sanitaslink.core.repository.OfficeInvitationRepository;
import com.sanitaslink.core.repository.OfficeMembershipRepository;
import com.sanitaslink.core.repository.PasswordResetTokenRepository;
import com.sanitaslink.core.repository.RefreshTokenRepository;
import com.sanitaslink.core.repository.UserRepository;
import com.sanitaslink.core.repository.UserRoleRepository;
import com.sanitaslink.core.security.AuthenticatedUser;
import com.sanitaslink.core.security.JwtTokenService;
import com.sanitaslink.core.security.LoginRateLimiter;
import com.sanitaslink.core.security.PermissionResolver;
import com.sanitaslink.core.security.TokenGenerator;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authentication, token lifecycle, invitation acceptance and password management. */
@Service
public class AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final OfficeMembershipRepository membershipRepository;
  private final UserRoleRepository userRoleRepository;
  private final OfficeInvitationRepository invitationRepository;
  private final JwtTokenService jwtTokenService;
  private final JwtProperties jwtProperties;
  private final TokenProperties tokenProperties;
  private final PasswordEncoder passwordEncoder;
  private final NotificationPort notificationPort;
  private final AuditService auditService;
  private final TenantContextManager tenantContextManager;
  private final PermissionResolver permissionResolver;
  private final LoginRateLimiter loginRateLimiter;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      OfficeMembershipRepository membershipRepository,
      UserRoleRepository userRoleRepository,
      OfficeInvitationRepository invitationRepository,
      JwtTokenService jwtTokenService,
      JwtProperties jwtProperties,
      TokenProperties tokenProperties,
      PasswordEncoder passwordEncoder,
      NotificationPort notificationPort,
      AuditService auditService,
      TenantContextManager tenantContextManager,
      PermissionResolver permissionResolver,
      LoginRateLimiter loginRateLimiter) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.membershipRepository = membershipRepository;
    this.userRoleRepository = userRoleRepository;
    this.invitationRepository = invitationRepository;
    this.jwtTokenService = jwtTokenService;
    this.jwtProperties = jwtProperties;
    this.tokenProperties = tokenProperties;
    this.passwordEncoder = passwordEncoder;
    this.notificationPort = notificationPort;
    this.auditService = auditService;
    this.tenantContextManager = tenantContextManager;
    this.permissionResolver = permissionResolver;
    this.loginRateLimiter = loginRateLimiter;
  }

  @Transactional
  public LoginResponse login(LoginRequest request, String clientIp) {
    String rateKey = clientIp;
    if (!loginRateLimiter.isAllowed(rateKey)) {
      throw new ApiException(
          ErrorCodes.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts");
    }

    String email = User.normalizeEmail(request.email());
    User user = userRepository.findByEmail(email).orElse(null);

    boolean passwordMatches =
        user != null
            && user.getPasswordHash() != null
            && passwordEncoder.matches(request.password(), user.getPasswordHash());

    if (user == null || !passwordMatches) {
      if (user != null) {
        incrementFailedAttempts(user);
      }
      auditService.recordAs(
          user != null ? user.getId() : null,
          AuditActionType.LOGIN_FAILED,
          "USER",
          user != null ? user.getId().toString() : email);
      throw new ApiException(
          ErrorCodes.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
      throw new ApiException(
          ErrorCodes.ACCOUNT_LOCKED, HttpStatus.FORBIDDEN, "Account temporarily locked");
    }
    if (!UserStatus.ACTIVE.equals(user.getStatus())) {
      throw new ApiException(
          ErrorCodes.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN, "Account is not active");
    }

    user.setFailedLoginAttempts(0);
    user.setLastLoginAt(Instant.now());
    userRepository.save(user);
    loginRateLimiter.reset(rateKey);

    LoginResponse response = issueTokenPair(user);
    auditService.recordAs(user.getId(), AuditActionType.LOGIN, "USER", user.getId().toString());
    return response;
  }

  @Transactional
  public LoginResponse refresh(RefreshTokenRequest request) {
    String hash = TokenGenerator.sha256Hex(request.refreshToken());
    // The raw refresh token is the bearer credential for this lookup.
    tenantContextManager.initializeWithToken(hash);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(this::invalidRefreshToken);
    if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
      throw invalidRefreshToken();
    }

    User user = userRepository.findById(token.getUserId()).orElseThrow(this::invalidRefreshToken);
    if (!UserStatus.ACTIVE.equals(user.getStatus())) {
      throw invalidRefreshToken();
    }

    tenantContextManager.initialize(null, user.getId(), false);

    // Atomic claim: only the caller that revokes the token first may rotate it.
    if (refreshTokenRepository.revokeIfActive(token.getId(), Instant.now()) == 0) {
      throw invalidRefreshToken();
    }
    String newRawRefresh = TokenGenerator.randomToken();
    refreshTokenRepository.save(
        RefreshToken.create(
            UUID.randomUUID(),
            user.getId(),
            TokenGenerator.sha256Hex(newRawRefresh),
            Instant.now().plus(jwtProperties.getRefreshTokenTtl())));

    LoginResponse response = issueTokenPair(user);
    auditService.recordAs(
        user.getId(), AuditActionType.TOKEN_REFRESH, "USER", user.getId().toString());
    return new LoginResponse(
        response.accessToken(), newRawRefresh, response.expiresInSeconds(), response.tokenType());
  }

  @Transactional
  public void logout(LogoutRequest request) {
    String hash = TokenGenerator.sha256Hex(request.refreshToken());
    tenantContextManager.initializeWithToken(hash);
    refreshTokenRepository
        .findByTokenHash(hash)
        .ifPresent(
            token -> {
              tenantContextManager.initialize(null, token.getUserId(), false);
              token.setRevokedAt(Instant.now());
              refreshTokenRepository.save(token);
            });
    TenantContext context = TenantContextHolder.get();
    if (context != null) {
      auditService.recordAs(context.userId(), AuditActionType.LOGOUT, "SESSION", hash);
    }
  }

  public MeResponse me() {
    TenantContext context = TenantContextHolder.require();
    User user =
        userRepository
            .findById(context.userId())
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
    return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getStatus(),
        context.officeId(),
        context.roles(),
        context.permissions());
  }

  @Transactional
  public LoginResponse acceptInvitation(AcceptInvitationRequest request) {
    String hash = TokenGenerator.sha256Hex(request.token());

    // RLS: the token itself is the bearer credential for this lookup.
    tenantContextManager.initializeWithToken(hash);
    OfficeInvitation invitation =
        invitationRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.INVALID_INVITATION_TOKEN,
                        HttpStatus.UNAUTHORIZED,
                        "Invalid invitation token"));

    if (InvitationStatus.ACCEPTED.equals(invitation.getStatus())) {
      throw new ApiException(
          ErrorCodes.INVITATION_ALREADY_USED, HttpStatus.CONFLICT, "Invitation already used");
    }
    if (InvitationStatus.REVOKED.equals(invitation.getStatus())) {
      throw new ApiException(
          ErrorCodes.INVALID_INVITATION_TOKEN, HttpStatus.UNAUTHORIZED, "Invalid invitation token");
    }
    if (invitation.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(
          ErrorCodes.INVITATION_EXPIRED, HttpStatus.UNAUTHORIZED, "Invitation has expired");
    }

    String email = User.normalizeEmail(invitation.getEmail());
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
      user = User.invited(UUID.randomUUID(), email, request.firstName(), request.lastName(), null);
    } else {
      user.setFirstName(request.firstName());
      user.setLastName(request.lastName());
    }

    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setStatus(UserStatus.ACTIVE);
    user.setEmailVerifiedAt(Instant.now());
    user.setPasswordChangedAt(Instant.now());
    userRepository.save(user);

    // Full context for the office being joined so membership/role writes pass RLS.
    tenantContextManager.initialize(invitation.getOfficeId(), user.getId(), false);

    OfficeMembership membership = membershipRepository.findByUserId(user.getId()).orElse(null);
    if (membership == null) {
      membership = OfficeMembership.invited(user.getId(), invitation.getOfficeId());
    } else {
      if (!invitation.getOfficeId().equals(membership.getOfficeId())
          || !MembershipStatus.INVITED.equals(membership.getStatus())) {
        throw new ApiException(
            ErrorCodes.USER_ALREADY_MEMBER,
            HttpStatus.CONFLICT,
            "User is already a member of another office");
      }
    }
    membership.setStatus(MembershipStatus.ACTIVE);
    membership.setAcceptedAt(Instant.now());
    membershipRepository.save(membership);

    if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), invitation.getRoleId())) {
      userRoleRepository.save(
          new UserRole(user.getId(), invitation.getRoleId(), invitation.getCreatedByUserId()));
    }

    // Atomic claim: exactly one concurrent acceptance may consume the pending invitation.
    if (invitationRepository.claimPending(invitation.getId(), Instant.now()) == 0) {
      throw new ApiException(
          ErrorCodes.INVITATION_ALREADY_USED, HttpStatus.CONFLICT, "Invitation already used");
    }

    auditService.recordAs(
        user.getId(),
        AuditActionType.INVITATION_ACCEPTED,
        "INVITATION",
        invitation.getId().toString());

    return issueTokenPair(user);
  }

  @Transactional
  public void requestPasswordReset(RequestPasswordResetRequest request) {
    String email = User.normalizeEmail(request.email());
    User user = userRepository.findByEmail(email).orElse(null);
    if (user != null && UserStatus.ACTIVE.equals(user.getStatus())) {
      tenantContextManager.initialize(null, user.getId(), false);
      String rawToken = TokenGenerator.randomToken();
      passwordResetTokenRepository.save(
          PasswordResetToken.create(
              UUID.randomUUID(),
              user.getId(),
              TokenGenerator.sha256Hex(rawToken),
              Instant.now().plus(tokenProperties.getPasswordResetTtl())));
      notificationPort.sendPasswordReset(
          user.getEmail(), rawToken, Instant.now().plus(tokenProperties.getPasswordResetTtl()));
      auditService.recordAs(
          user.getId(), AuditActionType.PASSWORD_RESET_REQUESTED, "USER", user.getId().toString());
    }
    // Uniform response to avoid account enumeration.
  }

  @Transactional
  public void confirmPasswordReset(ConfirmPasswordResetRequest request) {
    String hash = TokenGenerator.sha256Hex(request.token());
    // The reset token is the bearer credential for this lookup.
    tenantContextManager.initializeWithToken(hash);
    PasswordResetToken token =
        passwordResetTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.INVALID_RESET_TOKEN,
                        HttpStatus.UNAUTHORIZED,
                        "Invalid reset token"));
    if (token.getUsedAt() != null) {
      throw new ApiException(
          ErrorCodes.INVALID_RESET_TOKEN, HttpStatus.UNAUTHORIZED, "Invalid reset token");
    }
    if (token.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(
          ErrorCodes.RESET_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Reset token has expired");
    }

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.INVALID_RESET_TOKEN,
                        HttpStatus.UNAUTHORIZED,
                        "Invalid reset token"));

    tenantContextManager.initialize(null, user.getId(), false);

    // Atomic claim: exactly one concurrent confirmation may consume the reset token.
    if (passwordResetTokenRepository.claimUnused(token.getId(), Instant.now()) == 0) {
      throw new ApiException(
          ErrorCodes.INVALID_RESET_TOKEN, HttpStatus.UNAUTHORIZED, "Invalid reset token");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setPasswordChangedAt(Instant.now());
    user.setStatus(UserStatus.ACTIVE);
    user.setSecurityVersion(incrementSecurityVersion(user));
    userRepository.save(user);

    revokeAllRefreshTokens(user.getId());
    auditService.recordAs(
        user.getId(), AuditActionType.PASSWORD_RESET_CONFIRMED, "USER", user.getId().toString());
  }

  @Transactional
  public void changePassword(ChangePasswordRequest request) {
    TenantContext context = TenantContextHolder.require();
    tenantContextManager.initialize();
    User user =
        userRepository
            .findById(context.userId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(
          ErrorCodes.INVALID_CREDENTIALS, HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setPasswordChangedAt(Instant.now());
    user.setSecurityVersion(incrementSecurityVersion(user));
    userRepository.save(user);
    revokeAllRefreshTokens(user.getId());
    auditService.recordAs(
        user.getId(), AuditActionType.PASSWORD_CHANGE, "USER", user.getId().toString());
  }

  private LoginResponse issueTokenPair(User user) {
    PermissionResolver.Resolution resolution = permissionResolver.resolve(user.getId());
    AuthenticatedUser principal =
        new AuthenticatedUser(
            user.getId(),
            user.getEmail(),
            resolution.officeId(),
            resolution.roles(),
            resolution.admin(),
            resolution.securityVersion());
    String accessToken =
        jwtTokenService.issueAccessToken(principal, resolution.roles(), resolution.permissions());
    String rawRefresh = TokenGenerator.randomToken();
    refreshTokenRepository.save(
        RefreshToken.create(
            UUID.randomUUID(),
            user.getId(),
            TokenGenerator.sha256Hex(rawRefresh),
            Instant.now().plus(jwtProperties.getRefreshTokenTtl())));
    long expiresIn = jwtProperties.getAccessTokenTtl().toSeconds();
    return new LoginResponse(accessToken, rawRefresh, expiresIn, "Bearer");
  }

  private int incrementSecurityVersion(User user) {
    int current = user.getSecurityVersion() == null ? 0 : user.getSecurityVersion();
    return current + 1;
  }

  private void revokeAllRefreshTokens(UUID userId) {
    List<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
    tokens.forEach(token -> token.setRevokedAt(Instant.now()));
    refreshTokenRepository.saveAll(tokens);
  }

  private void incrementFailedAttempts(User user) {
    int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
    user.setFailedLoginAttempts(attempts);
    if (attempts >= MAX_FAILED_ATTEMPTS) {
      user.setLockedUntil(Instant.now().plusSeconds(300));
    }
    userRepository.save(user);
  }

  private ApiException invalidRefreshToken() {
    return new ApiException(
        ErrorCodes.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, "Invalid refresh token");
  }
}
