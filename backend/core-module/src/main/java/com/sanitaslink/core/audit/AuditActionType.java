package com.sanitaslink.core.audit;

/** Audit action types. */
public final class AuditActionType {

  public static final String LOGIN = "LOGIN";
  public static final String LOGIN_FAILED = "LOGIN_FAILED";
  public static final String LOGOUT = "LOGOUT";
  public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
  public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
  public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
  public static final String PASSWORD_RESET_CONFIRMED = "PASSWORD_RESET_CONFIRMED";
  public static final String OFFICE_CREATED = "OFFICE_CREATED";
  public static final String OFFICE_UPDATED = "OFFICE_UPDATED";
  public static final String MEMBER_INVITED = "MEMBER_INVITED";
  public static final String INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
  public static final String INVITATION_REVOKED = "INVITATION_REVOKED";
  public static final String MEMBER_ROLE_CHANGED = "MEMBER_ROLE_CHANGED";
  public static final String MEMBER_REMOVED = "MEMBER_REMOVED";

  private AuditActionType() {}
}
