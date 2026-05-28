package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.StaffingConfigResponse;
import com.industrialhub.backend.production.application.dto.UpdateStaffingConfigRequest;
import com.industrialhub.backend.production.domain.StaffingConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UpdateStaffingConfigUseCase {

    private final GetStaffingConfigUseCase getStaffingConfig;

    public UpdateStaffingConfigUseCase(GetStaffingConfigUseCase getStaffingConfig) {
        this.getStaffingConfig = getStaffingConfig;
    }

    @Transactional
    public StaffingConfigResponse execute(UpdateStaffingConfigRequest request, String username) {
        StaffingConfig config = getStaffingConfig.getOrCreate();
        config.setShiftHours(request.shiftHours());
        config.setShiftsPerDay(request.shiftsPerDay());
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(username);
        // JPA managed entity — save automático no commit da transação
        return StaffingConfigResponse.from(config);
    }
}
