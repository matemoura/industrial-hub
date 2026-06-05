package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.training.application.dto.CompetencyMatrixRow;
import com.industrialhub.backend.training.application.dto.TrainingComplianceSummary;
import com.industrialhub.backend.training.domain.CompetencyStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetTrainingComplianceSummaryUseCase {

    private final GetCompetencyMatrixUseCase matrixUseCase;

    public GetTrainingComplianceSummaryUseCase(GetCompetencyMatrixUseCase matrixUseCase) {
        this.matrixUseCase = matrixUseCase;
    }

    public TrainingComplianceSummary execute() {
        List<CompetencyMatrixRow> matrix = matrixUseCase.execute();

        if (matrix.isEmpty()) {
            return new TrainingComplianceSummary(0, 0, 0, 0, 0, 0, 100.0);
        }

        Map<CompetencyStatus, Long> counts = matrix.stream()
            .collect(Collectors.groupingBy(CompetencyMatrixRow::status, Collectors.counting()));

        int total = matrix.size();
        int valid    = counts.getOrDefault(CompetencyStatus.VALID, 0L).intValue();
        int expiring = counts.getOrDefault(CompetencyStatus.EXPIRING, 0L).intValue();
        int expired  = counts.getOrDefault(CompetencyStatus.EXPIRED, 0L).intValue();
        int missing  = counts.getOrDefault(CompetencyStatus.MISSING, 0L).intValue();

        long totalUsers = matrix.stream().map(CompetencyMatrixRow::username).distinct().count();
        double compliancePct = total == 0 ? 100.0
            : Math.round(((double) valid / total) * 10000.0) / 100.0;

        return new TrainingComplianceSummary(
            (int) totalUsers, total, valid, expiring, expired, missing, compliancePct
        );
    }
}
