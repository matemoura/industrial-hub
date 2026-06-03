package com.industrialhub.backend.qms.ged;

import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.presentation.GedController;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural tests for SEC-127 and SEC-128 security hardening on GedController.
 */
class GedControllerSecurityTest {

    // --- SEC-127: @Validated present on GedController ---

    @Test
    void gedController_hasValidatedAnnotation() {
        assertThat(GedController.class.isAnnotationPresent(Validated.class))
                .as("GedController must have @Validated for @RequestParam Bean Validation (ADR-031)")
                .isTrue();
    }

    // --- SEC-128: uploadedBy removed from DocumentRevisionResponse ---

    @Test
    void documentRevisionResponse_doesNotContain_uploadedBy() {
        boolean hasUploadedBy = Arrays.stream(DocumentRevisionResponse.class.getRecordComponents())
                .anyMatch(rc -> rc.getName().equals("uploadedBy"));

        assertThat(hasUploadedBy)
                .as("DocumentRevisionResponse must not contain 'uploadedBy' (SEC-128, ADR-041 §7)")
                .isFalse();
    }

    @Test
    void documentRevisionResponse_hasExpectedFields() {
        var fieldNames = Arrays.stream(DocumentRevisionResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(fieldNames).containsExactlyInAnyOrder(
                "id", "revisionNumber", "originalFileName",
                "fileSizeBytes", "uploadedAt", "changeReason"
        );
    }

    // --- SEC-127: changeReason @RequestParam has @NotBlank and @Size constraints ---

    @Test
    void addRevision_changeReasonParam_hasNotBlankAndSize() {
        Method addRevision = Arrays.stream(GedController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("addRevision"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("addRevision method not found in GedController"));

        Parameter changeReasonParam = Arrays.stream(addRevision.getParameters())
                .filter(p -> p.isAnnotationPresent(RequestParam.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No @RequestParam parameter found in addRevision"));

        assertThat(changeReasonParam.isAnnotationPresent(NotBlank.class))
                .as("changeReason must have @NotBlank (SEC-127)")
                .isTrue();

        assertThat(changeReasonParam.isAnnotationPresent(Size.class))
                .as("changeReason must have @Size (SEC-127)")
                .isTrue();

        Size sizeAnnotation = changeReasonParam.getAnnotation(Size.class);
        assertThat(sizeAnnotation.max())
                .as("@Size(max) on changeReason must be 1000")
                .isEqualTo(1000);
    }
}
