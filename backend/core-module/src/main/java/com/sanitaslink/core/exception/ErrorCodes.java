package com.sanitaslink.core.exception;

/** Machine-readable error codes used in RFC 7807 ProblemDetail responses. */
public final class ErrorCodes {

  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
  public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String CSRF_REJECTED = "CSRF_REJECTED";
  public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
  public static final String INVALID_INVITATION_TOKEN = "INVALID_INVITATION_TOKEN";
  public static final String INVITATION_EXPIRED = "INVITATION_EXPIRED";
  public static final String INVITATION_ALREADY_USED = "INVITATION_ALREADY_USED";
  public static final String INVITATION_CONFLICT = "INVITATION_CONFLICT";
  public static final String INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN";
  public static final String RESET_TOKEN_EXPIRED = "RESET_TOKEN_EXPIRED";
  public static final String USER_ALREADY_MEMBER = "USER_ALREADY_MEMBER";
  public static final String OFFICE_NOT_FOUND = "OFFICE_NOT_FOUND";
  public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
  public static final String ROLE_NOT_ACTIVE = "ROLE_NOT_ACTIVE";
  public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
  public static final String MEMBERSHIP_NOT_FOUND = "MEMBERSHIP_NOT_FOUND";
  public static final String LAST_PRACTICE_OWNER = "LAST_PRACTICE_OWNER";
  public static final String OFFICE_MISMATCH = "OFFICE_MISMATCH";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String OPERATION_CONFLICT = "OPERATION_CONFLICT";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private ErrorCodes() {}
}
