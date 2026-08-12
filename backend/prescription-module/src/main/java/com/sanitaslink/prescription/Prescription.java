package com.sanitaslink.prescription;

import com.sanitaslink.core.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A prescription request or issued ticket of an office. Sensitive fields are encrypted. */
@Entity
@Table(name = "prescriptions")
public class Prescription extends com.sanitaslink.core.domain.AbstractBaseEntity {

  @Id private UUID id;

  @Column(name = "office_id", nullable = false)
  private UUID officeId;

  @Column(name = "patient_id")
  private UUID patientId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "medication", nullable = false, length = 600)
  private String medication;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "instructions", length = 20000)
  private String instructions;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "printed_at")
  private Instant printedAt;

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMedication() {
    return medication;
  }

  public void setMedication(String medication) {
    this.medication = medication;
  }

  public String getInstructions() {
    return instructions;
  }

  public void setInstructions(String instructions) {
    this.instructions = instructions;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  public Instant getPrintedAt() {
    return printedAt;
  }

  public void setPrintedAt(Instant printedAt) {
    this.printedAt = printedAt;
  }
}
