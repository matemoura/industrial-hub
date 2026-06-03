package com.industrialhub.backend.qms.ged.domain;

/**
 * SEC-125: Thrown when an uploaded GED file fails MIME type validation
 * or size constraints. Maps to HTTP 422 Unprocessable Entity.
 */
public class InvalidGedFileException extends RuntimeException {

    public InvalidGedFileException(String message) {
        super(message);
    }
}
