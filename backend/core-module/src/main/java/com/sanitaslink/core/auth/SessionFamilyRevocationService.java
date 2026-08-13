package com.sanitaslink.core.auth;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.domain.MembershipStatus;
import com.sanitaslink.core.domain.OfficeMembership;
import com.sanitaslink.core.repository.OfficeMembershipRepository;
import com.sanitaslink.core.repository.RefreshTokenRepository;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a refresh-token session family in its own transaction. Called when a replayed (already
 * rotated) refresh token is detected: the revocation must survive the subsequent 401 response, so
 * it must not be rolled back together with the failed request. {@link TenantContextManager}
 * switches the connection to the owning user's RLS context before touching the tokens.
 *
 * <p>The family advisory lock ({@link SessionFamilyLocks}) is acquired before the sweep so a
 * concurrent rotation can never insert a successor that escapes the revocation; the sweep is a
 * single atomic UPDATE. The reuse audit event is recorded only when the sweep actually revoked at
 * least one token, keeping repeated replays of the same family idempotent.
 */
@Service
public class SessionFamilyRevocationService {

  private final TenantContextManager tenantContextManager;
  private final RefreshTokenRepository refreshTokenRepository;
  private final OfficeMembershipRepository membershipRepository;
  private final AuditService auditService;
  private final SessionFamilyLocks sessionFamilyLocks;

  public SessionFamilyRevocationService(
      TenantContextManager tenantContextManager,
      RefreshTokenRepository refreshTokenRepository,
      OfficeMembershipRepository membershipRepository,
      AuditService auditService,
      SessionFamilyLocks sessionFamilyLocks) {
    this.tenantContextManager = tenantContextManager;
    this.refreshTokenRepository = refreshTokenRepository;
    this.membershipRepository = membershipRepository;
    this.auditService = auditService;
    this.sessionFamilyLocks = sessionFamilyLocks;
  }

  /** Revokes every token of the given family and records the reuse audit event. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamilyAndAudit(UUID userId, UUID sessionFamilyId) {
    // Phase 1: self-read context so RLS lets the membership lookup see the owning user.
    tenantContextManager.initialize(null, userId, false);
    UUID officeId =
        membershipRepository
            .findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
            .map(OfficeMembership::getOfficeId)
            .orElse(null);
    // Phase 2: full context (office + user) for the sweep and the audit insert.
    tenantContextManager.initialize(officeId, userId, false);
    sessionFamilyLocks.lockFamily(sessionFamilyId);
    int revoked = refreshTokenRepository.revokeFamily(sessionFamilyId, Instant.now());
    if (revoked > 0) {
      auditService.recordAs(
          userId, officeId, AuditActionType.TOKEN_REUSE, "USER", userId.toString());
    }
  }
}
