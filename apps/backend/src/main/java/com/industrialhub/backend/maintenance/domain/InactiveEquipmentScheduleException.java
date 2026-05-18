package com.industrialhub.backend.maintenance.domain;

public class InactiveEquipmentScheduleException extends RuntimeException {
    public InactiveEquipmentScheduleException() {
        super("Equipamento inativo não pode receber plano");
    }
}
