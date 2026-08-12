package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** A granular permission from the centrally managed, mutable permission catalog. */
@Entity
@Table(name = "permissions")
public class Permission extends AbstractBaseEntity {

  @Id private UUID id;

  @Column(name = "code", nullable = false, length = 100)
  private String code;

  @Column(name = "module", nullable = false, length = 50)
  private String module;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "active", nullable = false)
  private Boolean active = true;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  public static Permission create(
      UUID id, String code, String module, String name, String description) {
    Permission permission = new Permission();
    permission.id = id;
    permission.code = code;
    permission.module = module;
    permission.name = name;
    permission.description = description;
    permission.active = true;
    return permission;
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

  public String getModule() {
    return module;
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
