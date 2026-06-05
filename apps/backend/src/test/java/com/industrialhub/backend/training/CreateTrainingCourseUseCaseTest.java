package com.industrialhub.backend.training;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.training.application.dto.TrainingCourseResponse;
import com.industrialhub.backend.training.application.usecase.CreateTrainingCourseUseCase;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseCodeAlreadyExistsException;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTrainingCourseUseCaseTest {

    @Mock private TrainingCourseRepository courseRepository;
    @Mock private AuditService auditService;

    private CreateTrainingCourseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateTrainingCourseUseCase(courseRepository, auditService);
    }

    @Test
    void shouldCreateCourseAndAuditLog() {
        UUID id = UUID.randomUUID();
        TrainingCourse saved = TrainingCourse.builder()
            .id(id).code("GMP-001").title("Boas Práticas de Fabricação")
            .category(TrainingCategory.GMP).durationHours(8).validityMonths(12)
            .createdAt(LocalDateTime.now()).build();

        when(courseRepository.save(any())).thenReturn(saved);

        var req = new CreateTrainingCourseUseCase.Request(
            "GMP-001", "Boas Práticas de Fabricação", null,
            TrainingCategory.GMP, 8, 12, Set.of("OPERATOR"));

        TrainingCourseResponse response = useCase.execute(req, "admin");

        assertThat(response.code()).isEqualTo("GMP-001");
        assertThat(response.durationHours()).isEqualTo(8);
        assertThat(response.validityMonths()).isEqualTo(12);

        verify(auditService).log(eq("admin"), eq(AuditAction.TRAINING_COURSE_CREATED),
            eq("TrainingCourse"), eq(id), any());
    }

    @Test
    void shouldNormalizecodeToUpperCase() {
        ArgumentCaptor<TrainingCourse> captor = ArgumentCaptor.forClass(TrainingCourse.class);
        UUID id = UUID.randomUUID();
        when(courseRepository.save(captor.capture())).thenAnswer(inv -> {
            TrainingCourse c = inv.getArgument(0);
            c.setId(id);
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        var req = new CreateTrainingCourseUseCase.Request(
            "gmp-001", "Curso", null, TrainingCategory.GMP, 4, null, null);

        useCase.execute(req, "admin");

        assertThat(captor.getValue().getCode()).isEqualTo("GMP-001");
    }

    @Test
    void shouldThrow409WhenCodeAlreadyExists() {
        when(courseRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

        var req = new CreateTrainingCourseUseCase.Request(
            "DUP-001", "Duplicado", null, TrainingCategory.OTHER, 2, null, null);

        assertThatThrownBy(() -> useCase.execute(req, "admin"))
            .isInstanceOf(TrainingCourseCodeAlreadyExistsException.class)
            .hasMessageContaining("DUP-001");
    }
}
