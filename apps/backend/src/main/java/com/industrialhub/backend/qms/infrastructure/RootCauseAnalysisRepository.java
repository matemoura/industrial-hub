package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.domain.RootCauseAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RootCauseAnalysisRepository extends JpaRepository<RootCauseAnalysis, UUID> {

    Optional<RootCauseAnalysis> findByNonConformanceId(UUID ncId);

    boolean existsByNonConformanceId(UUID ncId);
}
