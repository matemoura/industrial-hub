package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.domain.InvalidScheduleRecurrenceException;
import com.industrialhub.backend.maintenance.domain.ScheduleRecurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Calcula a próxima data de execução de um plano de manutenção preventiva.
 * Utilizado tanto na criação/atualização quanto no job do scheduler.
 */
public final class ScheduleRecurrenceHelper {

    private ScheduleRecurrenceHelper() {}

    /**
     * A partir de {@code from}, retorna a próxima ocorrência da recorrência.
     * Para criação/atualização, {@code from = LocalDate.now()}.
     * Para o scheduler, {@code from = lastRunAt (hoje)}.
     */
    public static LocalDate calculateNext(LocalDate from, ScheduleRecurrence recurrence,
                                          Integer dayOfWeek, Integer dayOfMonth) {
        return switch (recurrence) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> {
                LocalDate candidate = from.with(DayOfWeek.of(dayOfWeek));
                // Se o candidato ainda não passou (ou é hoje), avança uma semana
                yield from.getDayOfWeek().getValue() >= dayOfWeek
                        ? candidate.plusWeeks(1)
                        : candidate;
            }
            case MONTHLY -> {
                LocalDate next = from.plusMonths(1);
                yield next.withDayOfMonth(Math.min(dayOfMonth, next.lengthOfMonth()));
            }
        };
    }

    /**
     * Valida as regras de recorrência e lança {@link InvalidScheduleRecurrenceException} se inválido.
     */
    public static void validate(ScheduleRecurrence recurrence, Integer dayOfWeek, Integer dayOfMonth) {
        switch (recurrence) {
            case WEEKLY -> {
                if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
                    throw new InvalidScheduleRecurrenceException(
                            "dayOfWeek é obrigatório para recorrência WEEKLY (1=SEG … 7=DOM)");
                }
            }
            case MONTHLY -> {
                if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28) {
                    throw new InvalidScheduleRecurrenceException(
                            "dayOfMonth é obrigatório para recorrência MONTHLY (1–28)");
                }
            }
            case DAILY -> { /* sem validação extra */ }
        }
    }
}
