package com.industrialhub.backend.oee.application.usecase;

public class InvalidExcelFormatException extends RuntimeException {

    public InvalidExcelFormatException(String message) {
        super(message);
    }
}
