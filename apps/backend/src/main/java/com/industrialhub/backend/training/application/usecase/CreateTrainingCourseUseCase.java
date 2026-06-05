package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.training.application.dto.TrainingCourseResponse;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseCodeAlreadyExistsException;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class CreateTrainingCourseUseCase {

    private final TrainingCourseRepository courseRepository;
    private final AuditService auditService;

    public CreateTrainingCourseUseCase(TrainingCourseRepository courseRepository,
                                       AuditService auditService) {
        this.courseRepository = courseRepository;
        this.auditService = auditService;
    }

    public record Request(
        @NotBlank String code,
        @NotBlank String title,
        String description,
        @NotNull TrainingCategory category,
        @NotNull @Positive Integer durationHours,
        Integer validityMonths,
        Set<String> requiredForRoles
    ) {}

    @Transactional
    public TrainingCourseResponse execute(Request req, String principal) {
        TrainingCourse course = TrainingCourse.builder()
            .code(req.code().toUpperCase().strip())
            .title(req.title().strip())
            .description(req.description())
            .category(req.category())
            .durationHours(req.durationHours())
            .validityMonths(req.validityMonths())
            .requiredForRoles(req.requiredForRoles() != null ? req.requiredForRoles() : Set.of())
            .build();

        try {
            course = courseRepository.save(course);
        } catch (DataIntegrityViolationException e) {
            throw new TrainingCourseCodeAlreadyExistsException(req.code());
        }

        auditService.log(principal, AuditAction.TRAINING_COURSE_CREATED, "TrainingCourse",
            course.getId(), Map.of("code", course.getCode()));

        return TrainingCourseResponse.from(course);
    }
}
