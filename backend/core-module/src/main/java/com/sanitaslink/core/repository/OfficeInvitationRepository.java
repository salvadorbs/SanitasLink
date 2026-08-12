package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.OfficeInvitation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link OfficeInvitation}. */
public interface OfficeInvitationRepository extends JpaRepository<OfficeInvitation, UUID> {

  Optional<OfficeInvitation> findByTokenHash(String tokenHash);

  List<OfficeInvitation> findByOfficeId(UUID officeId);

  List<OfficeInvitation> findByOfficeIdAndStatus(UUID officeId, String status);

  Optional<OfficeInvitation> findByIdAndOfficeId(UUID id, UUID officeId);

  Optional<OfficeInvitation> findFirstByOfficeIdAndEmailAndStatus(
      UUID officeId, String email, String status);
}
