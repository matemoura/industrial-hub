package com.industrialhub.backend.qms.domain;

public class SupplierDuplicateCodeException extends RuntimeException {

    public SupplierDuplicateCodeException(String code) {
        super("Código de fornecedor já existe: " + code);
    }
}
