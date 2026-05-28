package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.StaffingConfigResponse;
import com.industrialhub.backend.production.domain.StaffingConfig;
import com.industrialhub.backend.production.infrastructure.StaffingConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** ADR-043 Decisão 1 — singleton com bootstrap lazy */
@Service
public class GetStaffingConfigUseCase {

    private final StaffingConfigRepository repository;

    public GetStaffingConfigUseCase(StaffingConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StaffingConfigResponse execute() {
        StaffingConfig config = getOrCreate();
        return StaffingConfigResponse.from(config);
    }

    public StaffingConfig getOrCreate() {
        return repository.findFirst().orElseGet(() -> {
            StaffingConfig defaults = new StaffingConfig();
            defaults.setShiftHours(8);
            defaults.setShiftsPerDay(1);
            defaults.setUpdatedAt(LocalDateTime.now());
            defaults.setUpdatedBy("system");
            return repository.save(defaults);
        });
    }
}
