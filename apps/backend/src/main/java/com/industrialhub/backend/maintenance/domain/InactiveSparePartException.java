package com.industrialhub.backend.maintenance.domain;

public class InactiveSparePartException extends RuntimeException {
    public InactiveSparePartException() {
        super("Peça inativa não pode ser editada");
    }
    public InactiveSparePartException(String message) {
        super(message);
    }
}
