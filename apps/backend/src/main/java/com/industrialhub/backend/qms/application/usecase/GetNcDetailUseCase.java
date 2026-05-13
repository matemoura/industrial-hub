package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetNcDetailUseCase {

    private final NonConformanceRepository repository;

    public GetNcDetailUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NcResponse execute(UUID id) {
        return repository.findById(id)
                .map(NcResponse::from)
                .orElseThrow(() -> new NcNotFoundException(id));
    }
}
