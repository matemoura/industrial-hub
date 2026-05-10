package com.industrialhub.backend.oee.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ImportResultDto(
        UUID batchId,
        LocalDate periodDate,
        int workerCount,
        int recordsImported,
        int recordsSkipped,
        List<String> errors
) {}
