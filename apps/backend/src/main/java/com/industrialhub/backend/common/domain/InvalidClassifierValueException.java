package com.industrialhub.backend.common.domain;

public class InvalidClassifierValueException extends RuntimeException {

    public InvalidClassifierValueException(String value, String field, String allowed) {
        super(String.format("Valor '%s' inválido para %s. Valores permitidos: %s", value, field, allowed));
    }
}
