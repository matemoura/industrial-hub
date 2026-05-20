package com.industrialhub.backend.maintenance.domain;

public class SparePartDuplicateCodeException extends RuntimeException {
    public SparePartDuplicateCodeException(String code) {
        super("Já existe uma peça com o código informado.");
    }
}
