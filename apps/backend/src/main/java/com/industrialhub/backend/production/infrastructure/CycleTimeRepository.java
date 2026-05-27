package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.CycleTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CycleTimeRepository extends JpaRepository<CycleTime, UUID> {

    Optional<CycleTime> findByProductIdAndEffectiveDate(UUID productId, LocalDate effectiveDate);

    Optional<CycleTime> findTopByProductIdOrderByEffectiveDateDesc(UUID productId);

    @Query("SELECT c FROM CycleTime c WHERE c.product.id = :productId ORDER BY c.effectiveDate DESC")
    List<CycleTime> findByProductIdOrderByEffectiveDateDesc(@Param("productId") UUID productId);
}
