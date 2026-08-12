package com.sanitaslink.core.patient;

import com.sanitaslink.core.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A patient of an office. Sensitive fields are encrypted at rest. */
@Entity
@Table(name = "patients")
public class Patient extends com.sanitaslink.core.domain.AbstractBaseEntity {

  @Id private UUID id;

  @Column(name = "office_id", nullable = false)
  private UUID officeId;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "tax_identifier", length = 255)
  private String taxIdentifier;

  @Column(name = "email", length = 320)
  private String email;

  @Column(name = "phone", length = 30)
  private String phone;

  @Column(name = "address", length = 300)
  private String address;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "clinical_notes", length = 20000)
  private String clinicalNotes;

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

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public String getTaxIdentifier() {
    return taxIdentifier;
  }

  public void setTaxIdentifier(String taxIdentifier) {
    this.taxIdentifier = taxIdentifier;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getClinicalNotes() {
    return clinicalNotes;
  }

  public void setClinicalNotes(String clinicalNotes) {
    this.clinicalNotes = clinicalNotes;
  }
}
