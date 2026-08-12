package com.sanitaslink.core.prescription;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Prescription}. */
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {}
