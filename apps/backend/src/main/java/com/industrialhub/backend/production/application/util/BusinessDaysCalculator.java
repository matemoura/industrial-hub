package com.industrialhub.backend.production.application.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * ADR-043 Decisão 4 — cálculo de dias úteis (seg–sex) sem biblioteca de calendário.
 * Feriados fora de scope; o usuário ajusta leadTimeDays manualmente como folga.
 */
public final class BusinessDaysCalculator {

    private BusinessDaysCalculator() {}

    /**
     * Conta dias úteis (seg–sex) entre {@code from} (inclusive) e {@code to} (exclusive).
     * Retorna mínimo 1 para evitar divisão por zero no cálculo de staffing.
     */
    public static int workdaysUntil(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) return 1;
        int days = 0;
        LocalDate d = from;
        while (d.isBefore(to)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) days++;
            d = d.plusDays(1);
        }
        return Math.max(1, days);
    }
}
