package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.dto.CreateAlertThresholdRequest;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAlertThresholdUseCase {

    private final AlertThresholdRepository alertThresholdRepository;

    public CreateAlertThresholdUseCase(AlertThresholdRepository alertThresholdRepository) {
        this.alertThresholdRepository = alertThresholdRepository;
    }

    @Transactional
    public AlertThresholdResponse execute(CreateAlertThresholdRequest request, String username) {
        alertThresholdRepository.findByMetricAndActiveTrue(request.metric())
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Já existe threshold ativo para a métrica: " + request.metric().name());
                });

        AlertThreshold threshold = AlertThreshold.builder()
                .metric(request.metric())
                .threshold(request.threshold())
                .emailEnabled(request.emailEnabled())
                .active(true)
                .createdBy(username)
                .build();

        AlertThreshold saved = alertThresholdRepository.save(threshold);
        return AlertThresholdResponse.from(saved);
    }
}
