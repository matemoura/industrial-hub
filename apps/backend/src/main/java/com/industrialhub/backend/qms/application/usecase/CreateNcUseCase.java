package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.Supplier;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.domain.SupplierRequiredForNcException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CreateNcUseCase {

    private final NonConformanceRepository repository;
    private final SupplierRepository supplierRepository;
    private final QmsEmailService emailService;
    private final AuditService auditService;

    public CreateNcUseCase(NonConformanceRepository repository,
                           SupplierRepository supplierRepository,
                           QmsEmailService emailService,
                           AuditService auditService) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    @Transactional
    public NcResponse execute(CreateNcRequest request, String reportedBy) {
        Supplier supplier = null;
        if (request.type() == NcType.SUPPLIER) {
            if (request.supplierId() == null) {
                throw new SupplierRequiredForNcException();
            }
            supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new SupplierNotFoundException(request.supplierId()));
        }

        NonConformance nc = NonConformance.builder()
                .title(request.title())
                .description(request.description())
                .type(request.type())
                .severity(request.severity())
                .status(NcStatus.OPEN)
                .reportedBy(reportedBy)
                .reportedAt(LocalDateTime.now())
                .supplier(supplier)
                .build();

        NonConformance saved = repository.save(nc);

        if (saved.getSeverity() == NcSeverity.CRITICAL) {
            emailService.notifyCriticalNc(saved);
        }

        auditService.log(reportedBy, AuditAction.NC_CREATED, "NonConformance", saved.getId(),
                Map.of("title", saved.getTitle(), "severity", saved.getSeverity().name()));

        return NcResponse.from(saved);
    }
}
