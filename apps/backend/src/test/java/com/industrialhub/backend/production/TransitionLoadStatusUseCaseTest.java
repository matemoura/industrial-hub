package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.production.application.usecase.TransitionLoadStatusUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import com.industrialhub.backend.common.domain.AuditAction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransitionLoadStatusUseCaseTest {

    @Mock SterilizationLoadRepository loadRepository;
    @Mock ProductionOrderRepository orderRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock AuditService auditService;
    @InjectMocks TransitionLoadStatusUseCase useCase;

    private SterilizationLoad loadWithStatus(LoadStatus status) {
        SterilizationLoad load = new SterilizationLoad();
        load.setId(UUID.randomUUID());
        load.setLoadNumber("CARGA-2026-001");
        load.setStatus(status);
        return load;
    }

    @Test
    void openToClosedSetsTimestampAndEquipmentUnderMaintenance() {
        Equipment eq = new Equipment();
        eq.setStatus(EquipmentStatus.OPERATIONAL);
        SterilizationLoad load = loadWithStatus(LoadStatus.OPEN);
        load.setSterilizer(eq);

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(loadRepository.save(any())).thenReturn(load);

        useCase.execute(load.getId(), LoadStatus.CLOSED, "supervisor1");

        assertThat(load.getClosedAt()).isNotNull();
        assertThat(eq.getStatus()).isEqualTo(EquipmentStatus.UNDER_MAINTENANCE);
        verify(equipmentRepository).save(eq);
    }

    @Test
    void sterilizingToReleasedSetsTimestampAndRestoresEquipment() {
        Equipment eq = new Equipment();
        eq.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        SterilizationLoad load = loadWithStatus(LoadStatus.STERILIZING);
        load.setSterilizer(eq);

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(loadRepository.save(any())).thenReturn(load);

        useCase.execute(load.getId(), LoadStatus.RELEASED, "supervisor1");

        assertThat(load.getReleasedAt()).isNotNull();
        assertThat(eq.getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        verify(equipmentRepository).save(eq);
    }

    @Test
    void sterilizingToRejectedClearsAllOrdersInLoad() {
        SterilizationLoad load = loadWithStatus(LoadStatus.STERILIZING);

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(loadRepository.save(any())).thenReturn(load);

        useCase.execute(load.getId(), LoadStatus.REJECTED, "supervisor1");

        verify(orderRepository).clearLoadForAllOrdersInLoad(load.getId());
        assertThat(load.getStatus()).isEqualTo(LoadStatus.REJECTED);
    }

    @Test
    void closedToSterilizingHasNoEquipmentSideEffect() {
        SterilizationLoad load = loadWithStatus(LoadStatus.CLOSED);

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(loadRepository.save(any())).thenReturn(load);

        useCase.execute(load.getId(), LoadStatus.STERILIZING, "supervisor1");

        verifyNoInteractions(equipmentRepository);
        assertThat(load.getStatus()).isEqualTo(LoadStatus.STERILIZING);
    }

    @Test
    void invalidTransitionThrows422Exception() {
        SterilizationLoad load = loadWithStatus(LoadStatus.OPEN);
        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));

        assertThatThrownBy(() -> useCase.execute(load.getId(), LoadStatus.RELEASED, "supervisor1"))
                .isInstanceOf(InvalidLoadTransitionException.class)
                .hasMessageContaining("OPEN")
                .hasMessageContaining("RELEASED");
    }

    @Test
    void auditLogRecordsOriginalStatusAsFrom() {
        // Regression for BUG-1: load.getStatus() was called AFTER setStatus(), logging target as "from"
        SterilizationLoad load = loadWithStatus(LoadStatus.OPEN);
        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(loadRepository.save(any())).thenReturn(load);

        useCase.execute(load.getId(), LoadStatus.CLOSED, "supervisor1");

        verify(auditService).log(
                eq("supervisor1"),
                eq(AuditAction.STERILIZATION_LOAD_STATUS_CHANGED),
                eq("SterilizationLoad"),
                eq(load.getId().toString()),
                argThat(map -> "OPEN".equals(map.get("from")) && "CLOSED".equals(map.get("to")))
        );
    }
}
