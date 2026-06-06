package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.CalibrationSummary;
import com.industrialhub.backend.maintenance.domain.CalibrationResult;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationRecordRepository;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class GetCalibrationSummaryUseCase {

    private final CalibrationScheduleRepository scheduleRepository;
    private final CalibrationRecordRepository recordRepository;

    public GetCalibrationSummaryUseCase(CalibrationScheduleRepository scheduleRepository,
                                         CalibrationRecordRepository recordRepository) {
        this.scheduleRepository = scheduleRepository;
        this.recordRepository = recordRepository;
    }

    @Transactional(readOnly = true)
    public CalibrationSummary execute() {
        LocalDate today = LocalDate.now();
        LocalDate in14Days = today.plusDays(14);
        LocalDate thirtyDaysAgo = today.minusDays(30);

        long totalSchedules = scheduleRepository.countByActiveTrue();
        long overdueCount = scheduleRepository.countByActiveTrueAndNextDueAtBefore(today);
        long dueSoon14Days = scheduleRepository.countByActiveTrueAndNextDueAtBetween(today, in14Days);
        long lastMonthRecords = recordRepository.countByCalibratedAtAfter(thirtyDaysAgo);
        long outOfToleranceLastMonth = recordRepository.countByResultAndCalibratedAtAfter(
            CalibrationResult.OUT_OF_TOLERANCE, thirtyDaysAgo);

        return new CalibrationSummary(totalSchedules, overdueCount, dueSoon14Days,
            lastMonthRecords, outOfToleranceLastMonth);
    }
}
