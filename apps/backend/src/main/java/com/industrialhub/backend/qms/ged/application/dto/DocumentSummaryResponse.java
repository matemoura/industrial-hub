package com.industrialhub.backend.qms.ged.application.dto;

import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentSummaryResponse(
    UUID id,
    String code,
    String title,
    DocumentCategory category,
    DocumentStatus status,
    String currentRevisionNumber,
    LocalDateTime updatedAt
) {}
