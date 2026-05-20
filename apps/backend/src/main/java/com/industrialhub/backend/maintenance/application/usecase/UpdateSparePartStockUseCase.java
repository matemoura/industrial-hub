package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import com.industrialhub.backend.maintenance.application.dto.SparePartResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateSparePartStockRequest;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.SparePartNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateSparePartStockUseCase {

    private final SparePartRepository sparePartRepository;
    private final AuditService auditService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public UpdateSparePartStockUseCase(SparePartRepository sparePartRepository,
                                        AuditService auditService,
                                        NotificationRepository notificationRepository,
                                        NotificationService notificationService) {
        this.sparePartRepository = sparePartRepository;
        this.auditService = auditService;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public SparePartResponse execute(UUID id, UpdateSparePartStockRequest request, String username) {
        SparePart part = sparePartRepository.findById(id)
                .orElseThrow(() -> new SparePartNotFoundException(id));

        int newStock = part.getStockQty() + request.quantity();
        if (newStock < 0) {
            throw new IllegalArgumentException(
                "Estoque resultante seria negativo: atual " + part.getStockQty() + ", ajuste " + request.quantity());
        }
        part.setStockQty(newStock);
        SparePart saved = sparePartRepository.save(part);

        auditService.log(username, AuditAction.PART_STOCK_ADJUSTED, "SparePart", id.toString(),
            Map.of("delta", String.valueOf(request.quantity()), "reason", request.reason()));

        // Low stock alert with 24h debounce
        if (saved.getMinStockQty() != null && saved.getStockQty() < saved.getMinStockQty()) {
            String title = "Estoque baixo: " + saved.getName();
            if (!notificationRepository.existsByTitleAndCreatedAtAfter(title, LocalDateTime.now().minusHours(24))) {
                notificationService.broadcast(title,
                    "Estoque atual: " + saved.getStockQty() + " " + saved.getUnit()
                        + " (mínimo: " + saved.getMinStockQty() + ")",
                    NotificationSeverity.WARNING);
            }
        }

        return SparePartResponse.from(saved);
    }
}
