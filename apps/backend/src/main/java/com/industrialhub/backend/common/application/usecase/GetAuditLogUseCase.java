package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.AuditLogResponse;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAuditLogUseCase {

    private final AuditLogRepository repository;

    public GetAuditLogUseCase(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> execute(String entityType, AuditAction action,
                                          String username, Pageable pageable) {
        return repository.findWithFilters(entityType, action, username, pageable)
                .map(AuditLogResponse::from);
    }
}
