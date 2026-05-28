package com.industrialhub.backend.production.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.production.application.dto.PurchaseNeedResponse;
import com.industrialhub.backend.production.domain.MrpRun;
import com.industrialhub.backend.production.infrastructure.MrpRunRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetPurchaseNeedsUseCase {

    private final MrpRunRepository mrpRunRepository;
    private final ObjectMapper objectMapper;

    public GetPurchaseNeedsUseCase(MrpRunRepository mrpRunRepository, ObjectMapper objectMapper) {
        this.mrpRunRepository = mrpRunRepository;
        this.objectMapper = objectMapper;
    }

    public List<PurchaseNeedResponse> execute() {
        MrpRun latestRun = mrpRunRepository.findLatestRealRun()
                .orElseThrow(com.industrialhub.backend.production.domain.NoMrpRunException::new);

        if (latestRun.getPurchaseNeedsJson() == null || latestRun.getPurchaseNeedsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(latestRun.getPurchaseNeedsJson(),
                    new TypeReference<List<PurchaseNeedResponse>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
