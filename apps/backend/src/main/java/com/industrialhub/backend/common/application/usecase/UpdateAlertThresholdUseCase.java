package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.dto.UpdateAlertThresholdRequest;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.domain.AlertThresholdNotFoundException;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateAlertThresholdUseCase {

    private final AlertThresholdRepository alertThresholdRepository;

    public UpdateAlertThresholdUseCase(AlertThresholdRepository alertThresholdRepository) {
        this.alertThresholdRepository = alertThresholdRepository;
    }

    @Transactional
    public AlertThresholdResponse execute(UUID id, UpdateAlertThresholdRequest request) {
        AlertThreshold threshold = alertThresholdRepository.findById(id)
                .orElseThrow(() -> new AlertThresholdNotFoundException(id));

        threshold.setThreshold(request.threshold());
        threshold.setEmailEnabled(request.emailEnabled());

        AlertThreshold saved = alertThresholdRepository.save(threshold);
        return AlertThresholdResponse.from(saved);
    }
}
