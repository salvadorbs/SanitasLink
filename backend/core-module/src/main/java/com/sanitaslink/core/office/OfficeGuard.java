package com.sanitaslink.core.office;

import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.tenant.TenantContext;
import com.sanitaslink.core.tenant.TenantContextHolder;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Guards office-scoped operations: a global admin may access any office; any other authenticated
 * user must be a member of the requested office.
 */
@Component
public class OfficeGuard {

  public TenantContext requireOfficeAccess(UUID officeId) {
    TenantContext context = TenantContextHolder.require();
    if (context.admin()) {
      return context;
    }
    if (context.officeId() == null || !context.officeId().equals(officeId)) {
      throw ApiException.forbidden(
          ErrorCodes.OFFICE_MISMATCH, "Access to this office is not allowed");
    }
    return context;
  }
}
