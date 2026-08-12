package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link PasswordResetToken}. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * Atomically marks the token as used only if it is unused, making password-reset confirmation
   * race-free: exactly one concurrent caller observes a row update.
   */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
  int claimUnused(@Param("id") UUID id, @Param("now") Instant now);
}
