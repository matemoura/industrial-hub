package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {

    Optional<ProductionOrder> findByDynamicsOrderNumber(String dynamicsOrderNumber);

    @Query("SELECT o FROM ProductionOrder o LEFT JOIN o.product p LEFT JOIN o.family f " +
           "WHERE (:familyCode IS NULL OR f.code = :familyCode) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:productType IS NULL OR p.type = :productType) " +
           "AND (:overdueOnly = false OR (o.dueDate < :today AND o.status NOT IN ('DONE', 'CANCELLED')))")
    Page<ProductionOrder> findFiltered(
            @Param("familyCode") String familyCode,
            @Param("status") ProductionOrderStatus status,
            @Param("productType") ProductType productType,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("today") LocalDate today,
            Pageable pageable);
}
