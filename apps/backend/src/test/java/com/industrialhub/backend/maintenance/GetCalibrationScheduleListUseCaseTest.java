package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.maintenance.application.dto.CalibrationScheduleResponse;
import com.industrialhub.backend.maintenance.application.usecase.GetCalibrationSchedulesUseCase;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-121 ACs cobertos:
 * (d) filtro ?overdue=true â†’ somente registros com nextDueAt < today
 *     (repositÃ³rio: findByActiveTrueAndNextDueAtBefore(today))
 * AC5: GET /calibration-records?scheduleId=... â†’ delegado a findByEquipmentIdAndActiveTrue
 */
@ExtendWith(MockitoExtension.class)
class GetCalibrationScheduleListUseCaseTest {

    @Mock private CalibrationScheduleRepository calibrationScheduleRepository;

    private GetCalibrationSchedulesUseCase useCase;

    private Equipment equipment;

    @BeforeEach
    void setUp() {
        useCase = new GetCalibrationSchedulesUseCase(calibrationScheduleRepository);

        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-003")
                .name("ManÃ´metro AnalÃ³gico")
                .type(EquipmentType.TOOL)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();
    }

    // â”€â”€â”€ AC (d) US-121: overdue=true â†’ findByActiveTrueAndNextDueAtBefore(today) â”€

    @Test
    void shouldCallOverdueQuery_whenOverdueFilterIsTrue() {
        // AC US-121 (d): overdue=true delega para repositÃ³rio com today como limite
        CalibrationSchedule overdueSchedule = buildSchedule(LocalDate.now().minusDays(5));

        when(calibrationScheduleRepository.findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now())))
                .thenReturn(List.of(overdueSchedule));

        List<CalibrationScheduleResponse> responses = useCase.execute(null, true);

        assertThat(responses).hasSize(1);
        verify(calibrationScheduleRepository).findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now()));
    }

    @Test
    void shouldReturnOverdueTrue_forScheduleWithNextDueAtInPast() {
        // AC US-121 (d): resposta deve ter overdue=true calculado pelo from()
        CalibrationSchedule overdueSchedule = buildSchedule(LocalDate.now().minusDays(3));

        when(calibrationScheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of(overdueSchedule));

        List<CalibrationScheduleResponse> responses = useCase.execute(null, true);

        assertThat(responses.get(0).overdue()).isTrue();
        assertThat(responses.get(0).nextDueAt()).isBefore(LocalDate.now());
    }

    @Test
    void shouldNotReturnSchedulesDueToday_inOverdueFilter() {
        // AC US-121 (d): nextDueAt = today NÃƒO Ã© overdue (somente < today)
        // repositÃ³rio retorna vazio â†’ nenhum plano com nextDueAt = today incluÃ­do
        when(calibrationScheduleRepository.findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now())))
                .thenReturn(List.of()); // today nÃ£o estÃ¡ no resultado

        List<CalibrationScheduleResponse> responses = useCase.execute(null, true);

        assertThat(responses).isEmpty();
    }

    // â”€â”€â”€ AC sem filtro: retorna todos ativos â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldCallFindByActiveTrue_whenNoFiltersApplied() {
        CalibrationSchedule active = buildSchedule(LocalDate.now().plusDays(30));

        when(calibrationScheduleRepository.findByActiveTrue()).thenReturn(List.of(active));

        List<CalibrationScheduleResponse> responses = useCase.execute(null, false);

        assertThat(responses).hasSize(1);
        verify(calibrationScheduleRepository).findByActiveTrue();
    }

    // â”€â”€â”€ AC: filtro por equipmentId â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldCallFindByEquipmentIdAndActiveTrue_whenEquipmentIdProvided() {
        UUID equipId = equipment.getId();
        CalibrationSchedule s = buildSchedule(LocalDate.now().plusDays(10));

        when(calibrationScheduleRepository.findByEquipmentIdAndActiveTrue(equipId))
                .thenReturn(List.of(s));

        List<CalibrationScheduleResponse> responses = useCase.execute(equipId, false);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).equipmentId()).isEqualTo(equipId);
        verify(calibrationScheduleRepository).findByEquipmentIdAndActiveTrue(equipId);
    }

    @Test
    void shouldReturnEmptyList_whenNoSchedulesExist() {
        when(calibrationScheduleRepository.findByActiveTrue()).thenReturn(List.of());

        List<CalibrationScheduleResponse> responses = useCase.execute(null, null);

        assertThat(responses).isEmpty();
    }

    // â”€â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private CalibrationSchedule buildSchedule(LocalDate nextDueAt) {
        return CalibrationSchedule.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .intervalDays(30)
                .nextDueAt(nextDueAt)
                .active(true)
                .createdBy("supervisor1")
                .build();
    }
}
