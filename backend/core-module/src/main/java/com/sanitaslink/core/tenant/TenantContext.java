package com.sanitaslink.core.tenant;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated tenant context for the current request thread. Populated by the security filter
 * from fresh database state and consumed by the transaction layer to set RLS GUCs.
 */
public record TenantContext(
    UUID userId,
    String email,
    UUID officeId,
    boolean admin,
    List<String> roles,
    Set<String> permissions) {

  public static TenantContext of(
      UUID userId,
      String email,
      UUID officeId,
      boolean admin,
      List<String> roles,
      Set<String> permissions) {
    return new TenantContext(
        userId, email, officeId, admin, List.copyOf(roles), Set.copyOf(permissions));
  }
}
