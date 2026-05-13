package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.NcSummaryItem;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetNcListUseCase {

    private final NonConformanceRepository repository;

    public GetNcListUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<NcSummaryItem> execute(NcStatus status, NcSeverity severity, NcType type, Pageable pageable) {
        return repository.findAllFiltered(status, severity, type, pageable)
                .map(NcSummaryItem::from);
    }
}
