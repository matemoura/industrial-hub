package com.industrialhub.backend.production.domain;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(UUID id) {
        super("Produto não encontrado: " + id);
    }

    public ProductNotFoundException(String dynamicsCode) {
        super("Produto não encontrado para dynamics_code: " + dynamicsCode);
    }
}
