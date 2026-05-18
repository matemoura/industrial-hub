package com.industrialhub.backend.maintenance.application.dto;

import java.util.List;

public record MttrTrendResponse(
    List<String> monthLabels,
    List<Double> mttrValues
) {}
