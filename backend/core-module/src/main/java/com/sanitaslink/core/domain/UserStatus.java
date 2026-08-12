package com.sanitaslink.core.domain;

/** User lifecycle status values (stored as strings in the {@code users} table). */
public final class UserStatus {

  public static final String INVITED = "INVITED";
  public static final String ACTIVE = "ACTIVE";
  public static final String DISABLED = "DISABLED";
  public static final String LOCKED = "LOCKED";

  private UserStatus() {}
}
