package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.WorkerDto;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetWorkersUseCase {

    private final TimeRecordRepository repository;

    public GetWorkersUseCase(TimeRecordRepository repository) {
        this.repository = repository;
    }

    public List<WorkerDto> execute() {
        return repository.findDistinctWorkers()
                .stream()
                .map(p -> new WorkerDto(p.getWorkerId(), p.getWorkerName()))
                .toList();
    }
}
