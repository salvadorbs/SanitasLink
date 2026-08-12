package com.sanitaslink.core.appointment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Appointment}. */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {}
