package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Processa um único plano de manutenção em sua própria transação ({@code REQUIRES_NEW}).
 * Isolado de {@link RunDueSchedulesUseCase} para garantir que uma falha em um plano
 * não reverta as OSs já criadas com sucesso na mesma execução do job.
 */
@Service
public class SchedulePlanProcessorService {

    private static final String SCHEDULER_USER = "scheduler";

    private final WorkOrderRepository workOrderRepository;
    private final MaintenanceScheduleRepository scheduleRepository;

    public SchedulePlanProcessorService(WorkOrderRepository workOrderRepository,
                                        MaintenanceScheduleRepository scheduleRepository) {
        this.workOrderRepository = workOrderRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Cria a OS preventiva e avança {@code nextRunAt} do plano em uma transação independente.
     *
     * @param schedule plano de manutenção a processar
     * @param today    data de referência do job
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(MaintenanceSchedule schedule, LocalDate today) {
        WorkOrder workOrder = WorkOrder.builder()
                .equipment(schedule.getEquipment())
                .schedule(schedule)
                .type(WorkOrderType.PREVENTIVE)
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .priority(schedule.getPriority())
                .status(WorkOrderStatus.OPEN)
                .openedBy(SCHEDULER_USER)
                .openedAt(LocalDateTime.now())
                .build();

        workOrderRepository.save(workOrder);

        schedule.setLastRunAt(today);
        schedule.setNextRunAt(ScheduleRecurrenceHelper.calculateNext(
                today, schedule.getRecurrence(),
                schedule.getDayOfWeek(), schedule.getDayOfMonth()));
        scheduleRepository.save(schedule);
    }
}
