package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link RefreshToken}. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Reads the replacement marker straight from the database, bypassing the JPA identity map: a
   * concurrent rotation committed by another transaction is not visible in this transaction's
   * persistence context, so a cached entity would hide the takeover.
   */
  @Query("SELECT t.replacedByTokenHash FROM RefreshToken t WHERE t.id = :id")
  Optional<String> findReplacedByTokenHash(@Param("id") UUID id);

  List<RefreshToken> findByUserId(UUID userId);

  List<RefreshToken> findBySessionFamilyId(UUID sessionFamilyId);

  /**
   * Atomically revokes the token only if it is not already revoked, making rotation race-free:
   * exactly one concurrent caller observes a row update.
   */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.id = :id AND t.revokedAt IS NULL")
  int revokeIfActive(@Param("id") UUID id, @Param("now") Instant now);

  /**
   * Records the hash of the token that replaced a revoked token, enabling replay detection: a
   * revoked token carrying a replacement hash proves the original was rotated, so presenting it
   * again is a reuse attempt.
   */
  @Modifying
  @Query(
      "UPDATE RefreshToken t SET t.replacedByTokenHash = :replacementHash "
          + "WHERE t.id = :id AND t.revokedAt IS NOT NULL AND t.replacedByTokenHash IS NULL")
  int markReplacedBy(@Param("id") UUID id, @Param("replacementHash") String replacementHash);

  /**
   * Revokes every still-active token of a session family in a single atomic UPDATE: the caller
   * (holding the family advisory lock via {@link com.sanitaslink.core.auth.SessionFamilyLocks})
   * observes exactly the tokens that exist at execution time, and no concurrent rotation can slip a
   * new token past the sweep. Returns the number of tokens actually revoked, which doubles as the
   * "did this replay revoke anything" signal for idempotent reuse auditing.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now "
          + "WHERE t.sessionFamilyId = :familyId AND t.revokedAt IS NULL")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
