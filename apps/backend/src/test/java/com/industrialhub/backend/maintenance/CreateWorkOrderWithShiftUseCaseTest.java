package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.usecase.ShiftResolverService;
import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import com.industrialhub.backend.maintenance.application.dto.CreateWorkOrderRequest;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.application.usecase.CreateWorkOrderUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateWorkOrderWithShiftUseCaseTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftResolverService shiftResolverService;

    @Mock
    private AuditService auditService;

    private CreateWorkOrderUseCase useCase;

    private Equipment equipment;

    @BeforeEach
    void setUp() {
        useCase = new CreateWorkOrderUseCase(
                equipmentRepository, workOrderRepository, shiftRepository, shiftResolverService, auditService);

        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();
    }

    // (a) Turno ativo no momento: WorkOrder criada com shift associado
    @Test
    void shouldAssociateActiveShiftToWorkOrder() {
        Shift shift = Shift.builder()
                .id(UUID.randomUUID())
                .name("Turno A")
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(14, 0))
                .overnight(false)
                .active(true)
                .build();

        when(equipmentRepository.findByIdAndActiveTrue(equipment.getId()))
                .thenReturn(Optional.of(equipment));
        when(shiftRepository.findAllByActiveTrueOrderByStartTime()).thenReturn(List.of(shift));
        when(shiftResolverService.resolveCurrentShift(any(), any())).thenReturn(Optional.of(shift));

        WorkOrder savedWorkOrder = WorkOrder.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .type(WorkOrderType.PREVENTIVE)
                .title("Troca de óleo")
                .priority(WorkOrderPriority.MEDIUM)
                .status(WorkOrderStatus.OPEN)
                .openedBy("supervisor")
                .shift(shift)
                .build();
        when(workOrderRepository.save(any())).thenReturn(savedWorkOrder);

        // CreateWorkOrderRequest: equipmentId, type, title, description, priority, assignedTo
        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                equipment.getId(), WorkOrderType.PREVENTIVE, "Troca de óleo", null, WorkOrderPriority.MEDIUM, null);

        WorkOrderResponse response = useCase.execute(request, "supervisor");

        assertThat(response.shiftId()).isEqualTo(shift.getId());
        assertThat(response.shiftName()).isEqualTo("Turno A");

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getShift()).isEqualTo(shift);
    }

    // (b) Sem turno ativo: WorkOrder criada com shift = null, sem exceção
    @Test
    void shouldCreateWorkOrderWithNullShiftWhenNoActiveShift() {
        when(equipmentRepository.findByIdAndActiveTrue(equipment.getId()))
                .thenReturn(Optional.of(equipment));
        when(shiftRepository.findAllByActiveTrueOrderByStartTime()).thenReturn(List.of());
        when(shiftResolverService.resolveCurrentShift(any(), any())).thenReturn(Optional.empty());

        WorkOrder savedWorkOrder = WorkOrder.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .type(WorkOrderType.PREVENTIVE)
                .title("Troca de óleo")
                .priority(WorkOrderPriority.MEDIUM)
                .status(WorkOrderStatus.OPEN)
                .openedBy("supervisor")
                .shift(null)
                .build();
        when(workOrderRepository.save(any())).thenReturn(savedWorkOrder);

        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                equipment.getId(), WorkOrderType.PREVENTIVE, "Troca de óleo", null, WorkOrderPriority.MEDIUM, null);

        WorkOrderResponse response = useCase.execute(request, "supervisor");

        assertThat(response.shiftId()).isNull();
        assertThat(response.shiftName()).isNull();
    }
}
