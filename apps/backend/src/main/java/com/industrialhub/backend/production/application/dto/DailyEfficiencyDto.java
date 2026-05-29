package com.industrialhub.backend.production.application.dto;

import java.time.LocalDate;

/**
 * US-104 — ponto de dados para o gráfico de tendência de eficiência no painel executivo.
 * avgEfficiency = avg(producedQty / plannedQty * 100) para OPs DONE no dia dado.
 */
public record DailyEfficiencyDto(LocalDate date, Double avgEfficiency) {}
