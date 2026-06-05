package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.training.application.dto.TrainingRecordResponse;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetMyTrainingRecordsUseCase {

    private final TrainingRecordRepository recordRepository;

    public GetMyTrainingRecordsUseCase(TrainingRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<TrainingRecordResponse> execute(String username) {
        return recordRepository.findByUsername(username).stream()
            .map(TrainingRecordResponse::from)
            .toList();
    }
}
