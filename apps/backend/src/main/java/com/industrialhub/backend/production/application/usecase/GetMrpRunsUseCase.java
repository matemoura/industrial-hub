package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.MrpRunResponse;
import com.industrialhub.backend.production.infrastructure.MrpRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class GetMrpRunsUseCase {

    private final MrpRunRepository mrpRunRepository;

    public GetMrpRunsUseCase(MrpRunRepository mrpRunRepository) {
        this.mrpRunRepository = mrpRunRepository;
    }

    public Page<MrpRunResponse> execute(Pageable pageable) {
        return mrpRunRepository.findRealRunsPageable(pageable)
                .map(MrpRunResponse::from);
    }
}
