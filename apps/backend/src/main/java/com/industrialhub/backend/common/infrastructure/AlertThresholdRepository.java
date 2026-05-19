package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertThresholdRepository extends JpaRepository<AlertThreshold, UUID> {

    Optional<AlertThreshold> findByMetricAndActiveTrue(AlertMetric metric);

    List<AlertThreshold> findAllByActiveTrueOrderByMetric();
}
