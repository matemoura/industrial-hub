package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.training.application.dto.TrainingCourseResponse;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UpdateTrainingCourseUseCase {

    private final TrainingCourseRepository courseRepository;
    private final AuditService auditService;

    public UpdateTrainingCourseUseCase(TrainingCourseRepository courseRepository,
                                       AuditService auditService) {
        this.courseRepository = courseRepository;
        this.auditService = auditService;
    }

    public record Request(
        @NotBlank String title,
        String description,
        @NotNull TrainingCategory category,
        @NotNull @Positive Integer durationHours,
        Integer validityMonths,
        Set<String> requiredForRoles
    ) {}

    @Transactional
    public TrainingCourseResponse execute(UUID id, Request req, String principal) {
        TrainingCourse course = courseRepository.findById(id)
            .orElseThrow(() -> new TrainingCourseNotFoundException(id));

        course.setTitle(req.title().strip());
        course.setDescription(req.description());
        course.setCategory(req.category());
        course.setDurationHours(req.durationHours());
        course.setValidityMonths(req.validityMonths());
        if (req.requiredForRoles() != null) {
            course.setRequiredForRoles(req.requiredForRoles());
        }

        auditService.log(principal, AuditAction.TRAINING_COURSE_UPDATED, "TrainingCourse",
            id, Map.of("code", course.getCode()));

        return TrainingCourseResponse.from(course);
    }
}
