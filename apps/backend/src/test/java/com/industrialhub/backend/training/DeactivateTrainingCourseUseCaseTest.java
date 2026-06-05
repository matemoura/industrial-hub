package com.industrialhub.backend.training;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.training.application.usecase.DeactivateTrainingCourseUseCase;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateTrainingCourseUseCaseTest {

    @Mock private TrainingCourseRepository courseRepository;
    @Mock private AuditService auditService;

    private DeactivateTrainingCourseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeactivateTrainingCourseUseCase(courseRepository, auditService);
    }

    @Test
    void shouldDeactivateActiveCourse() {
        UUID id = UUID.randomUUID();
        TrainingCourse course = TrainingCourse.builder()
            .id(id).code("GMP-001").title("Curso").category(TrainingCategory.GMP)
            .durationHours(4).active(true).createdAt(LocalDateTime.now()).build();

        when(courseRepository.findById(id)).thenReturn(Optional.of(course));

        useCase.execute(id, "admin");

        assertThat(course.isActive()).isFalse();
    }

    @Test
    void shouldBeIdempotentForAlreadyInactiveCourse() {
        UUID id = UUID.randomUUID();
        TrainingCourse course = TrainingCourse.builder()
            .id(id).code("GMP-001").title("Curso").category(TrainingCategory.GMP)
            .durationHours(4).active(false).createdAt(LocalDateTime.now()).build();

        when(courseRepository.findById(id)).thenReturn(Optional.of(course));

        useCase.execute(id, "admin");

        assertThat(course.isActive()).isFalse();
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldThrow404WhenCourseNotFound() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
            .isInstanceOf(TrainingCourseNotFoundException.class);
    }
}
