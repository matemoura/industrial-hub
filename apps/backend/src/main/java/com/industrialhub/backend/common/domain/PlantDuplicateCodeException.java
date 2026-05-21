package com.industrialhub.backend.common.domain;

public class PlantDuplicateCodeException extends RuntimeException {

    public PlantDuplicateCodeException(String code) {
        super("Já existe uma planta com o código: " + code);
    }
}
