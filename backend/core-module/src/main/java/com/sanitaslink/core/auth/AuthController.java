package com.sanitaslink.core.auth;

import com.sanitaslink.core.auth.dto.AcceptInvitationRequest;
import com.sanitaslink.core.auth.dto.ChangePasswordRequest;
import com.sanitaslink.core.auth.dto.ConfirmPasswordResetRequest;
import com.sanitaslink.core.auth.dto.LoginRequest;
import com.sanitaslink.core.auth.dto.LoginResponse;
import com.sanitaslink.core.auth.dto.LogoutRequest;
import com.sanitaslink.core.auth.dto.MeResponse;
import com.sanitaslink.core.auth.dto.RefreshTokenRequest;
import com.sanitaslink.core.auth.dto.RequestPasswordResetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  @Operation(summary = "Login and obtain tokens")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
    return ResponseEntity.ok(authService.login(request, clientIp(httpServletRequest)));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate the refresh token and obtain a new access token")
  public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(authService.refresh(request));
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke the provided refresh token")
  public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  @Operation(summary = "Current user profile with effective roles and permissions")
  public ResponseEntity<MeResponse> me() {
    return ResponseEntity.ok(authService.me());
  }

  @PostMapping("/invitations/accept")
  @Operation(summary = "Accept an office invitation and set the first-access password")
  public ResponseEntity<LoginResponse> acceptInvitation(
      @Valid @RequestBody AcceptInvitationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.acceptInvitation(request));
  }

  @PostMapping("/password-reset/request")
  @Operation(summary = "Request a password reset (uniform response, no account enumeration)")
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody RequestPasswordResetRequest request) {
    authService.requestPasswordReset(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password-reset/confirm")
  @Operation(summary = "Confirm a password reset with the one-time token")
  public ResponseEntity<Void> confirmPasswordReset(
      @Valid @RequestBody ConfirmPasswordResetRequest request) {
    authService.confirmPasswordReset(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password/change")
  @Operation(summary = "Change the password of the authenticated user")
  public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(request);
    return ResponseEntity.noContent().build();
  }

  private String clientIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
