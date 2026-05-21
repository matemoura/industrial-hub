package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.SlaRuleResponse;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSlaRuleListUseCase {

    private final SlaRuleRepository slaRuleRepository;

    @Transactional(readOnly = true)
    public List<SlaRuleResponse> execute() {
        return slaRuleRepository.findAllByActiveTrueOrderByEntityTypeAscClassifierValueAsc()
            .stream()
            .map(SlaRuleResponse::from)
            .toList();
    }
}
