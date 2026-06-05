package com.industrialhub.backend.training.domain;

import java.util.UUID;

public class TrainingRecordNotFoundException extends RuntimeException {
    public TrainingRecordNotFoundException(UUID id) {
        super("Registro de treinamento não encontrado: " + id);
    }
}
