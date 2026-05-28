package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.production.application.dto.SterilizationLoadResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class TransitionLoadStatusUseCase {

    private final SterilizationLoadRepository loadRepository;
    private final ProductionOrderRepository orderRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditService auditService;

    public TransitionLoadStatusUseCase(SterilizationLoadRepository loadRepository,
                                        ProductionOrderRepository orderRepository,
                                        EquipmentRepository equipmentRepository,
                                        AuditService auditService) {
        this.loadRepository = loadRepository;
        this.orderRepository = orderRepository;
        this.equipmentRepository = equipmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SterilizationLoadResponse execute(UUID loadId, LoadStatus targetStatus, String username) {
        SterilizationLoad load = loadRepository.findById(loadId)
                .orElseThrow(() -> new SterilizationLoadNotFoundException(loadId));

        validateTransition(load.getStatus(), targetStatus);

        switch (targetStatus) {
            case CLOSED -> {
                load.setClosedAt(LocalDateTime.now());
                if (load.getSterilizer() != null) {
                    var eq = load.getSterilizer();
                    eq.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
                    equipmentRepository.save(eq);
                }
            }
            case STERILIZING -> { /* sem efeito colateral */ }
            case RELEASED -> {
                load.setReleasedAt(LocalDateTime.now());
                if (load.getSterilizer() != null) {
                    var eq = load.getSterilizer();
                    eq.setStatus(EquipmentStatus.OPERATIONAL);
                    equipmentRepository.save(eq);
                }
            }
            case REJECTED -> orderRepository.clearLoadForAllOrdersInLoad(loadId);
            default -> throw new InvalidLoadTransitionException(load.getStatus(), targetStatus);
        }

        LoadStatus fromStatus = load.getStatus(); // capture before mutation
        load.setStatus(targetStatus);
        SterilizationLoad saved = loadRepository.save(load);

        auditService.log(username, AuditAction.STERILIZATION_LOAD_STATUS_CHANGED,
                "SterilizationLoad", loadId.toString(),
                Map.of("from", fromStatus.toString(),
                       "to", targetStatus.toString(),
                       "loadNumber", load.getLoadNumber()));

        return SterilizationLoadResponse.from(saved);
    }

    private void validateTransition(LoadStatus current, LoadStatus target) {
        boolean valid = switch (current) {
            case OPEN        -> target == LoadStatus.CLOSED;
            case CLOSED      -> target == LoadStatus.STERILIZING;
            case STERILIZING -> target == LoadStatus.RELEASED || target == LoadStatus.REJECTED;
            default          -> false;
        };
        if (!valid) throw new InvalidLoadTransitionException(current, target);
    }
}
