package com.sanitaslink.core.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the RLS context GUCs to the current JDBC connection using {@code set_config} with {@code
 * is_local = true}, i.e. exact {@code SET LOCAL} semantics: the context is scoped to the current
 * transaction and can never leak across pooled connections.
 */
@Component
public class TenantContextManager {

  private static final String SET_OFFICE = "SELECT set_config('app.current_office_id', ?, true)";
  private static final String SET_USER = "SELECT set_config('app.current_user_id', ?, true)";
  private static final String SET_ADMIN = "SELECT set_config('app.is_admin', ?, true)";
  private static final String SET_TOKEN = "SELECT set_config('app.current_token_hash', ?, true)";

  private final JdbcTemplate jdbcTemplate;

  public TenantContextManager(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Applies the current request context from {@link TenantContextHolder}. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void initialize() {
    TenantContext context = TenantContextHolder.get();
    if (context == null) {
      apply(null, null, false);
    } else {
      apply(context.officeId(), context.userId(), context.admin());
    }
  }

  /** Applies an explicit context (e.g. during invitation acceptance or token flows). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void initialize(UUID officeId, UUID userId, boolean admin) {
    apply(officeId, userId, admin);
  }

  /**
   * Applies a context that additionally carries the invitation token hash, enabling the
   * unauthenticated invitation/token lookups under RLS. Office, user and admin GUCs are cleared.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void initializeWithToken(String tokenHash) {
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              set(connection, SET_OFFICE, "");
              set(connection, SET_USER, "");
              set(connection, SET_ADMIN, "false");
              set(connection, SET_TOKEN, tokenHash);
              return null;
            });
  }

  private void apply(UUID officeId, UUID userId, boolean admin) {
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              set(connection, SET_OFFICE, officeId != null ? officeId.toString() : "");
              set(connection, SET_USER, userId != null ? userId.toString() : "");
              set(connection, SET_ADMIN, admin ? "true" : "false");
              // Always clear a stale bearer-token context left by a previous flow in this tx.
              set(connection, SET_TOKEN, "");
              return null;
            });
  }

  private void set(Connection connection, String sql, String value) throws java.sql.SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, value);
      statement.execute();
    }
  }
}
