package com.sanitaslink.appointment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Appointment}. */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

  /**
   * Atomically transitions an appointment from the observed status to the target status. Returns
   * zero when the row no longer has the expected status (concurrent change or terminal state).
   */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE Appointment a SET a.status = :target WHERE a.id = :id AND a.status = :from")
  int transitionStatus(
      @Param("id") UUID id, @Param("from") String from, @Param("target") String target);
}
