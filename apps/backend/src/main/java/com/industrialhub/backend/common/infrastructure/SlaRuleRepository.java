package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlaRuleRepository extends JpaRepository<SlaRule, UUID> {

    List<SlaRule> findAllByActiveTrueOrderByEntityTypeAscClassifierValueAsc();

    List<SlaRule> findByActiveTrue();

    boolean existsByEntityTypeAndClassifierFieldAndClassifierValueAndActiveTrue(
        SlaEntityType entityType,
        SlaClassifierField classifierField,
        String classifierValue
    );
}
