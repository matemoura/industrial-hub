package com.industrialhub.backend.common.domain;

public class InvalidRetentionDaysException extends RuntimeException {
    public InvalidRetentionDaysException(int days) {
        super("Retenção inválida: " + days + " dias. Deve ser entre 30 e 3650.");
    }
}
