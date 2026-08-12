package com.sanitaslink.core.repository;

import com.sanitaslink.core.domain.Office;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Office}. */
public interface OfficeRepository extends JpaRepository<Office, UUID> {

  List<Office> findByStatus(String status);
}
