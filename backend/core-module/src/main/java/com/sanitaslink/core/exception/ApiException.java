package com.sanitaslink.core.exception;

import org.springframework.http.HttpStatus;

/** Base application exception mapped to an RFC 7807 {@code ProblemDetail} response. */
public class ApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  public ApiException(String errorCode, HttpStatus status, String message) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public static ApiException conflict(String errorCode, String message) {
    return new ApiException(errorCode, HttpStatus.CONFLICT, message);
  }

  public static ApiException notFound(String errorCode, String message) {
    return new ApiException(errorCode, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException badRequest(String errorCode, String message) {
    return new ApiException(errorCode, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException forbidden(String errorCode, String message) {
    return new ApiException(errorCode, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException unauthorized(String errorCode, String message) {
    return new ApiException(errorCode, HttpStatus.UNAUTHORIZED, message);
  }
}
