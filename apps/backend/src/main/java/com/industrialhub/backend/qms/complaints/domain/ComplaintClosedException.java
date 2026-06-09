package com.industrialhub.backend.qms.complaints.domain;

import java.util.UUID;

public class ComplaintClosedException extends RuntimeException {
    public ComplaintClosedException(UUID id) {
        super("Reclamação " + id + " está CLOSED e não pode ser editada");
    }
}
