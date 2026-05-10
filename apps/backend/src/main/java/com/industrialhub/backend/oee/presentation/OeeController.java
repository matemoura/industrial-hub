package com.industrialhub.backend.oee.presentation;

import com.industrialhub.backend.oee.application.dto.ImportResultDto;
import com.industrialhub.backend.oee.application.usecase.ImportDynamicsExcelUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/oee")
public class OeeController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportDynamicsExcelUseCase importUseCase;

    public OeeController(ImportDynamicsExcelUseCase importUseCase) {
        this.importUseCase = importUseCase;
    }

    @PostMapping("/imports")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Arquivo vazio"));
        }
        if (!isValidXlsxFile(file)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("message", "Formato inválido. Envie um arquivo .xlsx exportado do Dynamics"));
        }
        ImportResultDto result = importUseCase.execute(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private boolean isValidXlsxFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        boolean validExtension = originalFilename != null &&
                originalFilename.toLowerCase().endsWith(".xlsx");
        boolean validMime = XLSX_MIME.equals(file.getContentType());
        return validExtension || validMime;
    }
}
