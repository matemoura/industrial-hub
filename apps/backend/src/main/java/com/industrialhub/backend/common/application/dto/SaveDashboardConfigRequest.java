package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveDashboardConfigRequest(

    @NotBlank(message = "widgetsJson não pode ser nulo ou vazio")
    @Size(max = 10_240, message = "widgetsJson excede o limite de 10 KB")
    String widgetsJson
) {}
