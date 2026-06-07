package com.industrialhub.backend.qms.risk.infrastructure;

import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import com.industrialhub.backend.qms.risk.domain.RiskMitigationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskMitigationActionRepository extends JpaRepository<RiskMitigationAction, UUID> {

    List<RiskMitigationAction> findByRiskItemId(UUID riskItemId);

    boolean existsByRiskItemIdAndStatusAndResidualRpnLessThanEqual(
        UUID riskItemId, MitigationStatus status, int rpn);
}
