package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import com.industrialhub.backend.maintenance.application.dto.AddWorkOrderPartRequest;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderPartResponse;
import com.industrialhub.backend.maintenance.domain.InactiveSparePartException;
import com.industrialhub.backend.maintenance.domain.InsufficientStockException;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.SparePartNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderPart;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderPartRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AddWorkOrderPartUseCase {

    private final WorkOrderRepository workOrderRepository;
    private final SparePartRepository sparePartRepository;
    private final WorkOrderPartRepository workOrderPartRepository;
    private final AuditService auditService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public AddWorkOrderPartUseCase(WorkOrderRepository workOrderRepository,
                                    SparePartRepository sparePartRepository,
                                    WorkOrderPartRepository workOrderPartRepository,
                                    AuditService auditService,
                                    NotificationRepository notificationRepository,
                                    NotificationService notificationService) {
        this.workOrderRepository = workOrderRepository;
        this.sparePartRepository = sparePartRepository;
        this.workOrderPartRepository = workOrderPartRepository;
        this.auditService = auditService;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public WorkOrderPartResponse execute(UUID workOrderId, AddWorkOrderPartRequest request, String addedBy) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new WorkOrderNotFoundException(workOrderId));

        SparePart sparePart = sparePartRepository.findById(request.sparePartId())
                .orElseThrow(() -> new SparePartNotFoundException(request.sparePartId()));

        if (!sparePart.isActive()) {
            throw new InactiveSparePartException("Peça inativa não pode ser consumida");
        }

        if (sparePart.getStockQty() - request.quantity() < 0) {
            throw new InsufficientStockException(sparePart.getStockQty(), request.quantity());
        }

        sparePart.setStockQty(sparePart.getStockQty() - request.quantity());
        sparePartRepository.save(sparePart);

        WorkOrderPart wop = WorkOrderPart.builder()
                .workOrder(workOrder)
                .sparePart(sparePart)
                .quantity(request.quantity())
                .addedBy(addedBy)
                .addedAt(LocalDateTime.now())
                .build();

        WorkOrderPart saved = workOrderPartRepository.save(wop);

        auditService.log(addedBy, AuditAction.PART_CONSUMED, "WorkOrderPart", saved.getId().toString(),
            Map.of("workOrderId", workOrderId.toString(),
                   "sparePartId", request.sparePartId().toString(),
                   "quantity", String.valueOf(request.quantity())));

        // Low stock alert with 24h debounce
        if (sparePart.getMinStockQty() != null && sparePart.getStockQty() < sparePart.getMinStockQty()) {
            String title = "Estoque baixo: " + sparePart.getName();
            if (!notificationRepository.existsByTitleAndCreatedAtAfter(title, LocalDateTime.now().minusHours(24))) {
                notificationService.broadcast(title,
                    "Estoque atual: " + sparePart.getStockQty() + " " + sparePart.getUnit()
                        + " (mínimo: " + sparePart.getMinStockQty() + ")",
                    NotificationSeverity.WARNING);
            }
        }

        return WorkOrderPartResponse.from(saved);
    }
}
