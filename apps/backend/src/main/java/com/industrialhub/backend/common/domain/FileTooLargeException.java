package com.industrialhub.backend.common.domain;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long maxBytes) {
        super("File exceeds maximum allowed size of " + (maxBytes / 1024 / 1024) + " MB");
    }
}
