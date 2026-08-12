package com.sanitaslink.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** End-to-end tests for the identity, office, authorization and invitation flows. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthAndOfficeIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("sanitaslink_db")
          .withUsername("db_owner")
          .withPassword("db_owner")
          .withInitScript("postgres-init.sql");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "app_user");
    registry.add("spring.datasource.password", () -> "app_user");
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", () -> "db_owner");
    registry.add("spring.flyway.password", () -> "db_owner");
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TestDataSeeder seeder;
  @Autowired RecordingNotificationPort notificationPort;

  private static int counter = 0;

  private String uniqueEmail(String prefix) {
    return prefix + (counter++) + "@example.it";
  }

  @Test
  void adminProvisionsOffice_titularAccepts_collaboratorIsScopedByRole() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");

    // Admin provisions an office with its titular doctor.
    String officeName = "Studio Medico San Marco";
    String ownerEmail = uniqueEmail("titolare");
    JsonNode created =
        doPost(
            "/api/v1/admin/offices",
            objectMapper
                .createObjectNode()
                .put("name", officeName)
                .put("taxIdentifier", "12345678901")
                .put("ownerEmail", ownerEmail)
                .put("ownerFirstName", "Roberto")
                .put("ownerLastName", "Rossi")
                .toString(),
            adminToken,
            200);
    String officeId = created.get("id").asText();
    assertThat(officeId).isNotBlank();

    // Admin lists offices and the role catalog.
    JsonNode offices = doGet("/api/v1/admin/offices", adminToken, 200);
    assertThat(offices.size()).isGreaterThanOrEqualTo(1);
    JsonNode roles = doGet("/api/v1/admin/roles", adminToken, 200);
    String baseSecretaryRoleId = roleId(roles, "SEGRETARIA_BASE");
    String advancedSecretaryRoleId = roleId(roles, "SEGRETARIA_AVANZATA");

    // Titular accepts the invitation (first access).
    String invitationToken = notificationPort.takeInvitationToken();
    assertThat(invitationToken).isNotBlank();
    JsonNode accept =
        doPost(
            "/api/v1/auth/invitations/accept",
            objectMapper
                .createObjectNode()
                .put("token", invitationToken)
                .put("firstName", "Roberto")
                .put("lastName", "Rossi")
                .put("password", "titular-pass-123")
                .toString(),
            null,
            201);
    String titularAccess = accept.get("accessToken").asText();

    // Titular profile: full office permissions.
    JsonNode titularMe = doGet("/api/v1/auth/me", titularAccess, 200);
    assertThat(rolesOf(titularMe)).contains("MEDICO_TITOLARE");
    assertThat(permissionsOf(titularMe))
        .contains(
            "CORE_OFFICE_READ",
            "CORE_OFFICE_UPDATE",
            "CORE_STAFF_INVITE",
            "CORE_STAFF_MANAGE",
            "PATIENT_CLINICAL_READ",
            "PATIENT_CLINICAL_WRITE",
            "PRESCRIPTION_WRITE");

    // Titular can view the office.
    doGet("/api/v1/offices/" + officeId, titularAccess, 200);

    // Titular invites a base secretary.
    String secretaryEmail = uniqueEmail("laura");
    doPost(
        "/api/v1/offices/" + officeId + "/invitations",
        objectMapper
            .createObjectNode()
            .put("email", secretaryEmail)
            .put("roleId", baseSecretaryRoleId)
            .toString(),
        titularAccess,
        200);
    String secretaryInvitationToken = notificationPort.takeInvitationToken();
    assertThat(secretaryInvitationToken).isNotBlank();
    assertThat(notificationPort.lastInvitationEmail()).isEqualTo(secretaryEmail);

    // Secretary accepts and logs in.
    JsonNode secretaryAccept =
        doPost(
            "/api/v1/auth/invitations/accept",
            objectMapper
                .createObjectNode()
                .put("token", secretaryInvitationToken)
                .put("firstName", "Laura")
                .put("lastName", "Bianchi")
                .put("password", "laura-pass-123")
                .toString(),
            null,
            201);
    String secretaryAccess = secretaryAccept.get("accessToken").asText();
    JsonNode secretaryMe = doGet("/api/v1/auth/me", secretaryAccess, 200);
    String secretaryUserId = secretaryMe.get("id").asText();

    assertThat(rolesOf(secretaryMe)).contains("SEGRETARIA_BASE");
    assertThat(permissionsOf(secretaryMe))
        .contains(
            "APPOINTMENT_READ",
            "APPOINTMENT_CREATE",
            "PATIENT_REGISTRY_READ",
            "PRESCRIPTION_REQUEST_CREATE")
        .doesNotContain(
            "PATIENT_CLINICAL_READ",
            "PATIENT_CLINICAL_WRITE",
            "PRESCRIPTION_WRITE",
            "CORE_STAFF_MANAGE",
            "PRESCRIPTION_PRINT");

    // Secretary is denied office/staff management.
    doPatch(
        "/api/v1/offices/" + officeId,
        objectMapper.createObjectNode().put("name", "Hacked").toString(),
        secretaryAccess,
        403);
    doGet("/api/v1/offices/" + officeId + "/members", secretaryAccess, 403);
    doPost(
        "/api/v1/offices/" + officeId + "/invitations",
        objectMapper
            .createObjectNode()
            .put("email", uniqueEmail("x"))
            .put("roleId", baseSecretaryRoleId)
            .toString(),
        secretaryAccess,
        403);
    doGet("/api/v1/admin/offices", secretaryAccess, 403);

    // Titular can manage members and upgrade the secretary to advanced.
    JsonNode members = doGet("/api/v1/offices/" + officeId + "/members", titularAccess, 200);
    assertThat(members.toString()).contains(secretaryEmail);
    doPatch(
        "/api/v1/offices/" + officeId + "/members/" + secretaryUserId + "/role",
        objectMapper.createObjectNode().put("roleId", advancedSecretaryRoleId).toString(),
        titularAccess,
        204);

    // The upgraded secretary now has the PRINT permission.
    JsonNode upgradedMe = doGet("/api/v1/auth/me", secretaryAccess, 200);
    assertThat(permissionsOf(upgradedMe)).contains("PRESCRIPTION_PRINT");
  }

  @Test
  void crossOfficeAccessIsDenied() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");

    String officeA = createOffice(adminToken, "Studio Alpha", uniqueEmail("alpha"));
    String ownerAToken = acceptAndLogin();
    String officeB = createOffice(adminToken, "Studio Beta", uniqueEmail("beta"));
    String ownerBToken = acceptAndLogin();

    // Owner A cannot access office B.
    doGet("/api/v1/offices/" + officeB, ownerAToken, 403);
    doGet("/api/v1/offices/" + officeB + "/members", ownerAToken, 403);
    doPost(
        "/api/v1/offices/" + officeB + "/invitations",
        objectMapper
            .createObjectNode()
            .put("email", uniqueEmail("spy"))
            .put("roleId", anyOfficeRoleId(adminToken))
            .toString(),
        ownerAToken,
        403);

    // Owner B cannot access office A either.
    doGet("/api/v1/offices/" + officeA, ownerBToken, 403);
  }

  @Test
  void authenticationAndTokenLifecycle() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");

    // Wrong credentials and unknown users both return 401.
    doPost(
        "/api/v1/auth/login",
        objectMapper
            .createObjectNode()
            .put("email", adminEmail)
            .put("password", "wrong-pass-999")
            .toString(),
        null,
        401);
    doPost(
        "/api/v1/auth/login",
        objectMapper
            .createObjectNode()
            .put("email", uniqueEmail("ghost"))
            .put("password", "x")
            .toString(),
        null,
        401);

    // Unauthenticated protected access returns 401.
    doGet("/api/v1/auth/me", null, 401);

    // Refresh rotates the refresh token; logout revokes it.
    JsonNode login =
        doPost(
            "/api/v1/auth/login",
            objectMapper
                .createObjectNode()
                .put("email", adminEmail)
                .put("password", "admin-pass-123")
                .toString(),
            null,
            200);
    String refresh1 = login.get("refreshToken").asText();
    JsonNode refreshed =
        doPost(
            "/api/v1/auth/refresh",
            objectMapper.createObjectNode().put("refreshToken", refresh1).toString(),
            null,
            200);
    String refresh2 = refreshed.get("refreshToken").asText();
    assertThat(refresh2).isNotEqualTo(refresh1);

    // Reusing the rotated token fails.
    doPost(
        "/api/v1/auth/refresh",
        objectMapper.createObjectNode().put("refreshToken", refresh1).toString(),
        null,
        401);

    doPost(
        "/api/v1/auth/logout",
        objectMapper.createObjectNode().put("refreshToken", refresh2).toString(),
        null,
        204);
    doPost(
        "/api/v1/auth/refresh",
        objectMapper.createObjectNode().put("refreshToken", refresh2).toString(),
        null,
        401);
  }

  @Test
  void passwordResetFlow() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String ownerEmail = uniqueEmail("owner");
    createOffice(adminToken, "Studio Reset", ownerEmail);

    String ownerInvitation = notificationPort.takeInvitationToken();
    assertThat(ownerInvitation).isNotBlank();
    doPost(
        "/api/v1/auth/invitations/accept",
        objectMapper
            .createObjectNode()
            .put("token", ownerInvitation)
            .put("firstName", "N")
            .put("lastName", "O")
            .put("password", "owner-pass-123")
            .toString(),
        null,
        201);

    // Request reset (uniform 204).
    doPost(
        "/api/v1/auth/password-reset/request",
        objectMapper.createObjectNode().put("email", ownerEmail).toString(),
        null,
        204);
    String resetToken = notificationPort.takeResetToken();
    assertThat(resetToken).isNotBlank();

    doPost(
        "/api/v1/auth/password-reset/confirm",
        objectMapper
            .createObjectNode()
            .put("token", resetToken)
            .put("newPassword", "new-owner-pass-456")
            .toString(),
        null,
        204);

    // Old password no longer works, new one does.
    doPost(
        "/api/v1/auth/login",
        objectMapper
            .createObjectNode()
            .put("email", ownerEmail)
            .put("password", "owner-pass-123")
            .toString(),
        null,
        401);
    doPost(
        "/api/v1/auth/login",
        objectMapper
            .createObjectNode()
            .put("email", ownerEmail)
            .put("password", "new-owner-pass-456")
            .toString(),
        null,
        200);
  }

  @Test
  void memberManagementInvariantsAreEnforced() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");

    String officeId = createOffice(adminToken, "Studio Invariants", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    JsonNode roles = doGet("/api/v1/admin/roles", adminToken, 200);
    String ownerRoleId = roleId(roles, "MEDICO_TITOLARE");
    String collaboratorRoleId = roleId(roles, "MEDICO_COLLABORATORE");

    // Invite and activate a collaborator.
    String collaboratorEmail = uniqueEmail("collaborator");
    doPost(
        "/api/v1/offices/" + officeId + "/invitations",
        objectMapper
            .createObjectNode()
            .put("email", collaboratorEmail)
            .put("roleId", collaboratorRoleId)
            .toString(),
        ownerToken,
        200);
    String collaboratorInvitation = notificationPort.takeInvitationToken();
    JsonNode collaboratorAccept =
        doPost(
            "/api/v1/auth/invitations/accept",
            objectMapper
                .createObjectNode()
                .put("token", collaboratorInvitation)
                .put("firstName", "Col")
                .put("lastName", "Lab")
                .put("password", "collab-pass-123")
                .toString(),
            null,
            201);
    JsonNode collaboratorMe =
        doGet("/api/v1/auth/me", collaboratorAccept.get("accessToken").asText(), 200);
    String collaboratorUserId = collaboratorMe.get("id").asText();

    // The last practice owner cannot be removed nor lose the owner role.
    doDelete("/api/v1/offices/" + officeId + "/members/" + ownerId(ownerToken), ownerToken, 409);
    doDelete(
        "/api/v1/offices/" + officeId + "/members/" + ownerId(ownerToken) + "/roles/" + ownerRoleId,
        ownerToken,
        409);

    // A member must keep at least one role.
    doDelete(
        "/api/v1/offices/"
            + officeId
            + "/members/"
            + collaboratorUserId
            + "/roles/"
            + collaboratorRoleId,
        ownerToken,
        409);
  }

  private String ownerId(String ownerToken) throws Exception {
    return doGet("/api/v1/auth/me", ownerToken, 200).get("id").asText();
  }

  private String createOffice(String adminToken, String name, String ownerEmail) throws Exception {
    JsonNode resp =
        doPost(
            "/api/v1/admin/offices",
            objectMapper
                .createObjectNode()
                .put("name", name)
                .put("ownerEmail", ownerEmail)
                .put("ownerFirstName", "Owner")
                .put("ownerLastName", "Test")
                .toString(),
            adminToken,
            200);
    return resp.get("id").asText();
  }

  private String acceptAndLogin() throws Exception {
    String token = notificationPort.takeInvitationToken();
    assertThat(token).isNotBlank();
    JsonNode accept =
        doPost(
            "/api/v1/auth/invitations/accept",
            objectMapper
                .createObjectNode()
                .put("token", token)
                .put("firstName", "Owner")
                .put("lastName", "Test")
                .put("password", "owner-pass-123")
                .toString(),
            null,
            201);
    return accept.get("accessToken").asText();
  }

  private String anyOfficeRoleId(String adminToken) throws Exception {
    JsonNode roles = doGet("/api/v1/admin/roles", adminToken, 200);
    return roleId(roles, "SEGRETARIA_BASE");
  }

  private String login(String email, String password) throws Exception {
    JsonNode resp =
        doPost(
            "/api/v1/auth/login",
            objectMapper
                .createObjectNode()
                .put("email", email)
                .put("password", password)
                .toString(),
            null,
            200);
    return resp.get("accessToken").asText();
  }

  private java.util.List<String> rolesOf(JsonNode me) {
    java.util.List<String> roles = new java.util.ArrayList<>();
    me.get("roles").forEach(r -> roles.add(r.asText()));
    return roles;
  }

  private java.util.List<String> permissionsOf(JsonNode me) {
    java.util.List<String> permissions = new java.util.ArrayList<>();
    me.get("permissions").forEach(p -> permissions.add(p.asText()));
    return permissions;
  }

  private String roleId(JsonNode roles, String code) {
    for (JsonNode role : roles) {
      if (code.equals(role.get("code").asText())) {
        return role.get("id").asText();
      }
    }
    throw new AssertionError("Role not found: " + code);
  }

  private JsonNode doGet(String url, String token, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder request = get(url);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return doPerform(request, expectedStatus);
  }

  private JsonNode doPost(String url, String body, String token, int expectedStatus)
      throws Exception {
    MockHttpServletRequestBuilder request = post(url).contentType(APPLICATION_JSON);
    if (body != null) {
      request.content(body);
    }
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return doPerform(request, expectedStatus);
  }

  private JsonNode doPatch(String url, String body, String token, int expectedStatus)
      throws Exception {
    MockHttpServletRequestBuilder request = patch(url).contentType(APPLICATION_JSON).content(body);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return doPerform(request, expectedStatus);
  }

  private JsonNode doDelete(String url, String token, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder request = delete(url);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return doPerform(request, expectedStatus);
  }

  private JsonNode doPerform(MockHttpServletRequestBuilder request, int expectedStatus)
      throws Exception {
    var result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
    String content = result.getResponse().getContentAsString();
    if (content == null || content.isBlank()) {
      return null;
    }
    return objectMapper.readTree(content);
  }
}
