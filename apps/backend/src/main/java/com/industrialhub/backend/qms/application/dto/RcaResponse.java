package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.RootCauseAnalysis;

import java.time.LocalDateTime;
import java.util.UUID;

public record RcaResponse(
    UUID id,
    UUID ncId,
    String why1,
    String answer1,
    String why2,
    String answer2,
    String why3,
    String answer3,
    String why4,
    String answer4,
    String why5,
    String answer5,
    String rootCause,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static RcaResponse from(RootCauseAnalysis rca) {
        return new RcaResponse(
            rca.getId(),
            rca.getNonConformance().getId(),
            rca.getWhy1(),
            rca.getAnswer1(),
            rca.getWhy2(),
            rca.getAnswer2(),
            rca.getWhy3(),
            rca.getAnswer3(),
            rca.getWhy4(),
            rca.getAnswer4(),
            rca.getWhy5(),
            rca.getAnswer5(),
            rca.getRootCause(),
            rca.getCreatedBy(),
            rca.getCreatedAt(),
            rca.getUpdatedAt()
        );
    }
}
