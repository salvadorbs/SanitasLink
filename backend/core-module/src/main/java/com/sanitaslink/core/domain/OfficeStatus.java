package com.sanitaslink.core.domain;

/** Office lifecycle status values (stored as strings in the {@code offices} table). */
public final class OfficeStatus {

  public static final String ACTIVE = "ACTIVE";
  public static final String SUSPENDED = "SUSPENDED";
  public static final String DELETED = "DELETED";

  private OfficeStatus() {}
}
