package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeactivateTrainingCourseUseCase {

    private final TrainingCourseRepository courseRepository;
    private final AuditService auditService;

    public DeactivateTrainingCourseUseCase(TrainingCourseRepository courseRepository,
                                           AuditService auditService) {
        this.courseRepository = courseRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String principal) {
        TrainingCourse course = courseRepository.findById(id)
            .orElseThrow(() -> new TrainingCourseNotFoundException(id));

        if (!course.isActive()) return;

        course.setActive(false);

        auditService.log(principal, AuditAction.TRAINING_COURSE_DEACTIVATED, "TrainingCourse",
            id, Map.of("code", course.getCode()));
    }
}
