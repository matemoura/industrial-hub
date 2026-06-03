package com.industrialhub.backend.qms.ged;

import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import com.industrialhub.backend.qms.ged.domain.InvalidGedFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class GedFileValidatorTest {

    private GedFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GedFileValidator();
    }

    @Test
    void validate_nullFile_throwsInvalidGedFileException() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidGedFileException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    void validate_emptyFile_throwsInvalidGedFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(emptyFile))
                .isInstanceOf(InvalidGedFileException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    void validate_fileTooLarge_throwsInvalidGedFileException() {
        // Mock a file that reports size > 50MB without actual bytes
        byte[] oversizedContent = new byte[1];
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", oversizedContent) {
            @Override
            public long getSize() {
                return 51L * 1024 * 1024; // 51 MB
            }
        };

        assertThatThrownBy(() -> validator.validate(oversizedFile))
                .isInstanceOf(InvalidGedFileException.class)
                .hasMessageContaining("50 MB");
    }

    @Test
    void validate_htmlDisguisedAsPdf_throwsInvalidGedFileException() {
        // SEC-125: HTML bytes with PDF content-type — Tika detects actual type
        byte[] htmlBytes = "<html><body>not a pdf</body></html>".getBytes();
        MockMultipartFile htmlFile = new MockMultipartFile(
                "file", "malicious.pdf", "application/pdf", htmlBytes);

        assertThatThrownBy(() -> validator.validate(htmlFile))
                .isInstanceOf(InvalidGedFileException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    void validate_validPdfMagicBytes_doesNotThrow() {
        // PDF magic bytes: %PDF-
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34,
                0x0A, 0x25, (byte) 0xC3, (byte) 0xBC, (byte) 0xC3, (byte) 0xB1, (byte) 0xC3, 0x00};
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", pdfBytes);

        // Should not throw — Tika detects as application/pdf
        assertThatCode(() -> validator.validate(pdfFile))
                .doesNotThrowAnyException();
    }
}
