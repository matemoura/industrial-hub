package com.industrialhub.backend.maintenance.domain;

public class EquipmentDuplicateCodeException extends RuntimeException {

    public EquipmentDuplicateCodeException(String code) {
        super("Equipment code already exists: " + code);
    }
}
