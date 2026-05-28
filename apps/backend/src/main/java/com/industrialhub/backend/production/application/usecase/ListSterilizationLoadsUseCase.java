package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.SterilizationLoadResponse;
import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.SterilizationMethod;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ListSterilizationLoadsUseCase {

    private final SterilizationLoadRepository loadRepository;

    public ListSterilizationLoadsUseCase(SterilizationLoadRepository loadRepository) {
        this.loadRepository = loadRepository;
    }

    @Transactional(readOnly = true)
    public Page<SterilizationLoadResponse> execute(LoadStatus status, SterilizationMethod method,
                                                    LocalDate dateFrom, LocalDate dateTo,
                                                    Pageable pageable) {
        return loadRepository.findFiltered(status, method, dateFrom, dateTo, pageable)
                .map(SterilizationLoadResponse::from);
    }
}
