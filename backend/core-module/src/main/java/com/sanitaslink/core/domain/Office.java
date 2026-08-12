package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** An office (studio): the tenant for all business data. */
@Entity
@Table(name = "offices")
public class Office extends AbstractBaseEntity {

  @Id private UUID id;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "legal_name", length = 200)
  private String legalName;

  @Column(name = "tax_identifier", length = 50)
  private String taxIdentifier;

  @Column(name = "email", length = 320)
  private String email;

  @Column(name = "phone", length = 30)
  private String phone;

  @Column(name = "address", length = 300)
  private String address;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  public static Office create(
      UUID id,
      String name,
      String legalName,
      String taxIdentifier,
      String email,
      String phone,
      String address) {
    Office office = new Office();
    office.id = id;
    office.name = name;
    office.legalName = legalName;
    office.taxIdentifier = taxIdentifier;
    office.email = email;
    office.phone = phone;
    office.address = address;
    office.status = OfficeStatus.ACTIVE;
    return office;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLegalName() {
    return legalName;
  }

  public void setLegalName(String legalName) {
    this.legalName = legalName;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getVersion() {
    return version;
  }
}
