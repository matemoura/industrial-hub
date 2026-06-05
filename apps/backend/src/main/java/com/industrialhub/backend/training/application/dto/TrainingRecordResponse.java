package com.industrialhub.backend.training.application.dto;

import com.industrialhub.backend.training.domain.EffectivenessResult;
import com.industrialhub.backend.training.domain.TrainingRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TrainingRecordResponse(
    UUID id,
    UUID courseId,
    String courseCode,
    String courseTitle,
    String username,
    LocalDate completedAt,
    LocalDate expiresAt,
    String instructorName,
    Integer score,
    boolean passed,
    boolean hasCertificate,
    String recordedBy,
    LocalDateTime recordedAt,
    LocalDate effectivenessAssessedAt,
    String effectivenessAssessedBy,
    EffectivenessResult effectivenessResult,
    String effectivenessNotes
) {
    public static TrainingRecordResponse from(TrainingRecord r) {
        return new TrainingRecordResponse(
            r.getId(),
            r.getCourse().getId(),
            r.getCourse().getCode(),
            r.getCourse().getTitle(),
            r.getUsername(),
            r.getCompletedAt(),
            r.getExpiresAt(),
            r.getInstructorName(),
            r.getScore(),
            r.isPassed(),
            r.getCertificateStoragePath() != null,
            r.getRecordedBy(),
            r.getRecordedAt(),
            r.getEffectivenessAssessedAt(),
            r.getEffectivenessAssessedBy(),
            r.getEffectivenessResult(),
            r.getEffectivenessNotes()
        );
    }
}
