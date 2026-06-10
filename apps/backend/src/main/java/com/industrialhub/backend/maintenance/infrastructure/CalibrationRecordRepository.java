package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.CalibrationRecord;
import com.industrialhub.backend.maintenance.domain.CalibrationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, UUID> {

    List<CalibrationRecord> findByScheduleIdOrderByCalibratedAtDesc(UUID scheduleId);

    long countByEquipmentIdAndCalibratedAtAfter(UUID equipmentId, LocalDate since);

    long countByResultAndCalibratedAtAfter(CalibrationResult result, LocalDate since);

    long countByCalibratedAtAfter(LocalDate since);

    // US-135 — Management Review aggregations
    long countByResultAndCalibratedAtBetween(CalibrationResult result, LocalDate from, LocalDate to);

    long countByCalibratedAtBetween(LocalDate from, LocalDate to);
}
