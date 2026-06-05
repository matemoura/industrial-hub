package com.industrialhub.backend.training;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.training.application.usecase.AssessEffectivenessUseCase;
import com.industrialhub.backend.training.domain.EffectivenessResult;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.domain.TrainingRecordNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessEffectivenessUseCaseTest {

    @Mock private TrainingRecordRepository recordRepository;
    @Mock private AuditService auditService;

    private AssessEffectivenessUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AssessEffectivenessUseCase(recordRepository, auditService);
    }

    private TrainingCourse buildCourse() {
        return TrainingCourse.builder()
            .id(UUID.randomUUID()).code("GMP-001").title("Curso")
            .category(TrainingCategory.GMP).durationHours(4)
            .active(true).createdAt(LocalDateTime.now()).build();
    }

    @Test
    void shouldSetEffectivenessOnPassedRecord() {
        UUID recordId = UUID.randomUUID();
        TrainingRecord record = TrainingRecord.builder()
            .id(recordId).course(buildCourse()).username("joao")
            .completedAt(LocalDate.now()).passed(true).recordedBy("admin").build();

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(record));

        var req = new AssessEffectivenessUseCase.Request(
            EffectivenessResult.EFFECTIVE, "Demonstrou competência.");

        var response = useCase.execute(recordId, req, "supervisor");

        assertThat(response.effectivenessResult()).isEqualTo(EffectivenessResult.EFFECTIVE);
        assertThat(response.effectivenessAssessedBy()).isEqualTo("supervisor");
        assertThat(response.effectivenessAssessedAt()).isEqualTo(LocalDate.now());
        assertThat(response.effectivenessNotes()).isEqualTo("Demonstrou competência.");
    }

    @Test
    void shouldThrowWhenRecordNotFound() {
        UUID id = UUID.randomUUID();
        when(recordRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id,
            new AssessEffectivenessUseCase.Request(EffectivenessResult.EFFECTIVE, null), "sup"))
            .isInstanceOf(TrainingRecordNotFoundException.class);
    }

    @Test
    void shouldThrowWhenRecordNotPassed() {
        UUID recordId = UUID.randomUUID();
        TrainingRecord record = TrainingRecord.builder()
            .id(recordId).course(buildCourse()).username("joao")
            .completedAt(LocalDate.now()).passed(false).recordedBy("admin").build();

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> useCase.execute(recordId,
            new AssessEffectivenessUseCase.Request(EffectivenessResult.NOT_EFFECTIVE, null), "sup"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("passed=false");
    }

    @Test
    void shouldThrow409WhenEffectivenessAlreadyAssessed() {
        UUID recordId = UUID.randomUUID();
        TrainingRecord record = TrainingRecord.builder()
            .id(recordId).course(buildCourse()).username("joao")
            .completedAt(LocalDate.now()).passed(true).recordedBy("admin")
            .effectivenessResult(EffectivenessResult.EFFECTIVE)
            .effectivenessAssessedAt(LocalDate.now().minusDays(5))
            .effectivenessAssessedBy("supervisor")
            .build();

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> useCase.execute(recordId,
            new AssessEffectivenessUseCase.Request(EffectivenessResult.NOT_EFFECTIVE, null), "sup"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("já foi avaliada");
    }
}
