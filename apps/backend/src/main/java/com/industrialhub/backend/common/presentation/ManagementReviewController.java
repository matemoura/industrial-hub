package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.ManagementReviewData;
import com.industrialhub.backend.common.application.usecase.GenerateManagementReviewPdfUseCase;
import com.industrialhub.backend.common.application.usecase.GetManagementReviewDataUseCase;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/management-review")
@Validated
public class ManagementReviewController {

    private final GetManagementReviewDataUseCase getDataUseCase;
    private final GenerateManagementReviewPdfUseCase generatePdfUseCase;

    public ManagementReviewController(
        GetManagementReviewDataUseCase getDataUseCase,
        GenerateManagementReviewPdfUseCase generatePdfUseCase
    ) {
        this.getDataUseCase = getDataUseCase;
        this.generatePdfUseCase = generatePdfUseCase;
    }

    @GetMapping("/indicators")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MANAGEMENT_REVIEW)")
    public ResponseEntity<ManagementReviewData> getIndicators(
        @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(getDataUseCase.execute(from, to, principal.getUsername()));
    }

    @GetMapping("/indicators/export")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MANAGEMENT_REVIEW)")
    public ResponseEntity<byte[]> exportPdf(
        @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @AuthenticationPrincipal UserDetails principal
    ) {
        byte[] pdf = generatePdfUseCase.execute(from, to, principal.getUsername());
        String filename = "management-review-" + from + "-to-" + to + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(pdf);
    }
}
