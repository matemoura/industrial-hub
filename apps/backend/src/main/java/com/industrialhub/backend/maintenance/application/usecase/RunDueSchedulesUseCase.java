package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RunDueSchedulesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunDueSchedulesUseCase.class);

    private final MaintenanceScheduleRepository scheduleRepository;
    private final SchedulePlanProcessorService planProcessorService;

    public RunDueSchedulesUseCase(MaintenanceScheduleRepository scheduleRepository,
                                   SchedulePlanProcessorService planProcessorService) {
        this.scheduleRepository = scheduleRepository;
        this.planProcessorService = planProcessorService;
    }

    /**
     * Processa todos os planos ativos com nextRunAt <= hoje.
     * Cada plano é processado em transação independente via {@link SchedulePlanProcessorService#processOne},
     * garantindo que uma falha em um plano não reverta OSs já criadas com sucesso.
     *
     * @return número de OSs criadas com sucesso
     */
    public int execute() {
        LocalDate today = LocalDate.now();
        List<MaintenanceSchedule> due = scheduleRepository.findDueSchedules(today);

        int created = 0;
        for (MaintenanceSchedule schedule : due) {
            try {
                planProcessorService.processOne(schedule, today);
                created++;
            } catch (Exception e) {
                log.error("Erro ao processar plano de manutenção id={}: {}",
                        schedule.getId(), e.getMessage(), e);
            }
        }

        return created;
    }
}
