package com.industrialhub.backend.qms.domain;

public class SupplierRequiredForNcException extends RuntimeException {

    public SupplierRequiredForNcException() {
        super("supplierId é obrigatório para NCs do tipo SUPPLIER");
    }
}
