package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.MrpRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MrpRunRepository extends JpaRepository<MrpRun, UUID> {

    /** Histórico de runs reais (não dry-runs), mais recentes primeiro */
    @Query("SELECT r FROM MrpRun r WHERE r.isDryRun = false ORDER BY r.runAt DESC")
    Page<MrpRun> findRealRunsPageable(Pageable pageable);

    /** Último run real (para purchase-needs) — List para compatibilidade com Pageable */
    @Query("SELECT r FROM MrpRun r WHERE r.isDryRun = false ORDER BY r.runAt DESC")
    List<MrpRun> findLatestRealRunList(Pageable pageable);

    default Optional<MrpRun> findLatestRealRun() {
        List<MrpRun> result = findLatestRealRunList(PageRequest.of(0, 1));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
