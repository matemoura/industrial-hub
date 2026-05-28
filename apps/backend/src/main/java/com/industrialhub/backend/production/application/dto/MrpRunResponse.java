package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.MrpRun;

import java.time.LocalDateTime;
import java.util.UUID;

public record MrpRunResponse(
        UUID id,
        LocalDateTime runAt,
        String runBy,
        boolean isDryRun,
        Integer productsAnalyzed,
        Integer suggestionsGenerated,
        Integer alreadyOk
) {
    public static MrpRunResponse from(MrpRun run) {
        return new MrpRunResponse(
                run.getId(),
                run.getRunAt(),
                run.getRunBy(),
                run.isDryRun(),
                run.getProductsAnalyzed(),
                run.getSuggestionsGenerated(),
                run.getAlreadyOk()
        );
    }
}
