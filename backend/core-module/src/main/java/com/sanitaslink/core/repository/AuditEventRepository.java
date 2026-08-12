package com.sanitaslink.core.repository;

import com.sanitaslink.core.audit.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link AuditEvent}. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}
