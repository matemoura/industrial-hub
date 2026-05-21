package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.EscalationRunResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationJob {

    private static final Logger log = LoggerFactory.getLogger(EscalationJob.class);

    private final EscalationUseCase escalationUseCase;

    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    public void run() {
        log.info("EscalationJob iniciado");
        EscalationRunResponse result = escalationUseCase.execute();
        log.info("EscalationJob concluído: {} NCs e {} OSs marcadas",
            result.breachedNcs(), result.breachedWorkOrders());
    }
}
