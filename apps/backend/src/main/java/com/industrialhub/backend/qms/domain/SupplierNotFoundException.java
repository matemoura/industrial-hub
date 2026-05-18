package com.industrialhub.backend.qms.domain;

import java.util.UUID;

public class SupplierNotFoundException extends RuntimeException {

    public SupplierNotFoundException(UUID id) {
        super("Fornecedor não encontrado: " + id);
    }
}
