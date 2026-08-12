package com.sanitaslink.core.office;

import com.sanitaslink.core.audit.AuditActionType;
import com.sanitaslink.core.audit.AuditService;
import com.sanitaslink.core.domain.Office;
import com.sanitaslink.core.exception.ApiException;
import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.office.dto.OfficeResponse;
import com.sanitaslink.core.office.dto.UpdateOfficeRequest;
import com.sanitaslink.core.repository.OfficeRepository;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Member-facing office information and configuration. */
@Service
public class OfficeService {

  private final OfficeRepository officeRepository;
  private final OfficeGuard officeGuard;
  private final TenantContextManager tenantContextManager;
  private final AuditService auditService;

  public OfficeService(
      OfficeRepository officeRepository,
      OfficeGuard officeGuard,
      TenantContextManager tenantContextManager,
      AuditService auditService) {
    this.officeRepository = officeRepository;
    this.officeGuard = officeGuard;
    this.tenantContextManager = tenantContextManager;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public OfficeResponse getOffice(UUID officeId) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Office office =
        officeRepository
            .findById(officeId)
            .orElseThrow(
                () -> ApiException.notFound(ErrorCodes.OFFICE_NOT_FOUND, "Office not found"));
    return toResponse(office);
  }

  @Transactional
  public OfficeResponse updateOffice(UUID officeId, UpdateOfficeRequest request) {
    tenantContextManager.initialize();
    officeGuard.requireOfficeAccess(officeId);
    Office office =
        officeRepository
            .findById(officeId)
            .orElseThrow(
                () -> ApiException.notFound(ErrorCodes.OFFICE_NOT_FOUND, "Office not found"));
    if (request.name() != null) {
      office.setName(request.name());
    }
    if (request.legalName() != null) {
      office.setLegalName(request.legalName());
    }
    if (request.taxIdentifier() != null) {
      office.setTaxIdentifier(request.taxIdentifier());
    }
    if (request.email() != null) {
      office.setEmail(request.email());
    }
    if (request.phone() != null) {
      office.setPhone(request.phone());
    }
    if (request.address() != null) {
      office.setAddress(request.address());
    }
    officeRepository.save(office);
    auditService.record(AuditActionType.OFFICE_UPDATED, "OFFICE", officeId.toString(), null);
    return toResponse(office);
  }

  private OfficeResponse toResponse(Office office) {
    return new OfficeResponse(
        office.getId(),
        office.getName(),
        office.getLegalName(),
        office.getTaxIdentifier(),
        office.getEmail(),
        office.getPhone(),
        office.getAddress(),
        office.getStatus(),
        office.getCreatedAt(),
        office.getUpdatedAt());
  }
}
