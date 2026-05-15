package com.industrialhub.backend.common.kpi;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.common.kpi.application.WeeklyReportService;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TimeRecordRepository timeRecordRepository;
    @Mock private NonConformanceRepository nonConformanceRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private OeeCalculator oeeCalculator;
    @Mock private JavaMailSender mailSender;

    private WeeklyReportService service;

    @BeforeEach
    void setUp() {
        service = new WeeklyReportService(
                userRepository, timeRecordRepository,
                nonConformanceRepository, workOrderRepository, oeeCalculator);
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@test.com");
        stubEmptyCollectData();
    }

    // ── mail desabilitado ────────────────────────────────────────────────────

    @Test
    void shouldNotSendEmails_whenMailDisabled() {
        // mailEnabled = false (default via @Value fallback, not set in test)
        service.send();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldNotSendEmails_whenMailSenderIsNull() {
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        // mailSender permanece null (não injetado via ReflectionTestUtils)

        service.send();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ── mail habilitado ──────────────────────────────────────────────────────

    @Test
    void shouldSendEmailToAllSupervisorsAndAdmins() {
        enableMail();
        when(userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)))
                .thenReturn(List.of(
                        buildUser("sup@msb.com", Role.SUPERVISOR),
                        buildUser("adm@msb.com", Role.ADMIN)));

        service.send();

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldSkipRecipients_withNullOrBlankEmail() {
        enableMail();
        when(userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)))
                .thenReturn(List.of(
                        buildUser(null, Role.SUPERVISOR),
                        buildUser("  ", Role.SUPERVISOR),
                        buildUser("valid@msb.com", Role.ADMIN)));

        service.send();

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldContinueSending_whenOneEmailFails() {
        enableMail();
        when(userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)))
                .thenReturn(List.of(
                        buildUser("sup1@msb.com", Role.SUPERVISOR),
                        buildUser("sup2@msb.com", Role.SUPERVISOR)));
        doThrow(new RuntimeException("SMTP timeout"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        // não deve propagar exceção
        service.send();

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldIncludeNaFormatting_whenNoData() {
        enableMail();
        when(userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)))
                .thenReturn(List.of(buildUser("adm@msb.com", Role.ADMIN)));

        service.send();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();
        assertThat(body).contains("OEE médio (7 dias): N/A")
                        .contains("MTTR da semana: N/A");
    }

    @Test
    void shouldIncludeFormattedValues_whenDataAvailable() {
        // OEE 87.6% e MTTR 2.5h
        when(oeeCalculator.computeAvg(any())).thenReturn(0.876);
        when(workOrderRepository.findCompletedCorrectiveInPeriod(any(), any()))
                .thenReturn(List.of(buildWorkOrder(LocalDateTime.now().minusHours(2), LocalDateTime.now())));
        enableMail();
        when(userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)))
                .thenReturn(List.of(buildUser("adm@msb.com", Role.ADMIN)));

        service.send();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();
        assertThat(body).contains("87.6%").contains("2.0 h");
    }

    // ── MTTR usa findCompletedCorrectiveInPeriod (não o histórico completo) ──

    @Test
    void shouldQueryMttrByPeriod_notFullHistory() {
        enableMail();
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        service.send();

        // SH-18: deve chamar a query com filtro de data, nunca findCompletedCorrectiveForMetrics
        verify(workOrderRepository).findCompletedCorrectiveInPeriod(any(), any());
        verify(workOrderRepository, never()).findCompletedCorrectiveForMetrics(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void enableMail() {
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
    }

    private void stubEmptyCollectData() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), any()))
                .thenReturn(List.of());
        when(oeeCalculator.computeAvg(any())).thenReturn(null);
        when(nonConformanceRepository.countInPeriod(any(), any())).thenReturn(0L);
        when(nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL)).thenReturn(0L);
        when(workOrderRepository.countOpenByEquipmentId(null)).thenReturn(0L);
        when(workOrderRepository.findCompletedCorrectiveInPeriod(any(), any()))
                .thenReturn(List.of());
    }

    private User buildUser(String email, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .username("user_" + UUID.randomUUID().toString().substring(0, 4))
                .password("hash")
                .email(email)
                .role(role)
                .build();
    }

    private WorkOrder buildWorkOrder(LocalDateTime startedAt, LocalDateTime closedAt) {
        return WorkOrder.builder()
                .id(UUID.randomUUID())
                .equipment(Equipment.builder().id(UUID.randomUUID()).build())
                .type(WorkOrderType.CORRECTIVE).priority(WorkOrderPriority.HIGH)
                .title("Falha").status(WorkOrderStatus.DONE)
                .openedBy("op").openedAt(startedAt)
                .startedAt(startedAt).closedAt(closedAt)
                .build();
    }
}
