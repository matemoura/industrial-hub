package com.industrialhub.backend.training.application.dto;

import com.industrialhub.backend.training.domain.CompetencyStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CompetencyMatrixRow(
    String username,
    String role,
    UUID courseId,
    String courseCode,
    String courseTitle,
    CompetencyStatus status,
    LocalDate completedAt,
    LocalDate expiresAt
) {}
