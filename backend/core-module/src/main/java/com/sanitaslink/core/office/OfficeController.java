package com.sanitaslink.core.office;

import com.sanitaslink.core.invitation.InvitationService;
import com.sanitaslink.core.office.dto.AssignRoleRequest;
import com.sanitaslink.core.office.dto.InvitationResponse;
import com.sanitaslink.core.office.dto.InviteMemberRequest;
import com.sanitaslink.core.office.dto.OfficeMemberResponse;
import com.sanitaslink.core.office.dto.OfficeResponse;
import com.sanitaslink.core.office.dto.UpdateOfficeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Member-facing office, staff and invitation endpoints. */
@RestController
@RequestMapping("/api/v1/offices")
@Tag(name = "Office", description = "Office information, staff management and invitations")
public class OfficeController {

  private final OfficeService officeService;
  private final MembershipService membershipService;
  private final InvitationService invitationService;

  public OfficeController(
      OfficeService officeService,
      MembershipService membershipService,
      InvitationService invitationService) {
    this.officeService = officeService;
    this.membershipService = membershipService;
    this.invitationService = invitationService;
  }

  @GetMapping("/{officeId}")
  @PreAuthorize("hasAuthority('CORE_OFFICE_READ')")
  @Operation(summary = "View office information")
  public ResponseEntity<OfficeResponse> getOffice(@PathVariable UUID officeId) {
    return ResponseEntity.ok(officeService.getOffice(officeId));
  }

  @PatchMapping("/{officeId}")
  @PreAuthorize("hasAuthority('CORE_OFFICE_UPDATE')")
  @Operation(summary = "Update office information")
  public ResponseEntity<OfficeResponse> updateOffice(
      @PathVariable UUID officeId, @Valid @RequestBody UpdateOfficeRequest request) {
    return ResponseEntity.ok(officeService.updateOffice(officeId, request));
  }

  @GetMapping("/{officeId}/members")
  @PreAuthorize("hasAuthority('CORE_STAFF_MANAGE')")
  @Operation(summary = "List office members")
  public ResponseEntity<List<OfficeMemberResponse>> listMembers(@PathVariable UUID officeId) {
    return ResponseEntity.ok(membershipService.listMembers(officeId));
  }

  @PatchMapping("/{officeId}/members/{userId}/role")
  @PreAuthorize("hasAuthority('CORE_STAFF_MANAGE')")
  @Operation(summary = "Assign an office role to a member")
  public ResponseEntity<Void> assignRole(
      @PathVariable UUID officeId,
      @PathVariable UUID userId,
      @Valid @RequestBody AssignRoleRequest request) {
    membershipService.assignRole(officeId, userId, request.roleId());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{officeId}/members/{userId}/roles/{roleId}")
  @PreAuthorize("hasAuthority('CORE_STAFF_MANAGE')")
  @Operation(summary = "Revoke an office role from a member")
  public ResponseEntity<Void> revokeRole(
      @PathVariable UUID officeId, @PathVariable UUID userId, @PathVariable UUID roleId) {
    membershipService.revokeRole(officeId, userId, roleId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{officeId}/members/{userId}")
  @PreAuthorize("hasAuthority('CORE_STAFF_MANAGE')")
  @Operation(summary = "Remove a member from the office")
  public ResponseEntity<Void> removeMember(@PathVariable UUID officeId, @PathVariable UUID userId) {
    membershipService.removeMember(officeId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{officeId}/invitations")
  @PreAuthorize("hasAuthority('CORE_STAFF_INVITE')")
  @Operation(summary = "Invite a collaborator to the office")
  public ResponseEntity<InvitationResponse> inviteMember(
      @PathVariable UUID officeId, @Valid @RequestBody InviteMemberRequest request) {
    return ResponseEntity.ok(invitationService.invite(officeId, request));
  }

  @GetMapping("/{officeId}/invitations")
  @PreAuthorize("hasAuthority('CORE_STAFF_MANAGE')")
  @Operation(summary = "List office invitations")
  public ResponseEntity<List<InvitationResponse>> listInvitations(@PathVariable UUID officeId) {
    return ResponseEntity.ok(invitationService.listInvitations(officeId));
  }

  @DeleteMapping("/{officeId}/invitations/{invitationId}")
  @PreAuthorize("hasAuthority('CORE_STAFF_INVITE')")
  @Operation(summary = "Revoke a pending invitation")
  public ResponseEntity<Void> revokeInvitation(
      @PathVariable UUID officeId, @PathVariable UUID invitationId) {
    invitationService.revokeInvitation(officeId, invitationId);
    return ResponseEntity.noContent().build();
  }
}
