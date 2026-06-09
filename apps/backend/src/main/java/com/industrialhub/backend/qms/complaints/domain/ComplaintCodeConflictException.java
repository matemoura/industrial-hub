package com.industrialhub.backend.qms.complaints.domain;

public class ComplaintCodeConflictException extends RuntimeException {
    public ComplaintCodeConflictException(String code) {
        super("Código de reclamação já existe: " + code);
    }
}
