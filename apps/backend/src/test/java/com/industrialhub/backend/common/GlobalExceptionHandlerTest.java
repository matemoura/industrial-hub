package com.industrialhub.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void maxUploadSizeExceeded_returns413WithMessage() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        Map<String, String> response = handler.handleMaxUploadSize(ex);

        assertThat(response).containsEntry("message", "Arquivo muito grande. Limite: 10 MB.");
    }
}
