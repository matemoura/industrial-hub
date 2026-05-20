package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.SparePart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SparePartRepository extends JpaRepository<SparePart, UUID> {

    @Query("""
        SELECT p FROM SparePart p
        WHERE p.active = true
          AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
          AND (:belowMin = false OR p.stockQty < p.minStockQty)
        ORDER BY p.name ASC
    """)
    List<SparePart> findActiveWithFilters(
        @Param("category") String category,
        @Param("belowMin") boolean belowMin
    );
}
