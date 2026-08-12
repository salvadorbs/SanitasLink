package com.sanitaslink.core.security;

import com.sanitaslink.core.exception.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/** Returns RFC 7807 ProblemDetail JSON when an authenticated user lacks permission. */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    Map<String, Object> body =
        Map.of(
            "type",
            "about:blank",
            "title",
            ErrorCodes.OPERATION_CONFLICT,
            "status",
            HttpStatus.FORBIDDEN.value(),
            "detail",
            "You do not have permission to perform this action",
            "timestamp",
            Instant.now().toString());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
