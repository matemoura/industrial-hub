package com.industrialhub.backend.qms.complaints.domain;

import java.util.UUID;

public class CustomerComplaintNotFoundException extends RuntimeException {
    public CustomerComplaintNotFoundException(UUID id) {
        super("Reclamação não encontrada: " + id);
    }
}
