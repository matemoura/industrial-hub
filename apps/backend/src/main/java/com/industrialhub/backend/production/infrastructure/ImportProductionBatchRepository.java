package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.ImportProductionBatch;
import com.industrialhub.backend.production.domain.ProductionImportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ImportProductionBatchRepository extends JpaRepository<ImportProductionBatch, UUID> {

    @Query("SELECT b FROM ImportProductionBatch b " +
           "WHERE (:type IS NULL OR b.type = :type) " +
           "ORDER BY b.importedAt DESC")
    Page<ImportProductionBatch> findFiltered(@Param("type") ProductionImportType type, Pageable pageable);

    Optional<ImportProductionBatch> findFirstByTypeOrderByImportedAtDesc(ProductionImportType type);
}
