package com.sanitaslink.appointment;

import com.sanitaslink.core.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An appointment of an office. Organisational notes are encrypted at rest. */
@Entity
@Table(name = "appointments")
public class Appointment extends com.sanitaslink.core.domain.AbstractBaseEntity {

  @Id private UUID id;

  @Column(name = "office_id", nullable = false)
  private UUID officeId;

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "notes", length = 2000)
  private String notes;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getOfficeId() {
    return officeId;
  }

  public void setOfficeId(UUID officeId) {
    this.officeId = officeId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
