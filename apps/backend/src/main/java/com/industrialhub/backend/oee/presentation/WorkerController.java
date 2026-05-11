package com.industrialhub.backend.oee.presentation;

import com.industrialhub.backend.oee.application.dto.WorkerDto;
import com.industrialhub.backend.oee.application.usecase.GetWorkersUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {

    private final GetWorkersUseCase useCase;

    public WorkerController(GetWorkersUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<List<WorkerDto>> getWorkers() {
        return ResponseEntity.ok(useCase.execute());
    }
}
