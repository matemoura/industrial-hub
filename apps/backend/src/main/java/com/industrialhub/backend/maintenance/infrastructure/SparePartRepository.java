package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.SparePart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SparePartRepository extends JpaRepository<SparePart, UUID> {

    // native query — CAST(:category AS TEXT) prevents PostgreSQL from inferring bytea
    // for null parameters, which would cause "function lower(bytea) does not exist"
    @Query(value = """
        SELECT * FROM spare_part
        WHERE active = true
          AND (CAST(:category AS TEXT) IS NULL OR LOWER(category) = LOWER(CAST(:category AS TEXT)))
          AND (:belowMin = false OR stock_qty < min_stock_qty)
        ORDER BY name ASC
    """, nativeQuery = true)
    List<SparePart> findActiveWithFilters(
        @Param("category") String category,
        @Param("belowMin") boolean belowMin
    );
}
