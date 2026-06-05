package com.industrialhub.backend.training.domain;

import java.util.UUID;

public class TrainingCourseNotFoundException extends RuntimeException {
    public TrainingCourseNotFoundException(UUID id) {
        super("Curso de treinamento não encontrado: " + id);
    }
}
