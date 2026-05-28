package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.MrpOrderStatus;
import com.industrialhub.backend.production.domain.MrpPlannedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MrpPlannedOrderRepository extends JpaRepository<MrpPlannedOrder, UUID> {

    List<MrpPlannedOrder> findByStatus(MrpOrderStatus status);

    /** ADR-043 Decisão 3 — invalida sugestões pendentes antes de um novo run */
    @Modifying
    @Query("UPDATE MrpPlannedOrder o SET o.status = 'SUPERSEDED' WHERE o.status = 'SUGGESTED'")
    void supersedePendingSuggestions();

    /** Sugestões ativas (SUGGESTED + ACCEPTED) por produto — usadas no cálculo de openOrdersQty */
    @Query("""
        SELECT COALESCE(SUM(o.suggestedQty), 0)
        FROM MrpPlannedOrder o
        WHERE o.product.id = :productId
          AND o.status IN ('SUGGESTED', 'ACCEPTED')
        """)
    int sumActiveSuggestedQtyByProduct(@Param("productId") UUID productId);

    /** Sugestões ativas por família — para timeline */
    @Query("""
        SELECT o FROM MrpPlannedOrder o
        JOIN FETCH o.product p
        LEFT JOIN FETCH o.family f
        WHERE (:familyCode IS NULL OR f.code = :familyCode)
          AND o.status IN ('SUGGESTED', 'ACCEPTED')
        ORDER BY o.suggestedDueDate ASC NULLS LAST
        """)
    List<MrpPlannedOrder> findActiveByFamily(@Param("familyCode") String familyCode);
}
