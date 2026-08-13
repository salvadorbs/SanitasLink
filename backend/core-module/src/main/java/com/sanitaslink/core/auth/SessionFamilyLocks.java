package com.sanitaslink.core.auth;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes refresh-token rotation and session-family revocation through a transaction-scoped
 * PostgreSQL advisory lock keyed on the session family id. Without it, a revocation sweeping the
 * family could interleave with a concurrent rotation and miss the freshly inserted successor,
 * leaving one valid token in an otherwise-revoked family.
 *
 * <p>The lock is transaction-scoped ({@code pg_advisory_xact_lock}): it is released automatically
 * when the surrounding transaction commits or rolls back, so it can never leak across pooled
 * connections.
 */
@Component
public class SessionFamilyLocks {

  private static final String LOCK_FAMILY = "SELECT pg_advisory_xact_lock(hashtext(?)::bigint)";

  private final JdbcTemplate jdbcTemplate;

  public SessionFamilyLocks(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Acquires the advisory lock for the family, blocking until it is available. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockFamily(UUID sessionFamilyId) {
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(LOCK_FAMILY)) {
                statement.setString(1, sessionFamilyId.toString());
                statement.execute();
              }
              return null;
            });
  }
}
