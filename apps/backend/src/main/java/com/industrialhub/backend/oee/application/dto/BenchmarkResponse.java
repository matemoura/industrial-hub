package com.industrialhub.backend.oee.application.dto;

public record BenchmarkResponse(
        String label,
        double avgOee,
        double minOee,
        double maxOee,
        Double stdDev,
        long sampleCount,
        Long recordsWithoutShift
) {}
