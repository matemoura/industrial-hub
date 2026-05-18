package com.industrialhub.backend.common.application.dto;

import java.util.List;

public record TimeSeriesResponse(
    List<String> labels,
    List<Double> values
) {}
