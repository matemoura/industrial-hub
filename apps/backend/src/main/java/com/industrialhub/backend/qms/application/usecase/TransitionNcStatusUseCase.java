package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.domain.InvalidNcTransitionException;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransitionNcStatusUseCase {

    private static final Map<NcStatus, List<NcStatus>> ALLOWED = Map.of(
        NcStatus.OPEN,        List.of(NcStatus.IN_ANALYSIS),
        NcStatus.IN_ANALYSIS, List.of(NcStatus.CLOSED, NcStatus.OPEN),
        NcStatus.CLOSED,      List.of()
    );

    private final NonConformanceRepository repository;

    public TransitionNcStatusUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NcResponse execute(UUID id, NcStatus targetStatus, String username) {
        NonConformance nc = repository.findById(id)
                .orElseThrow(() -> new NcNotFoundException(id));

        List<NcStatus> allowed = ALLOWED.get(nc.getStatus());
        if (!allowed.contains(targetStatus)) {
            throw new InvalidNcTransitionException(nc.getStatus(), targetStatus, allowed);
        }

        nc.setStatus(targetStatus);

        if (targetStatus == NcStatus.CLOSED) {
            nc.setClosedAt(LocalDateTime.now());
            nc.setClosedBy(username);
        } else if (targetStatus == NcStatus.OPEN) {
            nc.setClosedAt(null);
            nc.setClosedBy(null);
        }

        return NcResponse.from(repository.save(nc));
    }
}
