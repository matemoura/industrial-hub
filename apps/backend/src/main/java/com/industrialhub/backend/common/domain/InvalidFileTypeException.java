package com.industrialhub.backend.common.domain;

public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException(String contentType) {
        super("File type not allowed: " + contentType);
    }
}
