package com.industrialhub.backend.qms.risk.application.dto;

import java.util.List;

public record RiskMatrixResponse(
    List<RiskMatrixCell> cells
) {}
