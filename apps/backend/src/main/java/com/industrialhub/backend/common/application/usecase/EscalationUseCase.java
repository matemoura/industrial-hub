package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.EmailEscalationService;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.application.dto.EscalationRunResponse;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class EscalationUseCase {

    private static final Logger log = LoggerFactory.getLogger(EscalationUseCase.class);

    private final SlaRuleRepository slaRuleRepository;
    private final NonConformanceRepository ncRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final EmailEscalationService emailEscalationService;

    public EscalationRunResponse execute(String triggeredBy) {
        List<SlaRule> rules = slaRuleRepository.findByActiveTrue();
        int breachedNcs = 0;
        int breachedWorkOrders = 0;
        LocalDateTime now = LocalDateTime.now();

        for (SlaRule rule : rules) {
            LocalDateTime deadline = now.minusHours(rule.getSlaHours());

            if (rule.getEntityType() == SlaEntityType.NC) {
                // fetch all breach candidates (status != CLOSED, slaBreached = false, old enough)
                List<NonConformance> candidates = ncRepository.findBreachCandidates(deadline);
                // filter by classifierValue in Java (classifierField = SEVERITY)
                for (NonConformance nc : candidates) {
                    String entityValue = switch (rule.getClassifierField()) {
                        case SEVERITY -> nc.getSeverity().name();
                        default -> null; // NC does not have other classifier fields
                    };
                    if (entityValue == null || !entityValue.equalsIgnoreCase(rule.getClassifierValue())) {
                        continue;
                    }
                    nc.setSlaBreached(true);
                    nc.setSlaBreachedAt(now);
                    breachedNcs++;

                    String msg = String.format("SLA vencido: NC '%s' ultrapassou %dh",
                        nc.getTitle(), rule.getSlaHours());
                    notificationService.broadcast("SLA Vencido: NC #" + nc.getId(), msg,
                        NotificationSeverity.CRITICAL);

                    auditService.log(triggeredBy, AuditAction.SLA_BREACHED, "NonConformance",
                        nc.getId().toString(),
                        Map.of("slaHours", rule.getSlaHours(),
                               "classifierValue", rule.getClassifierValue()));

                    if (rule.isEscalateByEmail()) {
                        emailEscalationService.notifySlaBreached(
                            "NC", nc.getId().toString(), nc.getTitle(), rule.getSlaHours());
                    }
                }
            } else if (rule.getEntityType() == SlaEntityType.WORK_ORDER) {
                List<WorkOrder> candidates = workOrderRepository.findBreachCandidates(deadline);
                for (WorkOrder wo : candidates) {
                    String entityValue = switch (rule.getClassifierField()) {
                        case PRIORITY -> wo.getPriority().name();
                        default -> null; // WorkOrder does not have other classifier fields
                    };
                    if (entityValue == null || !entityValue.equalsIgnoreCase(rule.getClassifierValue())) {
                        continue;
                    }
                    wo.setSlaBreached(true);
                    wo.setSlaBreachedAt(now);
                    breachedWorkOrders++;

                    String msg = String.format("SLA vencido: OS '%s' ultrapassou %dh",
                        wo.getTitle(), rule.getSlaHours());
                    notificationService.broadcast("SLA Vencido: OS #" + wo.getId(), msg,
                        NotificationSeverity.CRITICAL);

                    auditService.log(triggeredBy, AuditAction.SLA_BREACHED, "WorkOrder",
                        wo.getId().toString(),
                        Map.of("slaHours", rule.getSlaHours(),
                               "classifierValue", rule.getClassifierValue()));

                    if (rule.isEscalateByEmail()) {
                        emailEscalationService.notifySlaBreached(
                            "OS", wo.getId().toString(), wo.getTitle(), rule.getSlaHours());
                    }
                }
            }
        }

        log.info("EscalationUseCase concluído: {} NCs e {} OSs marcadas", breachedNcs, breachedWorkOrders);
        return new EscalationRunResponse(breachedNcs, breachedWorkOrders);
    }
}
