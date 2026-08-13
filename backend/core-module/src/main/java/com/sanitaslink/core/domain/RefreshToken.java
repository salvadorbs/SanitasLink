package com.sanitaslink.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A rotated, revocable refresh token. Only the SHA-256 hash of the raw token is stored. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by_token_hash", length = 64)
  private String replacedByTokenHash;

  @Column(name = "session_family_id", nullable = false)
  private UUID sessionFamilyId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static RefreshToken create(
      UUID id, UUID userId, String tokenHash, Instant expiresAt, UUID sessionFamilyId) {
    RefreshToken token = new RefreshToken();
    token.id = id;
    token.userId = userId;
    token.tokenHash = tokenHash;
    token.expiresAt = expiresAt;
    token.sessionFamilyId = sessionFamilyId;
    token.createdAt = Instant.now();
    return token;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public String getReplacedByTokenHash() {
    return replacedByTokenHash;
  }

  public void setReplacedByTokenHash(String replacedByTokenHash) {
    this.replacedByTokenHash = replacedByTokenHash;
  }

  public UUID getSessionFamilyId() {
    return sessionFamilyId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
