package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import com.industrialhub.backend.maintenance.application.dto.AddWorkOrderPartRequest;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderPartResponse;
import com.industrialhub.backend.maintenance.application.usecase.AddWorkOrderPartUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.InactiveSparePartException;
import com.industrialhub.backend.maintenance.domain.InsufficientStockException;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.SparePartNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderPart;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderPartRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddWorkOrderPartUseCaseTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private SparePartRepository sparePartRepository;
    @Mock private WorkOrderPartRepository workOrderPartRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;

    private AddWorkOrderPartUseCase useCase;

    private UUID workOrderId;
    private UUID sparePartId;
    private WorkOrder workOrder;
    private SparePart sparePart;

    @BeforeEach
    void setUp() {
        useCase = new AddWorkOrderPartUseCase(
                workOrderRepository, sparePartRepository, workOrderPartRepository,
                auditService, notificationRepository, notificationService);

        workOrderId = UUID.randomUUID();
        sparePartId = UUID.randomUUID();

        Equipment equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Máquina A")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .build();

        workOrder = WorkOrder.builder()
                .id(workOrderId)
                .equipment(equipment)
                .type(WorkOrderType.CORRECTIVE)
                .priority(WorkOrderPriority.HIGH)
                .status(WorkOrderStatus.OPEN)
                .openedAt(LocalDateTime.now())
                .openedBy("op1")
                .build();

        sparePart = SparePart.builder()
                .id(sparePartId)
                .code("PART-001")
                .name("Rolamento")
                .unit("un")
                .stockQty(10)
                .minStockQty(2)
                .active(true)
                .build();
    }

    @Test
    void shouldAddPartToWorkOrderAndDeductStock() {
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.of(sparePart));

        WorkOrderPart savedWop = WorkOrderPart.builder()
                .id(UUID.randomUUID())
                .workOrder(workOrder)
                .sparePart(sparePart)
                .quantity(3)
                .addedBy("supervisor")
                .addedAt(LocalDateTime.now())
                .build();
        when(workOrderPartRepository.save(any())).thenReturn(savedWop);

        AddWorkOrderPartRequest request = new AddWorkOrderPartRequest(sparePartId, 3);
        WorkOrderPartResponse response = useCase.execute(workOrderId, request, "supervisor");

        assertThat(response.quantity()).isEqualTo(3);
        assertThat(sparePart.getStockQty()).isEqualTo(7); // 10 - 3
        verify(sparePartRepository).save(sparePart);
        verify(auditService).log(eq("supervisor"), any(), eq("WorkOrderPart"), any(String.class), any());
    }

    @Test
    void shouldThrowWhenWorkOrderNotFound() {
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.empty());

        AddWorkOrderPartRequest request = new AddWorkOrderPartRequest(sparePartId, 2);
        assertThatThrownBy(() -> useCase.execute(workOrderId, request, "supervisor"))
                .isInstanceOf(WorkOrderNotFoundException.class);
    }

    @Test
    void shouldThrowWhenInsufficientStock() {
        sparePart.setStockQty(1);
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.of(sparePart));

        AddWorkOrderPartRequest request = new AddWorkOrderPartRequest(sparePartId, 5);
        assertThatThrownBy(() -> useCase.execute(workOrderId, request, "supervisor"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("disponível 1");
    }

    @Test
    void shouldThrowWhenSparePartInactive() {
        sparePart.setActive(false);
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.of(sparePart));

        AddWorkOrderPartRequest request = new AddWorkOrderPartRequest(sparePartId, 1);
        assertThatThrownBy(() -> useCase.execute(workOrderId, request, "supervisor"))
                .isInstanceOf(InactiveSparePartException.class);
    }

    // AC#7d — peça inexistente → 404
    @Test
    void shouldThrowWhenSparePartNotFound() {
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.empty());

        AddWorkOrderPartRequest request = new AddWorkOrderPartRequest(sparePartId, 2);
        assertThatThrownBy(() -> useCase.execute(workOrderId, request, "supervisor"))
                .isInstanceOf(SparePartNotFoundException.class);
    }

    // US-051 AC#5a — consumo que leva stockQty abaixo do mínimo sem notificação recente → broadcast chamado
    @Test
    void shouldBroadcastLowStockAlertWhenBelowMinAndNoRecentNotification() {
        sparePart.setStockQty(3);   // 3 - 2 = 1, minStockQty = 2 → abaixo
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.of(sparePart));
        when(notificationRepository.existsByTitleAndCreatedAtAfter(any(), any())).thenReturn(false);

        WorkOrderPart savedWop = WorkOrderPart.builder()
                .id(UUID.randomUUID()).workOrder(workOrder).sparePart(sparePart)
                .quantity(2).addedBy("supervisor").addedAt(LocalDateTime.now()).build();
        when(workOrderPartRepository.save(any())).thenReturn(savedWop);

        useCase.execute(workOrderId, new AddWorkOrderPartRequest(sparePartId, 2), "supervisor");

        verify(notificationService).broadcast(contains("Rolamento"), any(), any());
    }

    // US-051 AC#5b — consumo abaixo do mínimo com notificação recente → broadcast NÃO chamado (debounce)
    @Test
    void shouldNotBroadcastWhenDebounceActive() {
        sparePart.setStockQty(3);
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.of(sparePart));
        when(notificationRepository.existsByTitleAndCreatedAtAfter(any(), any())).thenReturn(true);

        WorkOrderPart savedWop = WorkOrderPart.builder()
                .id(UUID.randomUUID()).workOrder(workOrder).sparePart(sparePart)
                .quantity(2).addedBy("supervisor").addedAt(LocalDateTime.now()).build();
        when(workOrderPartRepository.save(any())).thenReturn(savedWop);

        useCase.execute(workOrderId, new AddWorkOrderPartRequest(sparePartId, 2), "supervisor");

        verify(notificationService, never()).broadcast(any(), any(), any());
    }

    // US-051 AC#5c — consumo mantém stockQty >= minStockQty → broadcast não chamado
    @Test
    void shouldNotBroadcastWhenStockRemainsAboveMinimum() {
        sparePart.setStockQty(10);
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(sparePartRepository.findById(sparePartId)).thenReturn(Optional.of(sparePart));

        WorkOrderPart savedWop = WorkOrderPart.builder()
                .id(UUID.randomUUID()).workOrder(workOrder).sparePart(sparePart)
                .quantity(3).addedBy("supervisor").addedAt(LocalDateTime.now()).build();
        when(workOrderPartRepository.save(any())).thenReturn(savedWop);

        useCase.execute(workOrderId, new AddWorkOrderPartRequest(sparePartId, 3), "supervisor");

        // stockQty 10 - 3 = 7 >= minStockQty 2 → no alert
        verify(notificationService, never()).broadcast(any(), any(), any());
    }
}
