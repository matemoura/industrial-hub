package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.qms.ged.domain.InvalidGedFileException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * SEC-125: Validates uploaded GED document files using Apache Tika magic-byte detection.
 * Rejects files whose actual content does not match an allowed MIME type, regardless
 * of the declared Content-Type header.
 *
 * Follows the same pattern as ExcelFileValidator (SEC-107, Sprint 29).
 */
@Component
public class GedFileValidator {

    private static final Tika TIKA = new Tika();

    /** Allowed MIME types for controlled documents. */
    private static final Set<String> ALLOWED_MIME = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         // .xlsx
    );

    private static final long MAX_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB

    /**
     * Validates file is non-empty, within size limit, and matches an allowed MIME type.
     *
     * @param file the uploaded multipart file
     * @throws InvalidGedFileException if validation fails (→ 422)
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidGedFileException("Arquivo não pode ser vazio.");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidGedFileException(
                "Arquivo excede o tamanho máximo permitido (50 MB).");
        }

        try {
            String safeName = safeName(file.getOriginalFilename());
            String detectedMime = TIKA.detect(file.getBytes(), safeName);
            if (!ALLOWED_MIME.contains(detectedMime)) {
                throw new InvalidGedFileException(
                    "Tipo de arquivo não permitido: " + detectedMime
                    + ". Tipos aceitos: PDF, DOCX, XLSX.");
            }
        } catch (IOException e) {
            throw new InvalidGedFileException("Não foi possível verificar o tipo do arquivo.");
        }
    }

    private static String safeName(String original) {
        if (original == null || original.isBlank()) return "upload";
        String name = new File(original).getName();
        return name.isBlank() ? "upload" : name;
    }
}
