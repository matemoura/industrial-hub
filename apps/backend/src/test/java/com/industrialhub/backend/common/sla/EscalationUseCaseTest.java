package com.industrialhub.backend.common.sla;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.application.dto.EscalationRunResponse;
import com.industrialhub.backend.common.application.usecase.EscalationUseCase;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationUseCaseTest {

    @Mock SlaRuleRepository slaRuleRepository;
    @Mock NonConformanceRepository ncRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;
    @InjectMocks EscalationUseCase useCase;

    private SlaRule ncCriticalRule48h() {
        return SlaRule.builder()
            .id(UUID.randomUUID())
            .entityType(SlaEntityType.NC)
            .classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("CRITICAL")
            .slaHours(48)
            .escalateByEmail(false)
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private NonConformance openCriticalNcOlderThan50h() {
        return NonConformance.builder()
            .id(UUID.randomUUID())
            .title("NC crítica")
            .type(NcType.PROCESS)
            .severity(NcSeverity.CRITICAL)
            .status(NcStatus.OPEN)
            .reportedBy("operator")
            .reportedAt(LocalDateTime.now().minusHours(50))
            .slaBreached(false)
            .build();
    }

    // (a) NC CRITICAL com reportedAt há 50h e regra 48h → slaBreached=true + notificação + auditoria
    @Test
    void execute_ncCriticalBreached_marksSlaBreachedAndNotifies() {
        SlaRule rule = ncCriticalRule48h();
        NonConformance nc = openCriticalNcOlderThan50h();

        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(ncRepository.findBreachCandidates(any())).thenReturn(List.of(nc));

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedNcs()).isEqualTo(1);
        assertThat(result.breachedWorkOrders()).isEqualTo(0);
        assertThat(nc.isSlaBreached()).isTrue();
        assertThat(nc.getSlaBreachedAt()).isNotNull();
        verify(ncRepository, never()).save(any()); // dirty-checking handles flush via @Transactional
        verify(notificationService).broadcast(anyString(), anyString(), eq(NotificationSeverity.CRITICAL));
        verify(auditService).log(eq("system"), any(), eq("NonConformance"), any(String.class), any());
    }

    // (b) NC CRITICAL já com slaBreached=true → não reprocessada
    @Test
    void execute_ncAlreadyBreached_notReprocessed() {
        SlaRule rule = ncCriticalRule48h();
        NonConformance nc = openCriticalNcOlderThan50h();
        nc.setSlaBreached(true); // already processed — filtered by query

        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        // Repository returns empty because slaBreached=false filter excludes it
        when(ncRepository.findBreachCandidates(any())).thenReturn(List.of());

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedNcs()).isEqualTo(0);
        verify(ncRepository, never()).save(any());
        verify(notificationService, never()).broadcast(any(), any(), any());
    }

    // (c) NC CLOSED → ignorada mesmo com prazo vencido (query não retorna CLOSED)
    @Test
    void execute_closedNc_ignored() {
        SlaRule rule = ncCriticalRule48h();

        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        // Query already filters out CLOSED — returns empty
        when(ncRepository.findBreachCandidates(any())).thenReturn(List.of());

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedNcs()).isEqualTo(0);
        verify(ncRepository, never()).save(any());
    }

    // (d) NC MEDIUM com regra CRITICAL → não marcada (classifierValue doesn't match)
    @Test
    void execute_ncMediumWithCriticalRule_notBreached() {
        SlaRule rule = ncCriticalRule48h();
        NonConformance nc = NonConformance.builder()
            .id(UUID.randomUUID()).title("NC média")
            .type(NcType.PROCESS).severity(NcSeverity.MEDIUM)
            .status(NcStatus.OPEN).reportedBy("operator")
            .reportedAt(LocalDateTime.now().minusHours(60))
            .slaBreached(false).build();

        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(ncRepository.findBreachCandidates(any())).thenReturn(List.of(nc));

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedNcs()).isEqualTo(0);
        verify(ncRepository, never()).save(any());
    }

    // (e) WorkOrder URGENT com regra 4h → marcada
    @Test
    void execute_urgentWorkOrderBreached_marksBreached() {
        SlaRule rule = SlaRule.builder()
            .id(UUID.randomUUID())
            .entityType(SlaEntityType.WORK_ORDER)
            .classifierField(SlaClassifierField.PRIORITY)
            .classifierValue("URGENT")
            .slaHours(4)
            .escalateByEmail(false)
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();

        WorkOrder wo = WorkOrder.builder()
            .id(UUID.randomUUID()).title("OS urgente")
            .type(WorkOrderType.CORRECTIVE).priority(WorkOrderPriority.URGENT)
            .status(WorkOrderStatus.OPEN).openedBy("supervisor")
            .openedAt(LocalDateTime.now().minusHours(5))
            .slaBreached(false)
            .equipment(Equipment.builder().id(UUID.randomUUID()).code("EQ-01").name("Machine")
                .type(com.industrialhub.backend.maintenance.domain.EquipmentType.MACHINE)
                .status(com.industrialhub.backend.maintenance.domain.EquipmentStatus.OPERATIONAL).build())
            .build();

        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(workOrderRepository.findBreachCandidates(any())).thenReturn(List.of(wo));

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedWorkOrders()).isEqualTo(1);
        assertThat(result.breachedNcs()).isEqualTo(0);
        assertThat(wo.isSlaBreached()).isTrue();
        assertThat(wo.getSlaBreachedAt()).isNotNull();
        verify(workOrderRepository, never()).save(any()); // dirty-checking handles flush via @Transactional
        verify(notificationService).broadcast(anyString(), anyString(), eq(NotificationSeverity.CRITICAL));
    }

    // (f) EscalationRunResponse com contagens corretas — múltiplas regras
    @Test
    void execute_multipleRules_correctCounts() {
        SlaRule ncRule = ncCriticalRule48h();
        SlaRule woRule = SlaRule.builder()
            .id(UUID.randomUUID()).entityType(SlaEntityType.WORK_ORDER)
            .classifierField(SlaClassifierField.PRIORITY).classifierValue("URGENT")
            .slaHours(4).escalateByEmail(false).active(true).createdAt(LocalDateTime.now()).build();

        NonConformance nc1 = openCriticalNcOlderThan50h();
        NonConformance nc2 = openCriticalNcOlderThan50h();

        WorkOrder wo1 = WorkOrder.builder()
            .id(UUID.randomUUID()).title("OS 1").type(WorkOrderType.CORRECTIVE)
            .priority(WorkOrderPriority.URGENT).status(WorkOrderStatus.OPEN)
            .openedBy("sup").openedAt(LocalDateTime.now().minusHours(5))
            .slaBreached(false)
            .equipment(Equipment.builder().id(UUID.randomUUID()).code("EQ-01").name("Machine")
                .type(com.industrialhub.backend.maintenance.domain.EquipmentType.MACHINE)
                .status(com.industrialhub.backend.maintenance.domain.EquipmentStatus.OPERATIONAL).build())
            .build();

        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of(ncRule, woRule));
        when(ncRepository.findBreachCandidates(any())).thenReturn(List.of(nc1, nc2));
        when(workOrderRepository.findBreachCandidates(any())).thenReturn(List.of(wo1));

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedNcs()).isEqualTo(2);
        assertThat(result.breachedWorkOrders()).isEqualTo(1);
    }

    // (g) No active rules → no breaches
    @Test
    void execute_noActiveRules_returnsZeros() {
        when(slaRuleRepository.findByActiveTrue()).thenReturn(List.of());

        EscalationRunResponse result = useCase.execute();

        assertThat(result.breachedNcs()).isEqualTo(0);
        assertThat(result.breachedWorkOrders()).isEqualTo(0);
        verifyNoInteractions(ncRepository, workOrderRepository, notificationService);
    }
}
