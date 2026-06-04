package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.projection.CapaAgingProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sprint 39 / ADR-050 Decisão 4: exporta CAPAs abertas como CSV.
 * UTF-8 BOM + separador ';' (padrão pt-BR para Excel).
 */
@Service
@Transactional(readOnly = true)
public class ExportCapaAgingCsvUseCase {

    private final CorrectiveActionRepository correctiveActionRepository;

    public ExportCapaAgingCsvUseCase(CorrectiveActionRepository correctiveActionRepository) {
        this.correctiveActionRepository = correctiveActionRepository;
    }

    public byte[] execute() {
        List<CapaAgingProjection> open = correctiveActionRepository.findOpenCapasForAging();
        LocalDate today = LocalDate.now();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // UTF-8 BOM
        baos.write(0xEF);
        baos.write(0xBB);
        baos.write(0xBF);

        PrintWriter pw = new PrintWriter(baos, true, StandardCharsets.UTF_8);
        pw.println("ncSeverity;status;dueDate;diasParaVencer;situacao");

        for (CapaAgingProjection p : open) {
            String dueDateStr = p.getDueDate() != null ? p.getDueDate().toString() : "";
            String diasStr = "";
            String situacao = "Sem prazo";

            if (p.getDueDate() != null) {
                long delta = ChronoUnit.DAYS.between(today, p.getDueDate());
                diasStr = String.valueOf(delta);
                situacao = delta < 0 ? "Vencida" : "Em dia";
            }

            pw.printf("%s;%s;%s;%s;%s%n",
                    escape(p.getNcSeverity()),
                    escape(p.getStatus()),
                    dueDateStr,
                    diasStr,
                    situacao
            );
        }

        pw.flush();
        return baos.toByteArray();
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
