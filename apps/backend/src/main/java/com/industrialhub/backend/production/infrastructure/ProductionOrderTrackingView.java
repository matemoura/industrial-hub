package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.ProductionOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Interface projection for production order tracking kanban view.
 * Loads only the fields required by the tracking UI — avoids full entity load.
 * The {@code overdue} flag is computed in the use case layer after fetching.
 * ADR-041 Decisão 6.
 */
public interface ProductionOrderTrackingView {

    String getId();

    String getDynamicsOrderNumber();

    String getProductName();

    String getProductFamilyName();

    ProductionOrderStatus getStatus();

    BigDecimal getPlannedQty();

    BigDecimal getProducedQty();

    LocalDate getDueDate();
}
