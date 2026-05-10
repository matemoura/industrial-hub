package com.industrialhub.backend.oee.application.parser;

import java.time.LocalDate;
import java.util.List;

public record ParseResult(
        LocalDate periodDate,
        int workerCount,
        List<ParsedRow> rows,
        int skippedCount,
        List<String> errors
) {}
