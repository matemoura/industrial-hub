package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.webhook.EquipmentWebhookPayload;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.maintenance.application.usecase.DeleteEquipmentUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentHasOpenOrdersException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteEquipmentUseCaseTest {

    @Mock private EquipmentRepository equipmentRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private AuditService auditService;
    @Mock private WebhookDispatchService webhookDispatchService;

    private DeleteEquipmentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteEquipmentUseCase(
                equipmentRepository, workOrderRepository, auditService, webhookDispatchService);
    }

    private Equipment buildEquipment(UUID id) {
        return Equipment.builder()
                .id(id)
                .code("EQP-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();
    }

    @Test
    void execute_softDeletesEquipment_andDispatchesEquipmentDecommissionedEvent() {
        UUID id = UUID.randomUUID();
        Equipment equipment = buildEquipment(id);

        when(equipmentRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsByEquipmentIdAndStatusIn(
                eq(id), eq(List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS))))
                .thenReturn(false);
        when(equipmentRepository.save(any())).thenReturn(equipment);

        useCase.execute(id, "admin");

        assertThat(equipment.isActive()).isFalse();
        verify(equipmentRepository).save(equipment);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(webhookDispatchService).dispatch(
                eq(WebhookEvent.EQUIPMENT_DECOMMISSIONED),
                payloadCaptor.capture());

        EquipmentWebhookPayload payload = (EquipmentWebhookPayload) payloadCaptor.getValue();
        assertThat(payload.equipmentId()).isEqualTo(id);
        assertThat(payload.equipmentCode()).isEqualTo("EQP-001");
    }

    @Test
    void execute_withOpenOrders_throwsAndDoesNotDispatchWebhook() {
        UUID id = UUID.randomUUID();
        Equipment equipment = buildEquipment(id);

        when(equipmentRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsByEquipmentIdAndStatusIn(
                eq(id), eq(List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS))))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
                .isInstanceOf(EquipmentHasOpenOrdersException.class);

        verify(webhookDispatchService, never()).dispatch(any(), any());
    }
}
