package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** A role from the centrally managed, mutable role catalog. */
@Entity
@Table(name = "roles")
public class Role extends AbstractBaseEntity {

  @Id private UUID id;

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "scope", nullable = false, length = 20)
  private String scope;

  @Column(name = "system_role", nullable = false)
  private Boolean systemRole = true;

  @Column(name = "active", nullable = false)
  private Boolean active = true;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  public static Role create(
      UUID id, String code, String name, String description, String scope, boolean systemRole) {
    Role role = new Role();
    role.id = id;
    role.code = code;
    role.name = name;
    role.description = description;
    role.scope = scope;
    role.systemRole = systemRole;
    role.active = true;
    return role;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getScope() {
    return scope;
  }

  public Boolean getSystemRole() {
    return systemRole;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Integer getVersion() {
    return version;
  }
}
