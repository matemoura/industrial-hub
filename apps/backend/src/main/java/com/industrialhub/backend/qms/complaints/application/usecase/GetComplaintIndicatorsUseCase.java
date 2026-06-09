package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.qms.complaints.application.dto.ComplaintIndicators;
import com.industrialhub.backend.qms.complaints.domain.ComplaintSource;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class GetComplaintIndicatorsUseCase {

    private final CustomerComplaintRepository complaintRepository;

    public GetComplaintIndicatorsUseCase(CustomerComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    @Transactional(readOnly = true)
    public ComplaintIndicators execute(LocalDate from, LocalDate to) {
        List<CustomerComplaint> complaints = complaintRepository.findByReportedDateBetween(from, to);

        int totalReceived = complaints.size();

        Map<ComplaintStatus, Integer> byStatus = complaints.stream()
            .collect(Collectors.groupingBy(CustomerComplaint::getStatus,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        Map<NcSeverity, Integer> bySeverity = complaints.stream()
            .collect(Collectors.groupingBy(CustomerComplaint::getSeverity,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        int reportedToAnvisa = (int) complaints.stream()
            .filter(CustomerComplaint::isReportedToAnvisa)
            .count();

        OptionalDouble avg = complaints.stream()
            .filter(c -> c.getStatus() == ComplaintStatus.CLOSED && c.getClosedAt() != null)
            .mapToLong(c -> ChronoUnit.DAYS.between(c.getCreatedAt().toLocalDate(),
                c.getClosedAt().toLocalDate()))
            .average();

        Double avgResolutionDays = avg.isPresent() ? avg.getAsDouble() : null;

        List<ComplaintIndicators.ProductCount> byProduct = complaints.stream()
            .filter(c -> c.getProductCode() != null && !c.getProductCode().isBlank())
            .collect(Collectors.groupingBy(CustomerComplaint::getProductCode,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(5)
            .map(e -> new ComplaintIndicators.ProductCount(e.getKey(), e.getValue()))
            .toList();

        Map<ComplaintSource, Integer> bySource = complaints.stream()
            .collect(Collectors.groupingBy(CustomerComplaint::getSource,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return new ComplaintIndicators(totalReceived, byStatus, bySeverity, reportedToAnvisa,
            avgResolutionDays, byProduct, bySource);
    }
}
