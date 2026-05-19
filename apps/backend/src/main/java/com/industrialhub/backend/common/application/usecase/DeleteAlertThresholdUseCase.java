package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.domain.AlertThresholdNotFoundException;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteAlertThresholdUseCase {

    private final AlertThresholdRepository alertThresholdRepository;

    public DeleteAlertThresholdUseCase(AlertThresholdRepository alertThresholdRepository) {
        this.alertThresholdRepository = alertThresholdRepository;
    }

    @Transactional
    public void execute(UUID id) {
        AlertThreshold threshold = alertThresholdRepository.findById(id)
                .orElseThrow(() -> new AlertThresholdNotFoundException(id));

        threshold.setActive(false);
        alertThresholdRepository.save(threshold);
    }
}
