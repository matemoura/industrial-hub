package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.TimelineEntryResponse;
import com.industrialhub.backend.production.domain.MrpPlannedOrder;
import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** ADR-030 Decisão 7 — timeline combinando OPs Dynamics + sugestões MRP ativas */
@Service
public class GetPlanningTimelineUseCase {

    private final ProductionOrderRepository productionOrderRepository;
    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;

    public GetPlanningTimelineUseCase(
            ProductionOrderRepository productionOrderRepository,
            MrpPlannedOrderRepository mrpPlannedOrderRepository) {
        this.productionOrderRepository = productionOrderRepository;
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
    }

    public List<TimelineEntryResponse> execute(String familyCode, int weeks) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusWeeks(Math.min(weeks, 26));

        List<TimelineEntryResponse> entries = new ArrayList<>();

        // OPs Dynamics abertas da família dentro do horizonte
        List<ProductionOrder> orders = productionOrderRepository.findOpenOrdersByFamily(familyCode);
        for (ProductionOrder order : orders) {
            if (order.getDueDate() == null) continue;
            if (order.getDueDate().isAfter(horizon)) continue;

            LocalDate start = order.getStartDate() != null ? order.getStartDate() : today;
            int qty = order.getPlannedQty() != null ? order.getPlannedQty().intValue() : 0;
            boolean overdue = order.getDueDate().isBefore(today);

            entries.add(new TimelineEntryResponse(
                    order.getDynamicsOrderNumber(),
                    order.getProduct().getDynamicsCode(),
                    order.getProduct().getName(),
                    start,
                    order.getDueDate(),
                    qty,
                    statusLabel(order),
                    false,
                    overdue,
                    null  // suggestionId: null para OPs Dynamics
            ));
        }

        // Sugestões MRP ativas da família
        List<MrpPlannedOrder> suggestions = mrpPlannedOrderRepository.findActiveByFamily(familyCode);
        for (MrpPlannedOrder s : suggestions) {
            if (s.getSuggestedDueDate() == null) continue;
            if (s.getSuggestedDueDate().isAfter(horizon)) continue;

            LocalDate start = s.getSuggestedStartDate() != null ? s.getSuggestedStartDate() : today;
            boolean overdue = s.getSuggestedDueDate().isBefore(today);

            entries.add(new TimelineEntryResponse(
                    "MRP-" + s.getId().toString().substring(0, 8),  // display label only
                    s.getProduct().getDynamicsCode(),
                    s.getProduct().getName(),
                    start,
                    s.getSuggestedDueDate(),
                    s.getSuggestedQty(),
                    "Planejado (MRP)",
                    true,
                    overdue,
                    s.getId()  // BUG-2 fix: UUID completo para accept/reject no frontend
            ));
        }

        entries.sort(Comparator.comparing(TimelineEntryResponse::dueDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return entries;
    }

    private String statusLabel(ProductionOrder order) {
        return switch (order.getStatus()) {
            case PLANNED      -> "Planejado";
            case RELEASED     -> "Liberado";
            case IN_PROGRESS  -> "Em Produção";
            default           -> order.getStatus().name();
        };
    }
}
