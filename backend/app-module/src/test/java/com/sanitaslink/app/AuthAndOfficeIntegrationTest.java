package com.sanitaslink.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanitaslink.core.auth.AuthService;
import com.sanitaslink.core.auth.dto.AcceptInvitationRequest;
import com.sanitaslink.core.auth.dto.ConfirmPasswordResetRequest;
import com.sanitaslink.core.auth.dto.LoginRequest;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
  @Autowired AuthService authService;
  @Autowired JwtEncoder jwtEncoder;
  @Autowired com.sanitaslink.prescription.PrescriptionService prescriptionService;

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
            201);
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
        201);
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

    // Refresh rotates the refresh token; logout revokes it. The raw refresh token only ever
    // travels in the HttpOnly cookie and is never part of the JSON bodies.
    String setCookie1 = loginAndTakeSetCookie(adminEmail, "admin-pass-123");
    assertCookieAttributes(setCookie1);
    String cookie1 = setCookieValue(setCookie1);

    MvcResult refreshResult = postWithCookie("/api/v1/auth/refresh", cookie1, 200);
    MockHttpServletResponse refreshResponse = refreshResult.getResponse();
    JsonNode refreshed = objectMapper.readTree(refreshResponse.getContentAsString());
    assertThat(refreshed.has("accessToken")).isTrue();
    assertThat(refreshed.has("refreshToken")).isFalse();
    String setCookie2 = requireSetCookie(refreshResponse);
    assertCookieAttributes(setCookie2);
    String cookie2 = setCookieValue(setCookie2);
    assertThat(cookie2).isNotEqualTo(cookie1);

    // Reusing the rotated token is rejected.
    postWithCookie("/api/v1/auth/refresh", cookie1, 401);

    // Logout revokes the current cookie and expires it.
    MvcResult logoutResult = postWithCookie("/api/v1/auth/logout", cookie2, 204);
    assertThat(requireSetCookie(logoutResult.getResponse())).contains("Max-Age=0");
    postWithCookie("/api/v1/auth/refresh", cookie2, 401);
  }

  @Test
  void cookieEndpointsRejectDisallowedOrigins() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");

    // Disallowed origins are rejected on every cookie-bearing endpoint. Without an Origin header
    // (non-browser clients) the cookie endpoints stay usable.
    MvcResult rejectedLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .header("Origin", "https://evil.example")
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper
                            .createObjectNode()
                            .put("email", adminEmail)
                            .put("password", "admin-pass-123")
                            .toString()))
            .andExpect(status().isForbidden())
            .andReturn();
    assertThat(rejectedLogin.getResponse().getContentAsString()).contains("Invalid CORS request");

    String cookie = setCookieValue(loginAndTakeSetCookie(adminEmail, "admin-pass-123"));
    String token = cookie.substring(cookie.indexOf('=') + 1);

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header("Origin", "https://evil.example")
                .cookie(new org.springframework.mock.web.MockCookie("sl_refresh", token))
                .header("Cookie", cookie))
        .andExpect(status().isForbidden());

    // The configured frontend origin is accepted (defense in depth next to SameSite).
    MvcResult allowed =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .header("Origin", "http://localhost:5173")
                    .cookie(new org.springframework.mock.web.MockCookie("sl_refresh", token))
                    .header("Cookie", cookie))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(requireSetCookie(allowed.getResponse())).contains("SameSite=Strict");

    // Credentialed preflight from the allowed origin passes and echoes the origin.
    mockMvc
        .perform(
            options("/api/v1/auth/refresh")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Correlation-Id"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

    // Missing Origin (curl, API clients, same-site navigations) is accepted.
    MvcResult noOrigin =
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .cookie(new org.springframework.mock.web.MockCookie("sl_refresh", token))
                    .header("Cookie", cookie))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(requireSetCookie(noOrigin.getResponse())).contains("Max-Age=0");
  }

  @Test
  void replayOfRotatedRefreshTokenRevokesTheSessionFamily() throws Exception {
    String adminEmail = uniqueEmail("admin");
    UUID userId = seeder.createAdmin(adminEmail, "admin-pass-123");
    String cookie1 = setCookieValue(loginAndTakeSetCookie(adminEmail, "admin-pass-123"));
    String cookie2 = setCookieValue(requireSetCookie(refreshWith(cookie1, 200).getResponse()));

    // One login, one rotation: exactly one active token, one revoked predecessor linked to its
    // replacement, all within a single session family. No orphan tokens are created.
    assertTokenState(userId, 2, 1, 1, 1);

    // Replaying the already-rotated first cookie is detected as token reuse.
    postWithCookie("/api/v1/auth/refresh", cookie1, 401);

    // The whole session family is revoked, so the valid second cookie is also dead, and the
    // reuse was audited.
    assertTokenState(userId, 2, 0, 2, 1);
    postWithCookie("/api/v1/auth/refresh", cookie2, 401);
    assertThat(auditEventCount("TOKEN_REUSE")).isGreaterThan(0);
  }

  @Test
  void refreshRotatesWithinOneSessionFamilyOnly() throws Exception {
    String adminEmail = uniqueEmail("admin");
    UUID userId = seeder.createAdmin(adminEmail, "admin-pass-123");
    String cookie1 = setCookieValue(loginAndTakeSetCookie(adminEmail, "admin-pass-123"));
    assertTokenState(userId, 1, 1, 0, 1);

    String cookie2 = setCookieValue(requireSetCookie(refreshWith(cookie1, 200).getResponse()));
    assertTokenState(userId, 2, 1, 1, 1);

    String cookie3 = setCookieValue(requireSetCookie(refreshWith(cookie2, 200).getResponse()));
    assertTokenState(userId, 3, 1, 2, 1);

    // The current token keeps rotating within the same family, and every rotated predecessor
    // stays revoked and linked to its successor.
    String cookie4 = setCookieValue(requireSetCookie(refreshWith(cookie3, 200).getResponse()));
    assertTokenState(userId, 4, 1, 3, 1);

    // Replaying any already-rotated token revokes the whole family, so the current token dies too.
    postWithCookie("/api/v1/auth/refresh", cookie1, 401);
    postWithCookie("/api/v1/auth/refresh", cookie2, 401);
    postWithCookie("/api/v1/auth/refresh", cookie3, 401);
    postWithCookie("/api/v1/auth/refresh", cookie4, 401);
    assertTokenState(userId, 4, 0, 4, 1);
  }

  @Test
  void replayDoesNotRevokeIndependentSessions() throws Exception {
    String adminEmail = uniqueEmail("admin");
    UUID userId = seeder.createAdmin(adminEmail, "admin-pass-123");

    // Two independent logins (e.g. two devices) create two distinct session families.
    String deviceA = setCookieValue(loginAndTakeSetCookie(adminEmail, "admin-pass-123"));
    String deviceB = setCookieValue(loginAndTakeSetCookie(adminEmail, "admin-pass-123"));
    assertTokenState(userId, 2, 2, 0, 2);

    String deviceARotated =
        setCookieValue(requireSetCookie(refreshWith(deviceA, 200).getResponse()));

    // Replay on device A revokes only A's family.
    postWithCookie("/api/v1/auth/refresh", deviceA, 401);
    assertTokenState(userId, 3, 1, 2, 2);
    postWithCookie("/api/v1/auth/refresh", deviceARotated, 401);

    // Device B is untouched and still rotates normally.
    String deviceBRotated =
        setCookieValue(requireSetCookie(refreshWith(deviceB, 200).getResponse()));
    assertTokenState(userId, 4, 1, 3, 2);
    refreshWith(deviceBRotated, 200);
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
        201);
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

  @Test
  void clinicalWorkflowsAndEncryptionAtRest() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Workflows", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    // Create a patient with a tax identifier and clinical notes.
    String patientId =
        doPost(
                "/api/v1/offices/" + officeId + "/patients",
                objectMapper
                    .createObjectNode()
                    .put("firstName", "Anna")
                    .put("lastName", "Verdi")
                    .put("taxIdentifier", "VRDANA85A41H501Z")
                    .toString(),
                ownerToken,
                201)
            .get("id")
            .asText();
    doPatch(
        "/api/v1/offices/" + officeId + "/patients/" + patientId + "/clinical",
        objectMapper
            .createObjectNode()
            .put("clinicalNotes", "allergie alla penicillina")
            .toString(),
        ownerToken,
        200);

    // Appointment lifecycle.
    String appointmentId =
        doPost(
                "/api/v1/offices/" + officeId + "/appointments",
                objectMapper
                    .createObjectNode()
                    .put("title", "Visita di controllo")
                    .put("patientId", patientId)
                    .put("startsAt", "2030-01-10T09:00:00Z")
                    .put("endsAt", "2030-01-10T09:30:00Z")
                    .toString(),
                ownerToken,
                201)
            .get("id")
            .asText();
    doGet("/api/v1/offices/" + officeId + "/appointments", ownerToken, 200);
    doDelete("/api/v1/offices/" + officeId + "/appointments/" + appointmentId, ownerToken, 204);

    // Prescription lifecycle: request -> issue -> print.
    String prescriptionId =
        doPost(
                "/api/v1/offices/" + officeId + "/prescriptions",
                objectMapper
                    .createObjectNode()
                    .put("medication", "Amoxicillina 500mg")
                    .put("patientId", patientId)
                    .toString(),
                ownerToken,
                201)
            .get("id")
            .asText();
    doPatch(
        "/api/v1/offices/" + officeId + "/prescriptions/" + prescriptionId + "/issue",
        ownerToken,
        200);
    doPatch(
        "/api/v1/offices/" + officeId + "/prescriptions/" + prescriptionId + "/print",
        ownerToken,
        200);

    // A base secretary cannot issue prescriptions but may create requests.
    JsonNode roles = doGet("/api/v1/admin/roles", adminToken, 200);
    String baseSecretaryRoleId = roleId(roles, "SEGRETARIA_BASE");
    String secretaryEmail = uniqueEmail("secretary");
    doPost(
        "/api/v1/offices/" + officeId + "/invitations",
        objectMapper
            .createObjectNode()
            .put("email", secretaryEmail)
            .put("roleId", baseSecretaryRoleId)
            .toString(),
        ownerToken,
        201);
    String secretaryInvitation = notificationPort.takeInvitationToken();
    JsonNode secretaryAccept =
        doPost(
            "/api/v1/auth/invitations/accept",
            objectMapper
                .createObjectNode()
                .put("token", secretaryInvitation)
                .put("firstName", "Sec")
                .put("lastName", "Two")
                .put("password", "sec-pass-123")
                .toString(),
            null,
            201);
    String secretaryToken = secretaryAccept.get("accessToken").asText();
    doPatch(
        "/api/v1/offices/" + officeId + "/prescriptions/" + prescriptionId + "/issue",
        secretaryToken,
        403);

    // Sensitive fields are encrypted at rest: the stored value differs from the plaintext.
    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.current_office_id', ?, true)")) {
        st.setString(1, officeId);
        st.execute();
      }
      try (PreparedStatement st =
          connection.prepareStatement(
              "SELECT tax_identifier, clinical_notes FROM patients WHERE id = ?::uuid")) {
        st.setString(1, patientId);
        try (ResultSet rs = st.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getString(1)).isNotEqualTo("VRDANA85A41H501Z").isNotBlank();
          assertThat(rs.getString(2)).isNotEqualTo("allergie alla penicillina").isNotBlank();
        }
      }
      connection.rollback();
    }
  }

  @Test
  void compositeForeignKeyPreventsCrossOfficeReferences() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");

    String officeA = createOffice(adminToken, "Studio A", uniqueEmail("alpha"));
    String ownerAToken = acceptAndLogin();
    String officeB = createOffice(adminToken, "Studio B", uniqueEmail("beta"));
    String ownerBToken = acceptAndLogin();

    String patientA =
        doPost(
                "/api/v1/offices/" + officeA + "/patients",
                objectMapper
                    .createObjectNode()
                    .put("firstName", "A")
                    .put("lastName", "Patient")
                    .toString(),
                ownerAToken,
                201)
            .get("id")
            .asText();

    // Application layer: office B cannot reference office A's patient.
    doPost(
        "/api/v1/offices/" + officeB + "/appointments",
        objectMapper
            .createObjectNode()
            .put("title", "Cross-office")
            .put("patientId", patientA)
            .put("startsAt", "2030-01-10T09:00:00Z")
            .put("endsAt", "2030-01-10T09:30:00Z")
            .toString(),
        ownerBToken,
        404);
    doPost(
        "/api/v1/offices/" + officeB + "/prescriptions",
        objectMapper
            .createObjectNode()
            .put("medication", "Drug")
            .put("patientId", patientA)
            .toString(),
        ownerBToken,
        404);

    // Database layer: the composite FK rejects a cross-office insert even under an admin context.
    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.is_admin', 'true', true)")) {
        st.execute();
      }
      try (PreparedStatement st =
          connection.prepareStatement(
              "INSERT INTO appointments (id, office_id, patient_id, title, starts_at, ends_at, status) "
                  + "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'x', now(), now() + interval '30 minutes', 'SCHEDULED')")) {
        st.setString(1, officeB);
        st.setString(2, patientA);
        assertThatThrownBy(st::executeUpdate).isInstanceOf(java.sql.SQLException.class);
      }
      connection.rollback();
    }
  }

  @Test
  void clinicalListReadsAreAudited() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Audit", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    doGet("/api/v1/offices/" + officeId + "/patients", ownerToken, 200);
    doGet("/api/v1/offices/" + officeId + "/appointments", ownerToken, 200);
    doGet("/api/v1/offices/" + officeId + "/prescriptions", ownerToken, 200);

    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.current_office_id', ?, true)")) {
        st.setString(1, officeId);
        st.execute();
      }
      for (String action : new String[] {"PATIENT_READ", "APPOINTMENT_READ", "PRESCRIPTION_READ"}) {
        try (PreparedStatement st =
            connection.prepareStatement(
                "SELECT count(*) FROM audit_events WHERE action_type = ? AND office_id = ?::uuid")) {
          st.setString(1, action);
          st.setString(2, officeId);
          try (ResultSet rs = st.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).as(action).isGreaterThan(0);
          }
        }
      }
      connection.rollback();
    }
  }

  @Test
  void prescriptionRequiresPatientAndNonBlankMedication() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Presc", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    // Missing patient.
    doPost(
        "/api/v1/offices/" + officeId + "/prescriptions",
        objectMapper.createObjectNode().put("medication", "Amoxicillina").toString(),
        ownerToken,
        400);
    // Blank medication.
    doPost(
        "/api/v1/offices/" + officeId + "/prescriptions",
        objectMapper
            .createObjectNode()
            .put("medication", "   ")
            .put("patientId", UUID.randomUUID().toString())
            .toString(),
        ownerToken,
        400);
  }

  @Test
  void appointmentTransitionsAndRescheduleAreEnforced() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Agenda", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    String appointmentId =
        doPost(
                "/api/v1/offices/" + officeId + "/appointments",
                objectMapper
                    .createObjectNode()
                    .put("title", "Visita")
                    .put("startsAt", "2030-02-01T09:00:00Z")
                    .put("endsAt", "2030-02-01T09:30:00Z")
                    .toString(),
                ownerToken,
                201)
            .get("id")
            .asText();

    // Reschedule is allowed while scheduled/confirmed.
    doPatch(
        "/api/v1/offices/" + officeId + "/appointments/" + appointmentId,
        objectMapper.createObjectNode().put("title", "Visita di controllo").toString(),
        ownerToken,
        200);
    // Valid transition.
    doPatch(
        "/api/v1/offices/" + officeId + "/appointments/" + appointmentId + "/status",
        objectMapper.createObjectNode().put("status", "CONFIRMED").toString(),
        ownerToken,
        200);
    // Invalid transition from CONFIRMED to SCHEDULED.
    doPatch(
        "/api/v1/offices/" + officeId + "/appointments/" + appointmentId + "/status",
        objectMapper.createObjectNode().put("status", "SCHEDULED").toString(),
        ownerToken,
        409);
    // Terminal state rejects further transitions.
    doPatch(
        "/api/v1/offices/" + officeId + "/appointments/" + appointmentId + "/status",
        objectMapper.createObjectNode().put("status", "CANCELLED").toString(),
        ownerToken,
        200);
    doPatch(
        "/api/v1/offices/" + officeId + "/appointments/" + appointmentId + "/status",
        objectMapper.createObjectNode().put("status", "COMPLETED").toString(),
        ownerToken,
        409);
  }

  @Test
  void concurrentPrescriptionIssueIsAtomic() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Race Rx", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    String patientId =
        doPost(
                "/api/v1/offices/" + officeId + "/patients",
                objectMapper
                    .createObjectNode()
                    .put("firstName", "P")
                    .put("lastName", "Rx")
                    .toString(),
                ownerToken,
                201)
            .get("id")
            .asText();
    String prescriptionId =
        doPost(
                "/api/v1/offices/" + officeId + "/prescriptions",
                objectMapper
                    .createObjectNode()
                    .put("medication", "Farmaco")
                    .put("patientId", patientId)
                    .toString(),
                ownerToken,
                201)
            .get("id")
            .asText();

    UUID ownerUserId = UUID.fromString(ownerId(ownerToken));
    int success =
        runConcurrently(
            () -> {
              TenantContextHolder.set(
                  TenantContext.of(
                      ownerUserId,
                      "owner@example.it",
                      UUID.fromString(officeId),
                      false,
                      List.of(),
                      Set.of()));
              try {
                prescriptionService.issue(
                    UUID.fromString(officeId), UUID.fromString(prescriptionId));
              } finally {
                TenantContextHolder.clear();
              }
            });
    assertThat(success).isEqualTo(1);
  }

  private String ownerId(String ownerToken) throws Exception {
    return doGet("/api/v1/auth/me", ownerToken, 200).get("id").asText();
  }

  @Test
  void rejectsTokenWithWrongIssuer() throws Exception {
    String email = uniqueEmail("admin");
    UUID adminId = seeder.createAdmin(email, "admin-pass-123");
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("evil-issuer")
            .subject(adminId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("email", email)
            .claim("sv", 0)
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    doGet("/api/v1/auth/me", token, 401);
  }

  @Test
  void platformRoleCannotBeRevokedThroughOffice() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");

    String officeId = createOffice(adminToken, "Studio Platform", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    JsonNode roles = doGet("/api/v1/admin/roles", adminToken, 200);
    String adminRoleId = roleId(roles, "ADMIN");

    // Revoking a PLATFORM role through office management must be rejected.
    doDelete(
        "/api/v1/offices/" + officeId + "/members/" + ownerId(ownerToken) + "/roles/" + adminRoleId,
        ownerToken,
        409);
    // Assigning a PLATFORM role through office management must be rejected.
    doPatch(
        "/api/v1/offices/" + officeId + "/members/" + ownerId(ownerToken) + "/role",
        objectMapper.createObjectNode().put("roleId", adminRoleId).toString(),
        ownerToken,
        409);
  }

  @Test
  void passwordChangeInvalidatesExistingAccessTokens() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String token1 = login(adminEmail, "admin-pass-123");

    doPost(
        "/api/v1/auth/password/change",
        objectMapper
            .createObjectNode()
            .put("currentPassword", "admin-pass-123")
            .put("newPassword", "new-admin-pass-456")
            .toString(),
        token1,
        204);

    // The pre-change access token must no longer be accepted.
    doGet("/api/v1/auth/me", token1, 403);

    // The new password produces a valid token.
    String token2 = login(adminEmail, "new-admin-pass-456");
    doGet("/api/v1/auth/me", token2, 200);
  }

  @Test
  void updateOfficeRejectsInvalidEmail() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Mail", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    doPatch(
        "/api/v1/offices/" + officeId,
        objectMapper.createObjectNode().put("email", "not-an-email").toString(),
        ownerToken,
        400);
  }

  @Test
  void rlsRestrictsRuntimeRole() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    createOffice(adminToken, "Studio A", uniqueEmail("alpha"));
    acceptAndLogin();
    createOffice(adminToken, "Studio B", uniqueEmail("beta"));
    acceptAndLogin();

    // A fresh JDBC connection as app_user (the restricted runtime role) proves RLS is active.
    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      // Not the owner: RLS applies and, with no context, nothing is visible.
      try (PreparedStatement st = connection.prepareStatement("SELECT count(*) FROM offices");
          ResultSet rs = st.executeQuery()) {
        rs.next();
        assertThat(rs.getInt(1)).isZero();
      }
      // With the admin context (SET LOCAL semantics) the rows become visible.
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.is_admin', 'true', true)")) {
        st.execute();
      }
      try (PreparedStatement st = connection.prepareStatement("SELECT count(*) FROM offices");
          ResultSet rs = st.executeQuery()) {
        rs.next();
        assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(2);
      }
      connection.rollback();
    }
  }

  @Test
  void auditEventsRecordRequestMetadata() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    login(adminEmail, "admin-pass-123");

    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.is_admin', 'true', true)")) {
        st.execute();
      }
      try (PreparedStatement st =
              connection.prepareStatement(
                  "SELECT ip_address FROM audit_events WHERE action_type = 'LOGIN' ORDER BY occurred_at DESC LIMIT 1");
          ResultSet rs = st.executeQuery()) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isNotBlank();
      }
      connection.rollback();
    }
  }

  @Test
  void refreshTokenRotationIsRaceSafe() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    AuthService.TokenPair first =
        authService.login(new LoginRequest(adminEmail, "admin-pass-123"), "127.0.0.1");
    String refresh = first.rawRefreshToken();

    int success = runConcurrently(() -> authService.refresh(refresh, "127.0.0.1"));
    assertThat(success).isEqualTo(1);
  }

  @Test
  void concurrentRefreshLoserRevokesTheFamilyOnTakeover() throws Exception {
    String adminEmail = uniqueEmail("admin");
    UUID userId = seeder.createAdmin(adminEmail, "admin-pass-123");
    AuthService.TokenPair first =
        authService.login(new LoginRequest(adminEmail, "admin-pass-123"), "127.0.0.1");
    String refresh = first.rawRefreshToken();

    // Two concurrent rotations of the same raw credential: one wins, the loser detected that
    // its credential was taken over by the winner's rotation.
    int success = runConcurrently(() -> authService.refresh(refresh, "127.0.0.1"));
    assertThat(success).isEqualTo(1);

    // The taken-over clone is treated as replay: the whole session family is revoked, so no
    // rotated successor remains usable, and the reuse was audited.
    assertTokenState(userId, 2, 0, 2, 1);
    assertThat(auditEventCount("TOKEN_REUSE")).isGreaterThan(0);
  }

  @Test
  void expiredRotatedTokenReplayStillRevokesTheSessionFamily() throws Exception {
    String adminEmail = uniqueEmail("admin");
    UUID userId = seeder.createAdmin(adminEmail, "admin-pass-123");
    String cookie1 = setCookieValue(loginAndTakeSetCookie(adminEmail, "admin-pass-123"));
    String cookie2 = setCookieValue(requireSetCookie(refreshWith(cookie1, 200).getResponse()));
    assertTokenState(userId, 2, 1, 1, 1);

    // The already-rotated predecessor is expired in the database: expiry must never bypass the
    // replay detection, otherwise an attacker replaying an old stolen hash after its TTL has
    // passed would escape family revocation.
    expireOldestRefreshToken(userId);
    postWithCookie("/api/v1/auth/refresh", cookie1, 401);

    assertTokenState(userId, 2, 0, 2, 1);
    postWithCookie("/api/v1/auth/refresh", cookie2, 401);
    assertThat(auditEventCount("TOKEN_REUSE")).isGreaterThan(0);
  }

  @Test
  void invitationAcceptanceIsRaceSafe() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    createOffice(adminToken, "Studio Race", uniqueEmail("owner"));
    String invitation = notificationPort.takeInvitationToken();

    int success =
        runConcurrently(
            () ->
                authService.acceptInvitation(
                    new AcceptInvitationRequest(invitation, "R", "Race", "race-pass-123")));
    assertThat(success).isEqualTo(1);
  }

  @Test
  void passwordResetConfirmationIsRaceSafe() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String ownerEmail = uniqueEmail("owner");
    createOffice(adminToken, "Studio ResetRace", ownerEmail);
    String invitation = notificationPort.takeInvitationToken();
    authService.acceptInvitation(
        new AcceptInvitationRequest(invitation, "N", "O", "owner-pass-123"));
    authService.requestPasswordReset(
        new com.sanitaslink.core.auth.dto.RequestPasswordResetRequest(ownerEmail), "127.0.0.1");
    String resetToken = notificationPort.takeResetToken();

    int success =
        runConcurrently(
            () ->
                authService.confirmPasswordReset(
                    new ConfirmPasswordResetRequest(resetToken, "new-owner-pass-456")));
    assertThat(success).isEqualTo(1);
  }

  @Test
  void clinicalPermissionsAreEnforced() throws Exception {
    String adminEmail = uniqueEmail("admin");
    seeder.createAdmin(adminEmail, "admin-pass-123");
    String adminToken = login(adminEmail, "admin-pass-123");
    String officeId = createOffice(adminToken, "Studio Clinica", uniqueEmail("owner"));
    String ownerToken = acceptAndLogin();

    // Titular registers a patient.
    JsonNode created =
        doPost(
            "/api/v1/offices/" + officeId + "/patients",
            objectMapper
                .createObjectNode()
                .put("firstName", "Mario")
                .put("lastName", "Rossi")
                .put("taxIdentifier", "RSSMRA80A01H501Z")
                .toString(),
            ownerToken,
            201);
    String patientId = created.get("id").asText();

    // Titular writes and reads the clinical record.
    doPatch(
        "/api/v1/offices/" + officeId + "/patients/" + patientId + "/clinical",
        objectMapper.createObjectNode().put("clinicalNotes", "Ipertensione").toString(),
        ownerToken,
        200);
    JsonNode clinical =
        doGet(
            "/api/v1/offices/" + officeId + "/patients/" + patientId + "/clinical",
            ownerToken,
            200);
    assertThat(clinical.get("clinicalNotes").asText()).isEqualTo("Ipertensione");

    // Invite and activate a base secretary.
    JsonNode roles = doGet("/api/v1/admin/roles", adminToken, 200);
    String baseSecretaryRoleId = roleId(roles, "SEGRETARIA_BASE");
    String secretaryEmail = uniqueEmail("secretary");
    doPost(
        "/api/v1/offices/" + officeId + "/invitations",
        objectMapper
            .createObjectNode()
            .put("email", secretaryEmail)
            .put("roleId", baseSecretaryRoleId)
            .toString(),
        ownerToken,
        201);
    String secretaryInvitation = notificationPort.takeInvitationToken();
    JsonNode secretaryAccept =
        doPost(
            "/api/v1/auth/invitations/accept",
            objectMapper
                .createObjectNode()
                .put("token", secretaryInvitation)
                .put("firstName", "Sec")
                .put("lastName", "Ret")
                .put("password", "sec-pass-123")
                .toString(),
            null,
            201);
    String secretaryToken = secretaryAccept.get("accessToken").asText();

    // The secretary can access the registry but NOT the clinical record.
    doGet("/api/v1/offices/" + officeId + "/patients", secretaryToken, 200);
    doGet(
        "/api/v1/offices/" + officeId + "/patients/" + patientId + "/clinical",
        secretaryToken,
        403);
  }

  private int runConcurrently(Runnable action) throws Exception {
    int threads = 4;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                try {
                  start.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                try {
                  action.run();
                  success.incrementAndGet();
                } catch (Exception ignored) {
                  // Expected for all but the winning thread.
                }
              }));
    }
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    for (Future<?> future : futures) {
      try {
        future.get(20, TimeUnit.SECONDS);
      } catch (Exception ignored) {
        // Ignore any assertion/other failures inside the worker threads.
      }
    }
    pool.shutdownNow();
    return success.get();
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
            201);
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

  /** Logs in and returns the raw Set-Cookie header carrying the HttpOnly refresh cookie. */
  private String loginAndTakeSetCookie(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper
                            .createObjectNode()
                            .put("email", email)
                            .put("password", password)
                            .toString()))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpServletResponse response = result.getResponse();
    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.has("refreshToken")).isFalse();
    assertThat(body.get("accessToken").asText()).isNotBlank();
    return requireSetCookie(response);
  }

  private MvcResult postWithCookie(String url, String cookie, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder request = post(url);
    if (cookie != null) {
      int eq = cookie.indexOf('=');
      request
          .cookie(
              new org.springframework.mock.web.MockCookie(
                  cookie.substring(0, eq), cookie.substring(eq + 1)))
          .header("Cookie", cookie);
    }
    MvcResult result = mockMvc.perform(request).andReturn();
    if (result.getResponse().getStatus() != expectedStatus) {
      System.out.println(
          "["
              + url
              + "] got "
              + result.getResponse().getStatus()
              + ": "
              + result.getResponse().getContentAsString());
    }
    org.springframework.test.util.AssertionErrors.assertEquals(
        "Response status", expectedStatus, result.getResponse().getStatus());
    return result;
  }

  /** Refreshes with the given cookie and returns the raw response with its new Set-Cookie. */
  private MvcResult refreshWith(String cookie, int expectedStatus) throws Exception {
    return postWithCookie("/api/v1/auth/refresh", cookie, expectedStatus);
  }

  private String requireSetCookie(MockHttpServletResponse response) {
    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).isNotBlank();
    return setCookie;
  }

  /** Extracts the name=value pair from a Set-Cookie header, ready to be echoed back. */
  private String setCookieValue(String setCookieHeader) {
    return setCookieHeader.split(";")[0];
  }

  private void assertCookieAttributes(String setCookie) {
    assertThat(setCookie).startsWith("sl_refresh=");
    assertThat(setCookie).contains("Path=/api/v1/auth");
    assertThat(setCookie).contains("SameSite=Strict");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("Secure");
    assertThat(setCookie).contains("Max-Age=604800");
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

  private JsonNode doPatch(String url, String token, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder request = patch(url);
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

  /**
   * Inspects the refresh-token rows of a user through a real {@code app_user} connection with the
   * owning user's RLS context (the same context the application uses at runtime), verifying the
   * exact token/revocation/replacement/family counts.
   */
  private void assertTokenState(UUID userId, int total, int active, int revoked, int families)
      throws Exception {
    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
        st.setString(1, userId.toString());
        st.execute();
      }
      try (PreparedStatement st =
          connection.prepareStatement(
              "SELECT COUNT(*), COUNT(revoked_at), COUNT(DISTINCT session_family_id) "
                  + "FROM refresh_tokens WHERE user_id = ?::uuid")) {
        st.setString(1, userId.toString());
        try (ResultSet rs = st.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1)).as("total tokens").isEqualTo(total);
          assertThat(rs.getInt(1) - rs.getInt(2)).as("active tokens").isEqualTo(active);
          assertThat(rs.getInt(2)).as("revoked tokens").isEqualTo(revoked);
          assertThat(rs.getInt(3)).as("session families").isEqualTo(families);
        }
      }
      connection.rollback();
    }
  }

  /**
   * Expires the oldest refresh token of a user (the original login token) directly through an
   * {@code app_user} connection in the owning user's RLS context, simulating the passage of time.
   */
  private void expireOldestRefreshToken(UUID userId) throws Exception {
    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
        st.setString(1, userId.toString());
        st.execute();
      }
      try (PreparedStatement st =
          connection.prepareStatement(
              "UPDATE refresh_tokens SET expires_at = TIMESTAMPTZ '2020-01-01 00:00:00Z' "
                  + "WHERE user_id = ?::uuid AND expires_at = "
                  + "(SELECT MIN(expires_at) FROM refresh_tokens WHERE user_id = ?::uuid)")) {
        st.setString(1, userId.toString());
        st.setString(2, userId.toString());
        st.executeUpdate();
      }
      connection.commit();
    }
  }

  /**
   * Counts audit events through a real {@code app_user} connection in the admin RLS context: only
   * platform admins (or events scoped to the caller's office) are readable, per {@code ae_select}.
   */
  private int auditEventCount(String actionType) throws Exception {
    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_user")) {
      connection.setAutoCommit(false);
      try (PreparedStatement st =
          connection.prepareStatement("SELECT set_config('app.is_admin', 'true', true)")) {
        st.execute();
      }
      try (PreparedStatement st =
          connection.prepareStatement("SELECT COUNT(*) FROM audit_events WHERE action_type = ?")) {
        st.setString(1, actionType);
        try (ResultSet rs = st.executeQuery()) {
          assertThat(rs.next()).isTrue();
          return rs.getInt(1);
        }
      }
    }
  }
}
