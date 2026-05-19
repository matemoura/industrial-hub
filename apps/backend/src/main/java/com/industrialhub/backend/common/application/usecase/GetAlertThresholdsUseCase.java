package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetAlertThresholdsUseCase {

    private final AlertThresholdRepository alertThresholdRepository;

    public GetAlertThresholdsUseCase(AlertThresholdRepository alertThresholdRepository) {
        this.alertThresholdRepository = alertThresholdRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertThresholdResponse> execute() {
        return alertThresholdRepository.findAllByActiveTrueOrderByMetric()
                .stream()
                .map(AlertThresholdResponse::from)
                .toList();
    }
}
