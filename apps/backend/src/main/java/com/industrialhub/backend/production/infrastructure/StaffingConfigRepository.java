package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.StaffingConfig;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffingConfigRepository extends JpaRepository<StaffingConfig, UUID> {

    /** ADR-043 Decisão 1 — registro singleton, pega o mais antigo — List para compatibilidade Pageable */
    @Query("SELECT sc FROM StaffingConfig sc ORDER BY sc.updatedAt ASC")
    List<StaffingConfig> findFirstList(Pageable pageable);

    default Optional<StaffingConfig> findFirst() {
        List<StaffingConfig> result = findFirstList(PageRequest.of(0, 1));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
