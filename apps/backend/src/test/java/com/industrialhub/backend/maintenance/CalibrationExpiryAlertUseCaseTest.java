package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.maintenance.application.usecase.CalibrationExpiryAlertUseCase;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-122 ACs cobertos:
 * AC8:  nextDueAt BETWEEN today AND today+14 â†’ notificaÃ§Ã£o WARNING (debounce 72h via NotificationService)
 * AC9:  nextDueAt < today â†’ notificaÃ§Ã£o CRITICAL (debounce 24h via NotificationService)
 * AC10: debounce delegado a NotificationService.createForUserWithDebounce (72h warning / 24h overdue)
 *       â€” teste verifica os parÃ¢metros corretos passados ao serviÃ§o
 */
@ExtendWith(MockitoExtension.class)
class CalibrationExpiryAlertUseCaseTest {

    @Mock private CalibrationScheduleRepository scheduleRepository;
    @Mock private NotificationService notificationService;

    private CalibrationExpiryAlertUseCase useCase;

    private Equipment equipment;

    @BeforeEach
    void setUp() {
        useCase = new CalibrationExpiryAlertUseCase(scheduleRepository, notificationService);

        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-004")
                .name("BalanÃ§a de precisÃ£o")
                .type(EquipmentType.TOOL)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();
    }

    // â”€â”€â”€ AC8: nextDueAt â‰¤ today+14 â†’ WARNING, debounce 72h â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldSendWarning_withDebounce72h_forDueSoonSchedule() {
        // AC US-122 AC8 + AC10: WARNING com debounce 72h
        CalibrationSchedule dueSoon = buildSchedule(LocalDate.now().plusDays(7));

        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(
                eq(LocalDate.now()), eq(LocalDate.now().plusDays(14))))
                .thenReturn(List.of(dueSoon));
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now())))
                .thenReturn(List.of());
        when(notificationService.createForUserWithDebounce(
                any(), any(), any(), eq(NotificationSeverity.WARNING), eq(72)))
                .thenReturn(1);

        int sent = useCase.execute();

        assertThat(sent).isEqualTo(1);
        verify(notificationService).createForUserWithDebounce(
                eq("supervisor1"), any(), any(), eq(NotificationSeverity.WARNING), eq(72));
    }

    @Test
    void shouldPassTodayAndToday14_toFindDueSoonQuery() {
        // AC US-122 AC8: janela de 14 dias verificada via parÃ¢metros corretos ao repositÃ³rio
        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(
                eq(LocalDate.now()), eq(LocalDate.now().plusDays(14))))
                .thenReturn(List.of());
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of());

        useCase.execute();

        verify(scheduleRepository).findByActiveTrueAndNextDueAtBetween(
                eq(LocalDate.now()), eq(LocalDate.now().plusDays(14)));
    }

    @Test
    void shouldSendWarning_forScheduleDueTomorrow() {
        // AC US-122 AC8: nextDueAt = today+1 â†’ dentro da janela â†’ WARNING
        CalibrationSchedule dueTomorrow = buildSchedule(LocalDate.now().plusDays(1));

        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of(dueTomorrow));
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of());
        when(notificationService.createForUserWithDebounce(any(), any(), any(),
                eq(NotificationSeverity.WARNING), anyInt()))
                .thenReturn(1);

        int sent = useCase.execute();

        assertThat(sent).isEqualTo(1);
    }

    // â”€â”€â”€ AC9: nextDueAt < today â†’ CRITICAL, debounce 24h â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldSendCritical_withDebounce24h_forOverdueSchedule() {
        // AC US-122 AC9 + AC10: CRITICAL com debounce 24h
        CalibrationSchedule overdue = buildSchedule(LocalDate.now().minusDays(3));

        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of());
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now())))
                .thenReturn(List.of(overdue));
        when(notificationService.createForUserWithDebounce(
                any(), any(), any(), eq(NotificationSeverity.CRITICAL), eq(24)))
                .thenReturn(1);

        int sent = useCase.execute();

        assertThat(sent).isEqualTo(1);
        verify(notificationService).createForUserWithDebounce(
                eq("supervisor1"), any(), any(), eq(NotificationSeverity.CRITICAL), eq(24));
    }

    @Test
    void shouldPassTodayAsLimit_toFindOverdueQuery() {
        // AC US-122 AC9: "vencido" = nextDueAt < today (referÃªncia correta)
        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of());
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now())))
                .thenReturn(List.of());

        useCase.execute();

        verify(scheduleRepository).findByActiveTrueAndNextDueAtBefore(eq(LocalDate.now()));
    }

    // â”€â”€â”€ AC10: debounce delegado ao NotificationService â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldPassCorrectDebounceHours_forWarningVsOverdue() {
        // AC US-122 AC10: debounce 72h para WARNING, 24h para CRITICAL
        CalibrationSchedule warning = buildSchedule(LocalDate.now().plusDays(5));
        CalibrationSchedule critical = buildSchedule(LocalDate.now().minusDays(1));

        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of(warning));
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of(critical));
        when(notificationService.createForUserWithDebounce(any(), any(), any(), any(), anyInt()))
                .thenReturn(1);

        useCase.execute();

        ArgumentCaptor<Integer> debounceCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<NotificationSeverity> severityCaptor =
                ArgumentCaptor.forClass(NotificationSeverity.class);

        verify(notificationService, times(2)).createForUserWithDebounce(
                any(), any(), any(), severityCaptor.capture(), debounceCaptor.capture());

        List<NotificationSeverity> severities = severityCaptor.getAllValues();
        List<Integer> debounceTimes = debounceCaptor.getAllValues();

        // WARNING com 72h
        int warningIdx = severities.indexOf(NotificationSeverity.WARNING);
        assertThat(debounceTimes.get(warningIdx)).isEqualTo(72);

        // CRITICAL com 24h
        int criticalIdx = severities.indexOf(NotificationSeverity.CRITICAL);
        assertThat(debounceTimes.get(criticalIdx)).isEqualTo(24);
    }

    // â”€â”€â”€ sem alertas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldReturnZero_whenNoSchedulesDueOrOverdue() {
        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of());
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of());

        int sent = useCase.execute();

        assertThat(sent).isZero();
        verify(notificationService, never()).createForUserWithDebounce(any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldReturnSumOfBothAlerts_whenBothWarningAndOverdueExist() {
        // retorna soma de warning + critical
        CalibrationSchedule warning = buildSchedule(LocalDate.now().plusDays(10));
        CalibrationSchedule critical = buildSchedule(LocalDate.now().minusDays(2));

        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of(warning));
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of(critical));
        when(notificationService.createForUserWithDebounce(any(), any(), any(), any(), anyInt()))
                .thenReturn(1);

        int sent = useCase.execute();

        assertThat(sent).isEqualTo(2);
    }

    @Test
    void shouldSkipSchedule_whenNextDueAtIsNull() {
        // schedules com nextDueAt null sÃ£o ignorados (guard no use case)
        CalibrationSchedule noDate = CalibrationSchedule.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .intervalDays(30)
                .nextDueAt(null)
                .active(true)
                .createdBy("supervisor1")
                .build();

        when(scheduleRepository.findByActiveTrueAndNextDueAtBetween(any(), any()))
                .thenReturn(List.of(noDate));
        when(scheduleRepository.findByActiveTrueAndNextDueAtBefore(any()))
                .thenReturn(List.of());

        int sent = useCase.execute();

        assertThat(sent).isZero();
        verify(notificationService, never()).createForUserWithDebounce(any(), any(), any(), any(), anyInt());
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
