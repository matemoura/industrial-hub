package com.industrialhub.backend.production.application.usecase;

import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * SEC-107: Validates uploaded Excel files using Apache Tika magic-byte detection.
 * Rejects files whose content does not match a known Excel MIME type, preventing
 * polyglot/disguised file uploads regardless of the declared Content-Type.
 */
public class ExcelFileValidator {

    private static final Set<String> ALLOWED_EXCEL_TYPES = Set.of(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
        "application/vnd.ms-excel"                                            // .xls
    );

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Tika TIKA = new Tika();

    private ExcelFileValidator() {}

    /**
     * Validates that the file is a valid Excel spreadsheet with acceptable size.
     *
     * @param file the uploaded file
     * @throws IllegalArgumentException if the file is empty, too large, or not an Excel file
     * @throws IOException              if the file bytes cannot be read
     */
    public static void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo inválido: apenas Excel (.xlsx, .xls) é aceito");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                "Arquivo muito grande: o tamanho máximo permitido é 10 MB");
        }

        byte[] bytes = file.getBytes();

        String originalFilename = file.getOriginalFilename();
        String safeName = originalFilename != null ? new java.io.File(originalFilename).getName() : "upload";

        String detectedType = TIKA.detect(bytes, safeName);
        if (!ALLOWED_EXCEL_TYPES.contains(detectedType)) {
            throw new IllegalArgumentException(
                "Arquivo inválido: apenas Excel (.xlsx, .xls) é aceito");
        }
    }
}
