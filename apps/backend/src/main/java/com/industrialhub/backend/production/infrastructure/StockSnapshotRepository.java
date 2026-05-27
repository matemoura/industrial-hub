package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.StockSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, UUID> {

    Optional<StockSnapshot> findByProductIdAndSnapshotDate(UUID productId, LocalDate snapshotDate);

    Optional<StockSnapshot> findTopByProductIdOrderBySnapshotDateDesc(UUID productId);

    @Query("SELECT s FROM StockSnapshot s " +
           "WHERE (:productId IS NULL OR s.product.id = :productId) " +
           "AND (:from IS NULL OR s.snapshotDate >= :from) " +
           "AND (:to IS NULL OR s.snapshotDate <= :to) " +
           "ORDER BY s.snapshotDate DESC")
    List<StockSnapshot> findFiltered(
            @Param("productId") UUID productId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Returns the latest snapshot per product (one row per product).
     */
    @Query("SELECT s FROM StockSnapshot s " +
           "WHERE s.snapshotDate = (" +
           "  SELECT MAX(s2.snapshotDate) FROM StockSnapshot s2 WHERE s2.product = s.product" +
           ")")
    List<StockSnapshot> findLatestPerProduct();
}
