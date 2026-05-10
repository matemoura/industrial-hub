package com.industrialhub.backend.oee.infrastructure;

import com.industrialhub.backend.oee.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    Optional<ImportBatch> findByPeriodDate(LocalDate periodDate);

    boolean existsByPeriodDate(LocalDate periodDate);
}
