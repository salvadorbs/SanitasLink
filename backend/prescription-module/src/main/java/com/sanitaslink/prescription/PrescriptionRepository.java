package com.sanitaslink.prescription;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Prescription}. */
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

  /** Atomically transitions a requested prescription to issued. Returns the updated row count. */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE Prescription p SET p.status = 'ISSUED', p.issuedAt = :now "
          + "WHERE p.id = :id AND p.status = 'REQUESTED'")
  int issueIfRequested(@Param("id") UUID id, @Param("now") Instant now);

  /** Atomically transitions an issued prescription to printed. Returns the updated row count. */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE Prescription p SET p.status = 'PRINTED', p.printedAt = :now "
          + "WHERE p.id = :id AND p.status IN ('ISSUED', 'PRINTED')")
  int printIfIssued(@Param("id") UUID id, @Param("now") Instant now);
}
