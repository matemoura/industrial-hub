package com.industrialhub.backend.training.application.dto;

import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record TrainingCourseResponse(
    UUID id,
    String code,
    String title,
    String description,
    TrainingCategory category,
    Integer durationHours,
    Integer validityMonths,
    Set<String> requiredForRoles,
    boolean active,
    LocalDateTime createdAt
) {
    public static TrainingCourseResponse from(TrainingCourse c) {
        return new TrainingCourseResponse(
            c.getId(), c.getCode(), c.getTitle(), c.getDescription(),
            c.getCategory(), c.getDurationHours(), c.getValidityMonths(),
            c.getRequiredForRoles(), c.isActive(), c.getCreatedAt()
        );
    }
}
