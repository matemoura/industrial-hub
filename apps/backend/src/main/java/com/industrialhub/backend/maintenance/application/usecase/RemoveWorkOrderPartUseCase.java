package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.WorkOrderPart;
import com.industrialhub.backend.maintenance.domain.WorkOrderPartNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderPartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class RemoveWorkOrderPartUseCase {

    private final WorkOrderPartRepository workOrderPartRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditService auditService;

    public RemoveWorkOrderPartUseCase(WorkOrderPartRepository workOrderPartRepository,
                                       SparePartRepository sparePartRepository,
                                       AuditService auditService) {
        this.workOrderPartRepository = workOrderPartRepository;
        this.sparePartRepository = sparePartRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID workOrderId, UUID partId, String removedBy) {
        UUID actualWorkOrderId = workOrderPartRepository.findWorkOrderIdByPartId(partId)
                .orElseThrow(() -> new WorkOrderPartNotFoundException(partId));
        if (!actualWorkOrderId.equals(workOrderId)) {
            throw new WorkOrderPartNotFoundException(partId);
        }
        WorkOrderPart wop = workOrderPartRepository.findById(partId).orElseThrow();

        SparePart part = wop.getSparePart();
        part.setStockQty(part.getStockQty() + wop.getQuantity());
        sparePartRepository.save(part);

        workOrderPartRepository.delete(wop);

        auditService.log(removedBy, AuditAction.PART_CONSUMPTION_REMOVED, "WorkOrderPart", partId.toString(),
            Map.of("workOrderId", workOrderId.toString(),
                   "sparePartId", part.getId().toString(),
                   "quantity", String.valueOf(wop.getQuantity())));
    }
}
