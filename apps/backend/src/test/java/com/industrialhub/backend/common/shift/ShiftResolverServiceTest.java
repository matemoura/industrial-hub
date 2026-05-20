package com.industrialhub.backend.common.shift;

import com.industrialhub.backend.common.application.usecase.ShiftResolverService;
import com.industrialhub.backend.common.domain.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftResolverServiceTest {

    private ShiftResolverService service;

    @BeforeEach
    void setUp() {
        service = new ShiftResolverService();
    }

    private Shift daytimeShift(String name, LocalTime start, LocalTime end) {
        return Shift.builder()
                .id(UUID.randomUUID())
                .name(name)
                .startTime(start)
                .endTime(end)
                .overnight(false)
                .active(true)
                .build();
    }

    private Shift overnightShift(String name, LocalTime start, LocalTime end) {
        return Shift.builder()
                .id(UUID.randomUUID())
                .name(name)
                .startTime(start)
                .endTime(end)
                .overnight(true)
                .active(true)
                .build();
    }

    // AC#10 - 1: turno diurno encontrado quando now está dentro do intervalo
    @Test
    void shouldFindDaytimeShiftWhenNowIsWithinRange() {
        Shift shift = daytimeShift("Turno A", LocalTime.of(6, 0), LocalTime.of(14, 0));

        Optional<Shift> result = service.resolveCurrentShift(
                List.of(shift), LocalTime.of(10, 0));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Turno A");
    }

    // AC#10 - 2: endTime é exclusivo — não encontra quando now == endTime
    @Test
    void shouldNotFindDaytimeShiftWhenNowEqualsEndTime() {
        Shift shift = daytimeShift("Turno A", LocalTime.of(6, 0), LocalTime.of(14, 0));

        Optional<Shift> result = service.resolveCurrentShift(
                List.of(shift), LocalTime.of(14, 0));

        assertThat(result).isEmpty();
    }

    // AC#10 - 3: turno noturno encontrado às 23:30
    @Test
    void shouldFindOvernightShiftAt2330() {
        Shift shift = overnightShift("Turno Noturno", LocalTime.of(22, 0), LocalTime.of(6, 0));

        Optional<Shift> result = service.resolveCurrentShift(
                List.of(shift), LocalTime.of(23, 30));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Turno Noturno");
    }

    // AC#10 - 4: turno noturno encontrado às 03:00 (após meia-noite)
    @Test
    void shouldFindOvernightShiftAt0300() {
        Shift shift = overnightShift("Turno Noturno", LocalTime.of(22, 0), LocalTime.of(6, 0));

        Optional<Shift> result = service.resolveCurrentShift(
                List.of(shift), LocalTime.of(3, 0));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Turno Noturno");
    }

    // AC#10 - 5: turno noturno NÃO encontrado às 10:00 (fora do intervalo)
    @Test
    void shouldNotFindOvernightShiftAt1000() {
        Shift shift = overnightShift("Turno Noturno", LocalTime.of(22, 0), LocalTime.of(6, 0));

        Optional<Shift> result = service.resolveCurrentShift(
                List.of(shift), LocalTime.of(10, 0));

        assertThat(result).isEmpty();
    }

    // AC#10 - 6: lista vazia retorna empty
    @Test
    void shouldReturnEmptyWhenNoActiveShifts() {
        Optional<Shift> result = service.resolveCurrentShift(List.of(), LocalTime.of(12, 0));

        assertThat(result).isEmpty();
    }
}
