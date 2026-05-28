package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.SterilizationLoad;
import com.industrialhub.backend.production.domain.SterilizationMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface SterilizationLoadRepository extends JpaRepository<SterilizationLoad, UUID> {

    /** ADR-042 Decisão 1 — gera próximo número sequencial do ano */
    @Query("""
        SELECT COALESCE(MAX(CAST(SUBSTRING(sl.loadNumber, 12) AS int)), 0) + 1
        FROM SterilizationLoad sl
        WHERE sl.loadNumber LIKE CONCAT('CARGA-', :year, '-%')
        """)
    int nextSequenceForYear(@Param("year") int year);

    @Query("""
        SELECT sl FROM SterilizationLoad sl
        WHERE (:status IS NULL OR sl.status = :status)
          AND (:method IS NULL OR sl.method = :method)
          AND (:dateFrom IS NULL OR sl.sterilizationDate >= :dateFrom)
          AND (:dateTo   IS NULL OR sl.sterilizationDate <= :dateTo)
        ORDER BY sl.createdAt DESC
        """)
    Page<SterilizationLoad> findFiltered(
            @Param("status")   LoadStatus status,
            @Param("method")   SterilizationMethod method,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo")   LocalDate dateTo,
            Pageable pageable);
}
