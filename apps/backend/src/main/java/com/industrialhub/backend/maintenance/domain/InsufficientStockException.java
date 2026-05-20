package com.industrialhub.backend.maintenance.domain;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(int available, int requested) {
        super("Estoque insuficiente: disponível " + available + ", solicitado " + requested);
    }
}
