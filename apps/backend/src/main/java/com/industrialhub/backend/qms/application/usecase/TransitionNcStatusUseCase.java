package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.webhook.NcStatusChangedWebhookPayload;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
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
    private final AuditService auditService;
    private final WebhookDispatchService webhookDispatchService;

    public TransitionNcStatusUseCase(NonConformanceRepository repository,
                                      AuditService auditService,
                                      WebhookDispatchService webhookDispatchService) {
        this.repository = repository;
        this.auditService = auditService;
        this.webhookDispatchService = webhookDispatchService;
    }

    @Transactional
    public NcResponse execute(UUID id, NcStatus targetStatus, String username) {
        NonConformance nc = repository.findById(id)
                .orElseThrow(() -> new NcNotFoundException(id));

        List<NcStatus> allowed = ALLOWED.get(nc.getStatus());
        if (!allowed.contains(targetStatus)) {
            throw new InvalidNcTransitionException(nc.getStatus(), targetStatus, allowed);
        }

        NcStatus previousStatus = nc.getStatus();
        nc.setStatus(targetStatus);

        if (targetStatus == NcStatus.CLOSED) {
            nc.setClosedAt(LocalDateTime.now());
            nc.setClosedBy(username);
        } else if (targetStatus == NcStatus.OPEN) {
            nc.setClosedAt(null);
            nc.setClosedBy(null);
        }

        NcResponse response = NcResponse.from(repository.save(nc));

        auditService.log(username, AuditAction.NC_STATUS_CHANGED, "NonConformance", id,
                Map.of("from", previousStatus.name(), "to", targetStatus.name()));

        webhookDispatchService.dispatch(WebhookEvent.NC_STATUS_CHANGED,
                new NcStatusChangedWebhookPayload(id, nc.getTitle(), previousStatus, targetStatus, username));

        return response;
    }
}
