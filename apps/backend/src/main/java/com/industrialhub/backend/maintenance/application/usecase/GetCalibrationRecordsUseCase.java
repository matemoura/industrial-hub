package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.CalibrationRecordResponse;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetCalibrationRecordsUseCase {

    private final CalibrationRecordRepository recordRepository;

    public GetCalibrationRecordsUseCase(CalibrationRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordResponse> execute(UUID scheduleId) {
        return recordRepository.findByScheduleIdOrderByCalibratedAtDesc(scheduleId)
            .stream()
            .map(CalibrationRecordResponse::from)
            .toList();
    }
}
