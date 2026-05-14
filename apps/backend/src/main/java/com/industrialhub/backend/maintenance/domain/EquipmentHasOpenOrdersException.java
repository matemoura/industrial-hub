package com.industrialhub.backend.maintenance.domain;

public class EquipmentHasOpenOrdersException extends RuntimeException {

    public EquipmentHasOpenOrdersException(String code) {
        super("Equipment " + code + " has open work orders");
    }
}
