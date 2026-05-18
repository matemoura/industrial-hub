package com.industrialhub.backend.oee.application.dto;

import java.util.List;

public record OeeTrendResponse(
    List<String> weekLabels,
    List<Double> oeeValues,
    List<Integer> sampleCounts
) {}
