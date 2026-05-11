package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.WorkerDto;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.oee.infrastructure.WorkerProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWorkersUseCaseTest {

    @Mock
    private TimeRecordRepository repository;

    @InjectMocks
    private GetWorkersUseCase useCase;

    @Test
    void returnsDistinctWorkersOrderedByName() {
        when(repository.findDistinctWorkers()).thenReturn(List.of(
                projection(1001L, "Alice"),
                projection(1002L, "Bob")
        ));

        List<WorkerDto> result = useCase.execute();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workerId()).isEqualTo(1001L);
        assertThat(result.get(0).workerName()).isEqualTo("Alice");
        assertThat(result.get(1).workerId()).isEqualTo(1002L);
        assertThat(result.get(1).workerName()).isEqualTo("Bob");
    }

    @Test
    void emptyRepository_returnsEmptyList() {
        when(repository.findDistinctWorkers()).thenReturn(List.of());

        assertThat(useCase.execute()).isEmpty();
    }

    @Test
    void projection_isMappedCorrectly() {
        when(repository.findDistinctWorkers()).thenReturn(List.of(
                projection(639L, "João Silva")
        ));

        WorkerDto dto = useCase.execute().getFirst();

        assertThat(dto.workerId()).isEqualTo(639L);
        assertThat(dto.workerName()).isEqualTo("João Silva");
    }

    private WorkerProjection projection(Long workerId, String workerName) {
        return new WorkerProjection() {
            @Override public Long getWorkerId() { return workerId; }
            @Override public String getWorkerName() { return workerName; }
        };
    }
}
