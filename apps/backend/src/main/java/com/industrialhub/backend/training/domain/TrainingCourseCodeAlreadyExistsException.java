package com.industrialhub.backend.training.domain;

public class TrainingCourseCodeAlreadyExistsException extends RuntimeException {
    public TrainingCourseCodeAlreadyExistsException(String code) {
        super("Já existe um curso com o código: " + code);
    }
}
