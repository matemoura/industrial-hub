package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.PlantContext;
import com.industrialhub.backend.qms.application.dto.NcSummaryItem;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetNcListUseCase {

    private final NonConformanceRepository repository;

    public GetNcListUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<NcSummaryItem> execute(NcStatus status, NcSeverity severity, NcType type, Pageable pageable) {
        return execute(status, severity, type, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NcSummaryItem> execute(NcStatus status, NcSeverity severity, NcType type,
                                       Boolean slaBreached, Pageable pageable) {
        boolean isAdmin = PlantContext.isAdminContext();
        List<UUID> plantIds = PlantContext.current();

        if (!isAdmin && plantIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        if (isAdmin) {
            return repository.findAllFiltered(status, severity, type, slaBreached, pageable)
                    .map(NcSummaryItem::from);
        } else {
            return repository.findAllFilteredByPlantIds(status, severity, type, slaBreached, plantIds, pageable)
                    .map(NcSummaryItem::from);
        }
    }
}
