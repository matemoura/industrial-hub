package com.industrialhub.backend.oee.application.dto;

import java.util.List;

public record PeriodComparisonResponse(
        List<BenchmarkResponse> periodA,
        List<BenchmarkResponse> periodB,
        Double improvementPct   // null se periodA ou periodB vazio
) {}
