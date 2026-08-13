package com.sanitaslink.core.auth;

import com.sanitaslink.core.auth.dto.AcceptInvitationRequest;
import com.sanitaslink.core.auth.dto.ChangePasswordRequest;
import com.sanitaslink.core.auth.dto.ConfirmPasswordResetRequest;
import com.sanitaslink.core.auth.dto.LoginRequest;
import com.sanitaslink.core.auth.dto.LoginResponse;
import com.sanitaslink.core.auth.dto.MeResponse;
import com.sanitaslink.core.auth.dto.RequestPasswordResetRequest;
import com.sanitaslink.core.config.CorsProperties;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication and account lifecycle endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description = "Login, token lifecycle, invitation acceptance and password management")
public class AuthController {

  private final AuthService authService;
  private final AuthCookies authCookies;
  private final CorsProperties corsProperties;
  private final ClientIpResolver clientIpResolver;

  public AuthController(
      AuthService authService,
      AuthCookies authCookies,
      CorsProperties corsProperties,
      ClientIpResolver clientIpResolver) {
    this.authService = authService;
    this.authCookies = authCookies;
    this.corsProperties = corsProperties;
    this.clientIpResolver = clientIpResolver;
  }

  @PostMapping("/login")
  @Operation(
      summary = "Login and obtain an access token",
      description =
          "Sets the refresh token in an HttpOnly cookie scoped to /api/v1/auth. "
              + "Authenticates through the refresh cookie: the client must send credentials "
              + "cookies (withCredentials) and no bearer header.",
      security = @SecurityRequirement(name = "cookieAuth"))
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
    assertCookieOrigin(httpServletRequest);
    AuthService.TokenPair pair =
        authService.login(request, clientIpResolver.clientIp(httpServletRequest));
    return responseWithCookie(pair);
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Rotate the refresh token and obtain a new access token",
      description =
          "Reads the refresh token from the HttpOnly cookie (no request body). "
              + "Authenticates through the refresh cookie: no bearer header is used.",
      security = @SecurityRequirement(name = "cookieAuth"))
  public ResponseEntity<LoginResponse> refresh(HttpServletRequest httpServletRequest) {
    assertCookieOrigin(httpServletRequest);
    AuthService.TokenPair pair =
        authService.refresh(
            authCookies.rawRefreshToken(cookies(httpServletRequest)),
            clientIpResolver.clientIp(httpServletRequest));
    return responseWithCookie(pair);
  }

  @PostMapping("/logout")
  @Operation(
      summary = "Revoke the refresh token from the HttpOnly cookie",
      description =
          "Reads the refresh token from the HttpOnly cookie (no request body) and expires it. "
              + "Authenticates through the refresh cookie: no bearer header is used.",
      security = @SecurityRequirement(name = "cookieAuth"))
  public ResponseEntity<Void> logout(HttpServletRequest httpServletRequest) {
    assertCookieOrigin(httpServletRequest);
    authService.logout(
        authCookies.rawRefreshToken(cookies(httpServletRequest)),
        clientIpResolver.clientIp(httpServletRequest));
    return ResponseEntity.noContent()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE, authCookies.setCookieHeader(authCookies.clearRefreshCookie()))
        .build();
  }

  @GetMapping("/me")
  @Operation(summary = "Current user profile with effective roles and permissions")
  public ResponseEntity<MeResponse> me() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.me());
  }

  @PostMapping("/invitations/accept")
  @Operation(summary = "Accept an office invitation and set the first-access password")
  public ResponseEntity<LoginResponse> acceptInvitation(
      @Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest httpServletRequest) {
    assertCookieOrigin(httpServletRequest);
    return responseWithCookie(authService.acceptInvitation(request), HttpStatus.CREATED);
  }

  @PostMapping("/password-reset/request")
  @Operation(summary = "Request a password reset (uniform response, no account enumeration)")
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody RequestPasswordResetRequest request,
      HttpServletRequest httpServletRequest) {
    authService.requestPasswordReset(request, clientIpResolver.clientIp(httpServletRequest));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/password-reset/confirm")
  @Operation(summary = "Confirm a password reset with the one-time token")
  public ResponseEntity<Void> confirmPasswordReset(
      @Valid @RequestBody ConfirmPasswordResetRequest request) {
    authService.confirmPasswordReset(request);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/password/change")
  @Operation(summary = "Change the password of the authenticated user")
  public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(request);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private ResponseEntity<LoginResponse> responseWithCookie(AuthService.TokenPair pair) {
    return responseWithCookie(pair, HttpStatus.OK);
  }

  private ResponseEntity<LoginResponse> responseWithCookie(
      AuthService.TokenPair pair, HttpStatus status) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE,
            authCookies.setCookieHeader(authCookies.refreshCookie(pair.rawRefreshToken())))
        .body(pair.response());
  }

  /**
   * Rejects cookie-bearing requests from disallowed origins (defense in depth next to SameSite).
   */
  private void assertCookieOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin == null || origin.isBlank()) {
      return;
    }
    if (!corsProperties.getAllowedOrigins().contains(origin)) {
      throw new ApiException(
          ErrorCodes.CSRF_REJECTED, HttpStatus.FORBIDDEN, "Cross-site request rejected");
    }
  }

  private List<HttpCookie> cookies(HttpServletRequest request) {
    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return List.of();
    }
    return Arrays.stream(cookies)
        .map(cookie -> new HttpCookie(cookie.getName(), cookie.getValue()))
        .toList();
  }
}
