package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
public class ExportNcCsvUseCase {

    private static final String[] HEADERS = {
        "id", "title", "type", "severity", "status",
        "reportedBy", "reportedAt", "closedBy", "closedAt"
    };

    private final NonConformanceRepository repository;

    public ExportNcCsvUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public void export(Writer writer) throws IOException {
        List<NonConformance> ncs = repository.findAllByOrderByReportedAtDesc();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (NonConformance nc : ncs) {
                printer.printRecord(
                    nc.getId(),
                    escapeCsv(nc.getTitle()),
                    nc.getType(),
                    nc.getSeverity(),
                    nc.getStatus(),
                    escapeCsv(nc.getReportedBy()),
                    nc.getReportedAt(),
                    escapeCsv(nc.getClosedBy()),
                    nc.getClosedAt()
                );
            }
        }
    }

    // SEC-026: prevent CSV injection — prefix formula chars with a single quote
    private String escapeCsv(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            return "'" + trimmed;
        }
        return trimmed;
    }
}
