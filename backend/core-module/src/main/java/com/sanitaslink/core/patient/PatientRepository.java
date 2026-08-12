package com.sanitaslink.core.patient;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Patient}. */
public interface PatientRepository extends JpaRepository<Patient, UUID> {}
