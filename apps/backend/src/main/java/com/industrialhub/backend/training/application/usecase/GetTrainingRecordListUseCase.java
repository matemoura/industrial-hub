package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.training.application.dto.TrainingRecordResponse;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetTrainingRecordListUseCase {

    private final TrainingRecordRepository recordRepository;

    public GetTrainingRecordListUseCase(TrainingRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public Page<TrainingRecordResponse> execute(String username, UUID courseId, Boolean passed,
                                                Pageable pageable) {
        return recordRepository.findWithFilters(username, courseId, passed, pageable)
            .map(TrainingRecordResponse::from);
    }
}
