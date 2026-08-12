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

  List<RefreshToken> findByUserId(UUID userId);

  /**
   * Atomically revokes the token only if it is not already revoked, making rotation race-free:
   * exactly one concurrent caller observes a row update.
   */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.id = :id AND t.revokedAt IS NULL")
  int revokeIfActive(@Param("id") UUID id, @Param("now") Instant now);
}
