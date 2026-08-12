package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.OfficeMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link OfficeMembership}. */
public interface OfficeMembershipRepository extends JpaRepository<OfficeMembership, UUID> {

  /** Mono-office invariant: a user has at most one membership. */
  Optional<OfficeMembership> findByUserId(UUID userId);

  Optional<OfficeMembership> findByUserIdAndStatus(UUID userId, String status);

  List<OfficeMembership> findByOfficeId(UUID officeId);

  List<OfficeMembership> findByOfficeIdAndStatus(UUID officeId, String status);
}
