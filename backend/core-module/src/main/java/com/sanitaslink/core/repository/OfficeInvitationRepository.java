package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.OfficeInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link OfficeInvitation}. */
public interface OfficeInvitationRepository extends JpaRepository<OfficeInvitation, UUID> {

  Optional<OfficeInvitation> findByTokenHash(String tokenHash);

  /**
   * Atomically transitions a pending invitation to accepted, making acceptance race-free: exactly
   * one concurrent caller observes a row update.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE OfficeInvitation i SET i.status = 'ACCEPTED', i.acceptedAt = :now "
          + "WHERE i.id = :id AND i.status = 'PENDING'")
  int claimPending(@Param("id") UUID id, @Param("now") Instant now);

  List<OfficeInvitation> findByOfficeId(UUID officeId);

  List<OfficeInvitation> findByOfficeIdAndStatus(UUID officeId, String status);

  Optional<OfficeInvitation> findByIdAndOfficeId(UUID id, UUID officeId);

  Optional<OfficeInvitation> findFirstByOfficeIdAndEmailAndStatus(
      UUID officeId, String email, String status);
}
