package com.sanitaslink.core.office.admin;

import com.sanitaslink.core.invitation.InvitationService;
import com.sanitaslink.core.office.dto.CreateOfficeRequest;
import com.sanitaslink.core.office.dto.InvitationResponse;
import com.sanitaslink.core.office.dto.InviteMemberRequest;
import com.sanitaslink.core.office.dto.OfficeMemberResponse;
import com.sanitaslink.core.office.dto.OfficeResponse;
import com.sanitaslink.core.office.dto.PermissionResponse;
import com.sanitaslink.core.office.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global platform administration endpoints. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Platform administration of offices, members and catalogs")
public class AdminOfficeController {

  private final AdminOfficeService adminOfficeService;
  private final InvitationService invitationService;

  public AdminOfficeController(
      AdminOfficeService adminOfficeService, InvitationService invitationService) {
    this.adminOfficeService = adminOfficeService;
    this.invitationService = invitationService;
  }

  @PostMapping("/offices")
  @Operation(summary = "Provision a new office with its titular doctor")
  public ResponseEntity<OfficeResponse> createOffice(
      @Valid @RequestBody CreateOfficeRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(adminOfficeService.createOffice(request));
  }

  @GetMapping("/offices")
  @Operation(summary = "List all offices")
  public ResponseEntity<List<OfficeResponse>> listOffices() {
    return ResponseEntity.ok(adminOfficeService.listOffices());
  }

  @GetMapping("/offices/{officeId}")
  @Operation(summary = "View an office")
  public ResponseEntity<OfficeResponse> getOffice(@PathVariable UUID officeId) {
    return ResponseEntity.ok(adminOfficeService.getOffice(officeId));
  }

  @GetMapping("/offices/{officeId}/members")
  @Operation(summary = "List the active members of an office")
  public ResponseEntity<List<OfficeMemberResponse>> listMembers(@PathVariable UUID officeId) {
    return ResponseEntity.ok(adminOfficeService.listMembers(officeId));
  }

  @PostMapping("/offices/{officeId}/members")
  @Operation(summary = "Provision a collaborator into an office")
  public ResponseEntity<InvitationResponse> addMember(
      @PathVariable UUID officeId, @Valid @RequestBody InviteMemberRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(invitationService.invite(officeId, request));
  }

  @GetMapping("/roles")
  @Operation(summary = "List the role catalog")
  public ResponseEntity<List<RoleResponse>> listRoles() {
    return ResponseEntity.ok(adminOfficeService.listRoles());
  }

  @GetMapping("/permissions")
  @Operation(summary = "List the permission catalog")
  public ResponseEntity<List<PermissionResponse>> listPermissions() {
    return ResponseEntity.ok(adminOfficeService.listPermissions());
  }
}
